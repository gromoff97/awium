package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

public final class ConditionExecution<OBSERVED, RESULT> {

    final Condition.Check condition;
    private final Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    public ConditionExecution(Condition.Check condition, Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        this.condition = requireNonNull(condition, "condition must not be null");
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    public static <OBSERVED> ConditionExecution<OBSERVED, OBSERVED> observing(
            Condition.Check condition, Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        return new ConditionExecution<>(condition, () -> {
            AwaitAttempt<? super OBSERVED, ?> evaluator = createEvaluator(evaluatorFactory);
            return (AwaitAttempt<OBSERVED, OBSERVED>) actual ->
                    requireNonNull(evaluator.probe(actual), "condition returned null evaluation")
                            .mapSatisfied(ignored -> actual);
        });
    }

    @SuppressWarnings("unchecked")
    private static <OBSERVED, RESULT> AwaitAttempt<? super OBSERVED, ? extends RESULT> createEvaluator(
            Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) throws Exception {
        // Typed constructors validate factories; closed source/selection families are bound by their terminal constraints.
        return (AwaitAttempt<? super OBSERVED, ? extends RESULT>) evaluatorFactory.call();
    }

    public AttemptResult<? extends RESULT> probe(OBSERVED actual) {
        try {
            if (Thread.currentThread().isInterrupted()) {
                return interrupted(null);
            }
            if (evaluator == null) evaluator = createEvaluator(evaluatorFactory);
            if (Thread.currentThread().isInterrupted()) {
                return interrupted(null);
            }
            var result = requireNonNull(evaluator.probe(actual), "condition returned null evaluation");
            if (!(result instanceof AttemptResult.Uncontrolled<?>) && Thread.currentThread().isInterrupted()) {
                return interrupted(result.cause());
            }
            return result;
        } catch (Throwable failure) {
            return AttemptResult.uncontrolled(failure);
        }
    }

    private AttemptResult<RESULT> interrupted(Throwable cause) {
        return AttemptResult.uncontrolled(cause == null
                ? new InterruptedException("caller thread interrupt flag was set") : cause);
    }
}

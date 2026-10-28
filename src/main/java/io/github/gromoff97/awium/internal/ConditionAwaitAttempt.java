package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

/** Creates typed attempts and executes them lazily, converting thrown failures into uncontrolled results. */
public final class ConditionAwaitAttempt<OBSERVED, RESULT> implements AwaitAttempt<OBSERVED, RESULT> {

    final Condition.Check condition;
    private final Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    public ConditionAwaitAttempt(Condition.Check condition, Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        this.condition = requireNonNull(condition, "condition must not be null");
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    /** Captures the original input after a check succeeds, irrespective of its selected result. */
    public static <OBSERVED> ConditionAwaitAttempt<OBSERVED, OBSERVED> observing(
            Condition.Check condition, Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        return new ConditionAwaitAttempt<>(condition, () -> {
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

    @Override
    public ConditionEvaluation<? extends RESULT> probe(OBSERVED actual) {
        try {
            if (evaluator == null && !Thread.currentThread().isInterrupted()) {
                evaluator = createEvaluator(evaluatorFactory);
            }
            if (Thread.currentThread().isInterrupted()) {
                return ConditionEvaluation.uncontrolled(
                        new InterruptedException("caller thread interrupt flag was set"), null);
            }
            return withInterruption(requireNonNull(evaluator.probe(actual), "condition returned null evaluation"));
        } catch (Throwable failure) {
            return ConditionEvaluation.uncontrolled(failure, null);
        }
    }

    private static <RESULT> ConditionEvaluation<? extends RESULT> withInterruption(ConditionEvaluation<? extends RESULT> result) {
        if (!(result instanceof ConditionEvaluation.Uncontrolled<?>) && Thread.currentThread().isInterrupted()) {
            var cause = result.status().cause();
            return ConditionEvaluation.uncontrolled(cause == null
                    ? new InterruptedException("caller thread interrupt flag was set") : cause,
                    result.condition());
        }
        return result;
    }
}

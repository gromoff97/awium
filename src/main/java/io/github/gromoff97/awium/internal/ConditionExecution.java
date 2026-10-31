package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.ConditionResult;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.internal.ProbeFailure.locatedFailure;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

public final class ConditionExecution<OBSERVED, RESULT> {

    private final Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    public ConditionExecution(Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    public static <OBSERVED> ConditionExecution<OBSERVED, OBSERVED> observing(
            Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        return new ConditionExecution<>(() -> {
            AwaitAttempt<? super OBSERVED, ?> evaluator = createEvaluator(evaluatorFactory);
            return (AwaitAttempt<OBSERVED, OBSERVED>) actual ->
                    requireNonNull(evaluator.probe(actual), "condition returned null ConditionResult")
                            .mapValue(ignored -> actual);
        });
    }

    @SuppressWarnings("unchecked")
    private static <OBSERVED, RESULT> AwaitAttempt<? super OBSERVED, ? extends RESULT> createEvaluator(
            Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) throws Exception {
        // Typed constructors validate factories; closed source/selection families are bound by their terminal constraints.
        return (AwaitAttempt<? super OBSERVED, ? extends RESULT>) evaluatorFactory.call();
    }

    public ConditionResult<? extends RESULT> probe(OBSERVED actual) throws Throwable {
        if (currentThread().isInterrupted()) throw interrupted(null);
        if (evaluator == null) evaluator = createEvaluator(evaluatorFactory);
        if (currentThread().isInterrupted()) throw interrupted(null);
        var result = requireNonNull(evaluator.probe(actual), "condition returned null ConditionResult");
        if (currentThread().isInterrupted()) throw interrupted(result.cause());
        return result;
    }

    public ConditionResult<? extends RESULT> probeAt(OBSERVED actual, String location) throws Throwable {
        try {
            return probe(actual);
        } catch (Throwable failure) {
            throw locatedFailure(location, failure);
        }
    }

    private static Throwable interrupted(Throwable cause) {
        return cause == null ? new InterruptedException("caller thread interrupt flag was set") : cause;
    }
}

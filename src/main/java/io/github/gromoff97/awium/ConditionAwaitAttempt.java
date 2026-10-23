package io.github.gromoff97.awium;

import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

/** Lazily executes a condition, converting nonfatal callback failures into uncontrolled results. */
final class ConditionAwaitAttempt<OBSERVED, RESULT> implements AwaitAttempt<OBSERVED, RESULT> {

    final Condition<?, ?> condition;
    private final Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    ConditionAwaitAttempt(Condition<?, ?> condition,
            Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
        this.condition = condition;
        this.evaluatorFactory = evaluatorFactory;
    }

    @Override
    public AttemptResult<? extends RESULT> probe(OBSERVED actual) {
        try {
            if (evaluator == null) {
                checkInterruption();
                evaluator = requireNonNull(evaluatorFactory.call(), "evaluator must not be null");
            }
            checkInterruption();
            var result = requireNonNull(evaluator.probe(actual), "condition returned null ConditionResult");
            return withInterruption(result);
        } catch (Throwable failure) {
            return new AttemptResult.Uncontrolled<>(failure, null, null);
        }
    }

    static <RESULT> AttemptResult<? extends RESULT> withInterruption(AttemptResult<? extends RESULT> result) {
        if (!(result instanceof AttemptResult.Uncontrolled<?>) && Thread.currentThread().isInterrupted()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            return new AttemptResult.Uncontrolled<>(interruption, result.condition(), result.progress());
        }
        return result;
    }

    private static void checkInterruption() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("caller thread interrupt flag was set");
        }
    }
}

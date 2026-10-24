package io.github.gromoff97.awium;

import java.util.concurrent.Callable;

/** Lazily executes a condition, converting any thrown failure into an uncontrolled result. */
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
            if (Thread.currentThread().isInterrupted()) {
                return new AttemptResult.Uncontrolled<>(
                        new InterruptedException("caller thread interrupt flag was set"), null, null);
            }
            if (evaluator == null) {
                evaluator = evaluatorFactory.call();
                if (Thread.currentThread().isInterrupted()) {
                    return new AttemptResult.Uncontrolled<>(
                            new InterruptedException("caller thread interrupt flag was set"), null, null);
                }
            }
            return withInterruption(evaluator.probe(actual));
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
}

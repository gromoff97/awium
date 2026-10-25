package io.github.gromoff97.awium;

/** Creates typed attempts and executes them lazily, converting thrown failures into uncontrolled results. */
final class ConditionAwaitAttempt<OBSERVED, RESULT> implements AwaitAttempt<OBSERVED, RESULT> {

    final Condition<? super OBSERVED, ? extends RESULT> condition;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    ConditionAwaitAttempt(Condition<? super OBSERVED, ? extends RESULT> condition) {
        this.condition = condition;
    }

    // Library-created preserving checks and selectors bind their result to the source type.
    @SuppressWarnings("unchecked")
    static <OBSERVED, RESULT> ConditionAwaitAttempt<OBSERVED, RESULT> bind(Condition<? super OBSERVED, ?> condition) {
        return (ConditionAwaitAttempt<OBSERVED, RESULT>) new ConditionAwaitAttempt<>(condition);
    }

    @Override
    public AttemptResult<? extends RESULT> probe(OBSERVED actual) {
        try {
            if (Thread.currentThread().isInterrupted()) {
                return new AttemptResult.Uncontrolled<>(
                        new InterruptedException("caller thread interrupt flag was set"), null, null);
            }
            if (evaluator == null) {
                evaluator = condition.evaluatorFactory.call();
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

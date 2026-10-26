package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.SourceCondition;

/** Creates typed attempts and executes them lazily, converting thrown failures into uncontrolled results. */
final class ConditionAwaitAttempt<OBSERVED, RESULT> implements AwaitAttempt<OBSERVED, RESULT> {

    final Condition<? super OBSERVED, ? extends RESULT> condition;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    ConditionAwaitAttempt(Condition<? super OBSERVED, ? extends RESULT> condition) {
        this.condition = condition;
    }

    // Preservers and selectors bind to source types. Collection stays disjoint from Map; Iterable would overlap.
    @SuppressWarnings("unchecked")
    static <OBSERVED, RESULT> ConditionAwaitAttempt<OBSERVED, RESULT> bind(Condition<? super OBSERVED, ?> condition) {
        return (ConditionAwaitAttempt<OBSERVED, RESULT>) new ConditionAwaitAttempt<>(condition);
    }

    // Selector terminals require SOURCE_VALUE to be accepted by CHECKED; their source protocol supplies that value.
    // This bridge is only for those nested checks. Sealed SourceCondition preserves the input instance.
    @SuppressWarnings("unchecked")
    static <VALUE> ConditionAwaitAttempt<VALUE, VALUE> bindSelectionCheck(SourceCondition<?, ?> condition) {
        return (ConditionAwaitAttempt<VALUE, VALUE>) new ConditionAwaitAttempt<>(condition.definition());
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

package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.SourceCondition;

/** Creates typed attempts and executes them lazily, converting thrown failures into uncontrolled results. */
final class ConditionAwaitAttempt<OBSERVED, RESULT> implements AwaitAttempt<OBSERVED, RESULT> {

    final Condition<?> condition;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    ConditionAwaitAttempt(Condition.Typed<? super OBSERVED, ? extends RESULT> condition) {
        this((Condition<?>) condition);
    }

    private ConditionAwaitAttempt(Condition<?> condition) {
        this.condition = condition;
    }

    // Preservers and selectors bind to source types. Collection stays disjoint from Map; Iterable would overlap.
    static <OBSERVED, RESULT> ConditionAwaitAttempt<OBSERVED, RESULT> bind(Condition<?> condition) {
        return new ConditionAwaitAttempt<>(condition);
    }

    // Selector terminals require SOURCE_VALUE to be accepted by CHECKED; their source protocol supplies that value.
    // This bridge is only for those nested checks. Sealed SourceCondition preserves the input instance.
    static <VALUE> ConditionAwaitAttempt<VALUE, VALUE> bindSelectionCheck(SourceCondition<?, ?, ?> condition) {
        return bind(condition);
    }

    @SuppressWarnings("unchecked")
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> createEvaluator() throws Exception {
        // Typed constructors validate factories; closed source/selection families are bound by their terminal constraints.
        return (AwaitAttempt<? super OBSERVED, ? extends RESULT>) condition.evaluatorFactory.call();
    }

    @Override
    public AttemptResult<? extends RESULT> probe(OBSERVED actual) {
        try {
            if (evaluator == null && !Thread.currentThread().isInterrupted()) {
                evaluator = createEvaluator();
            }
            if (Thread.currentThread().isInterrupted()) {
                return new AttemptResult.Uncontrolled<>(
                        new InterruptedException("caller thread interrupt flag was set"), null, null);
            }
            return withInterruption(evaluator.probe(actual));
        } catch (Throwable failure) {
            return new AttemptResult.Uncontrolled<>(failure, null, null);
        }
    }

    static <RESULT> AttemptResult<? extends RESULT> withInterruption(AttemptResult<? extends RESULT> result) {
        if (result instanceof AttemptResult.Evaluated<?> evaluated && Thread.currentThread().isInterrupted()) {
            var cause = evaluated.result().cause();
            return new AttemptResult.Uncontrolled<>(cause == null
                    ? new InterruptedException("caller thread interrupt flag was set") : cause,
                    result.condition(), result.progress());
        }
        return result;
    }
}

package io.github.gromoff97.awium;

import static java.util.Objects.requireNonNull;

sealed interface AttemptResult<RESULT> {

    Condition<?, ?> condition();

    AttemptRecord.Progress progress();

    static <RESULT> AttemptResult<RESULT> of(ConditionResult<? extends RESULT> result) {
        return new Evaluated<>(result, null, null);
    }

    default Condition<?, ?> conditionOr(Condition<?, ?> fallback) {
        var condition = condition();
        return condition == null ? fallback
                : condition.withExplanation(fallback.explanation());
    }

    record Evaluated<RESULT>(ConditionResult<? extends RESULT> result,
            Condition<?, ?> condition, AttemptRecord.Progress progress) implements AttemptResult<RESULT> {

        public Evaluated {
            requireNonNull(result, "condition returned null ConditionResult");
        }
    }

    record Uncontrolled<RESULT>(Throwable cause,
            Condition<?, ?> condition, AttemptRecord.Progress progress) implements AttemptResult<RESULT> {

        public Uncontrolled {
            requireNonNull(cause, "cause must not be null");
        }
    }
}

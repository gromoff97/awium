package io.github.gromoff97.awium;

import static java.util.Objects.requireNonNull;

sealed interface AttemptResult<RESULT> {

    Condition condition();

    AttemptRecord.Progress progress();

    static <RESULT> Evaluated<RESULT> of(ConditionResult<? extends RESULT> result) {
        return new Evaluated<>(result, null, null);
    }

    default Condition diagnosticCondition(Condition outer) {
        var condition = condition();
        if (condition == null) return outer;
        return outer.explanation() == null ? condition : condition.because(outer.explanation());
    }

    record Evaluated<RESULT>(ConditionResult<? extends RESULT> result,
            Condition condition, AttemptRecord.Progress progress) implements AttemptResult<RESULT> {

        public Evaluated {
            requireNonNull(result, "condition returned null ConditionResult");
        }
    }

    record Uncontrolled<RESULT>(Throwable cause,
            Condition condition, AttemptRecord.Progress progress) implements AttemptResult<RESULT> {

        public Uncontrolled {
            requireNonNull(cause, "cause must not be null");
        }
    }
}

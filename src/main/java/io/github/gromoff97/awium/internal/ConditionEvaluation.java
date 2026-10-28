package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/** A typed value or failure from one check, with the condition responsible for diagnostics. */
public sealed interface ConditionEvaluation<RESULT> {

    Condition condition();

    AttemptResult status();

    static <RESULT> ConditionEvaluation<RESULT> of(ConditionResult status, RESULT actual) {
        requireNonNull(status, "condition returned null ConditionResult");
        return switch (status) {
            case AttemptResult.Satisfied ignored -> satisfied(actual);
            case AttemptResult.Unsatisfied mismatch -> new Unsatisfied<>(mismatch, null);
        };
    }

    static <RESULT> Satisfied<RESULT> satisfied(RESULT value) {
        return new Satisfied<>(value, null);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(String mismatch) {
        return unsatisfied(mismatch, null);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(String mismatch, Throwable cause) {
        return new Unsatisfied<>(ConditionResult.unsatisfied(mismatch, cause), null);
    }

    static <RESULT> Uncontrolled<RESULT> uncontrolled(Throwable cause, Condition condition) {
        return new Uncontrolled<>(new AttemptResult.Uncontrolled(AttemptResult.Operation.CONDITION, cause), condition);
    }

    default <NEXT> ConditionEvaluation<NEXT> mapSatisfied(Function<? super RESULT, ? extends NEXT> mapping) {
        requireNonNull(mapping, "mapping must not be null");
        return switch (this) {
            case Satisfied<RESULT> success -> new Satisfied<>(mapping.apply(success.value()), condition());
            case Unsatisfied<RESULT> mismatch -> new Unsatisfied<>(mismatch.status(), condition());
            case Uncontrolled<RESULT> failure -> new Uncontrolled<>(failure.status(), condition());
        };
    }

    default Condition diagnosticCondition(Condition outer) {
        var condition = condition();
        if (condition == null) return outer;
        return outer.explanation() == null ? condition : condition.because(outer.explanation());
    }

    record Satisfied<RESULT>(RESULT value, Condition condition) implements ConditionEvaluation<RESULT> {
        @Override
        public AttemptResult.Satisfied status() {
            return ConditionResult.satisfied();
        }
    }

    record Unsatisfied<RESULT>(AttemptResult.Unsatisfied status, Condition condition) implements ConditionEvaluation<RESULT> {
        public Unsatisfied {
            requireNonNull(status, "status must not be null");
        }
    }

    record Uncontrolled<RESULT>(AttemptResult.Uncontrolled status, Condition condition) implements ConditionEvaluation<RESULT> {
        public Uncontrolled {
            requireNonNull(status, "status must not be null");
        }
    }
}

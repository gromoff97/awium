package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/** A typed value or failure from one check. The bound attempt owns the condition. */
public sealed interface AttemptResult<RESULT> {

    AttemptStatus status();

    static <RESULT> AttemptResult<RESULT> of(ConditionResult status, RESULT actual) {
        requireNonNull(status, "condition returned null ConditionResult");
        return switch (status) {
            case AttemptStatus.Satisfied ignored -> satisfied(actual);
            case AttemptStatus.Unsatisfied mismatch -> new Unsatisfied<>(mismatch);
        };
    }

    static <RESULT> Satisfied<RESULT> satisfied(RESULT value) {
        return new Satisfied<>(value);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(String mismatch) {
        return unsatisfied(mismatch, null);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(String mismatch, Throwable cause) {
        return new Unsatisfied<>(ConditionResult.unsatisfied(mismatch, cause));
    }

    static <RESULT> Uncontrolled<RESULT> uncontrolled(Throwable cause) {
        return new Uncontrolled<>(new AttemptStatus.Uncontrolled(AttemptStatus.Operation.CONDITION, cause));
    }

    default <NEXT> AttemptResult<NEXT> mapSatisfied(Function<? super RESULT, ? extends NEXT> mapping) {
        requireNonNull(mapping, "mapping must not be null");
        return switch (this) {
            case Satisfied<RESULT> success -> new Satisfied<>(mapping.apply(success.value()));
            case Unsatisfied<RESULT> mismatch -> new Unsatisfied<>(mismatch.status());
            case Uncontrolled<RESULT> failure -> new Uncontrolled<>(failure.status());
        };
    }

    record Satisfied<RESULT>(RESULT value) implements AttemptResult<RESULT> {
        @Override
        public AttemptStatus.Satisfied status() {
            return ConditionResult.satisfied();
        }
    }

    record Unsatisfied<RESULT>(AttemptStatus.Unsatisfied status) implements AttemptResult<RESULT> {
        public Unsatisfied {
            requireNonNull(status, "status must not be null");
        }
    }

    record Uncontrolled<RESULT>(AttemptStatus.Uncontrolled status) implements AttemptResult<RESULT> {
        public Uncontrolled {
            requireNonNull(status, "status must not be null");
        }
    }
}

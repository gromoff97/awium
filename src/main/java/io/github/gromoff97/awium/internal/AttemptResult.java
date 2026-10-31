package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.ConditionResult;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public sealed interface AttemptResult<RESULT> {

    default Throwable cause() {
        return null;
    }

    static <RESULT> AttemptResult<RESULT> of(ConditionResult<? extends RESULT> status) {
        requireNonNull(status, "condition returned null ConditionResult");
        return switch (status) {
            case ConditionResult.Satisfied<? extends RESULT> success -> satisfied(success.value());
            case ConditionResult.Unsatisfied<?> mismatch -> unsatisfied(mismatch.mismatch(), mismatch.cause());
        };
    }

    static <RESULT> Satisfied<RESULT> satisfied(RESULT value) {
        return new Satisfied<>(value);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(String mismatch) {
        return unsatisfied(mismatch, null);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(String mismatch, Throwable cause) {
        return new Unsatisfied<>(mismatch, cause);
    }

    static <RESULT> Uncontrolled<RESULT> uncontrolled(Throwable cause) {
        return new Uncontrolled<>(cause, null);
    }

    default <NEXT> AttemptResult<NEXT> mapSatisfied(Function<? super RESULT, ? extends NEXT> mapping) {
        requireNonNull(mapping, "mapping must not be null");
        return switch (this) {
            case Satisfied<RESULT> success -> new Satisfied<>(mapping.apply(success.value()));
            case Unsatisfied<RESULT> mismatch -> new Unsatisfied<>(mismatch.mismatch(), mismatch.cause());
            case Uncontrolled<RESULT> failure -> new Uncontrolled<>(failure.cause(), failure.location());
        };
    }

    record Satisfied<RESULT>(RESULT value) implements AttemptResult<RESULT> {}

    record Unsatisfied<RESULT>(String mismatch, Throwable cause) implements AttemptResult<RESULT> {}

    record Uncontrolled<RESULT>(Throwable cause, String location) implements AttemptResult<RESULT> {
        public Uncontrolled {
            requireNonNull(cause, "cause must not be null");
        }
    }
}

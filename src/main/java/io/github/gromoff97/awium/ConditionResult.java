package io.github.gromoff97.awium;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Result and diagnostic context of one condition invocation; polling and timeout policy belong to the wait engine.
 *
 * @param <RESULT> value produced by a satisfied condition
 */
public sealed interface ConditionResult<RESULT> {

    default Throwable cause() {
        return null;
    }

    default <NEXT> ConditionResult<NEXT> mapSatisfied(Function<? super RESULT, ? extends NEXT> mapping) {
        requireNonNull(mapping, "mapping must not be null");
        return switch (this) {
            case Satisfied<? extends RESULT> value -> new Satisfied<>(mapping.apply(value.result()));
            case Unsatisfied<?> value -> new Unsatisfied<>(value.mismatch(), value.cause());
        };
    }

    static <RESULT> ConditionResult<RESULT> satisfied(RESULT result) {
        return new Satisfied<>(result);
    }

    static <RESULT> ConditionResult<RESULT> unsatisfied(String mismatch) {
        return new Unsatisfied<>(mismatch, null);
    }

    static <RESULT> ConditionResult<RESULT> unsatisfied(String mismatch, Throwable cause) {
        return new Unsatisfied<>(nonBlank(mismatch, "mismatch"), requireNonNull(cause, "cause must not be null"));
    }

    record Satisfied<RESULT>(RESULT result) implements ConditionResult<RESULT> {}

    record Unsatisfied<RESULT>(String mismatch, Throwable cause) implements ConditionResult<RESULT> {

        public Unsatisfied {
            mismatch = nonBlank(mismatch, "mismatch");
        }

    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

}

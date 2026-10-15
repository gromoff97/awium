package io.github.gromoff97.awium;

import java.util.function.Function;

import static io.github.gromoff97.awium.ConditionResult.Context.Plain.INSTANCE;
import static java.util.Objects.requireNonNull;

/**
 * Result and diagnostic context of one condition invocation; polling and timeout policy belong to the wait engine.
 *
 * @param <RESULT> value produced by a satisfied condition
 */
public sealed interface ConditionResult<RESULT> {

    Context context();

    default <NEXT> ConditionResult<NEXT> continueIfSatisfied(Function<? super RESULT, ? extends ConditionResult<? extends NEXT>> continuation) {
        requireNonNull(continuation, "continuation must not be null");
        return switch (this) {
            case Satisfied<? extends RESULT> value -> {
                var next = continuation.apply(value.result());
                yield next == null ? null : next.mapSatisfied(result -> result);
            }
            case Unsatisfied<?> value -> new Unsatisfied<>(value.mismatch(), value.assertion(), value.context());
            case Uncontrolled<?> value -> new Uncontrolled<>(value.cause(), value.context());
        };
    }

    default <NEXT> ConditionResult<NEXT> mapSatisfied(Function<? super RESULT, ? extends NEXT> mapping) {
        requireNonNull(mapping, "mapping must not be null");
        return switch (this) {
            case Satisfied<? extends RESULT> value -> new Satisfied<>(mapping.apply(value.result()), value.context());
            case Unsatisfied<?> value -> new Unsatisfied<>(value.mismatch(), value.assertion(), value.context());
            case Uncontrolled<?> value -> new Uncontrolled<>(value.cause(), value.context());
        };
    }

    default ConditionResult<RESULT> withContext(Context replacement) {
        requireNonNull(replacement, "context must not be null");
        return switch (this) {
            case Satisfied<? extends RESULT> value -> new Satisfied<>(value.result(), replacement);
            case Unsatisfied<?> value -> new Unsatisfied<>(value.mismatch(), value.assertion(), replacement);
            case Uncontrolled<?> value -> new Uncontrolled<>(value.cause(), replacement);
        };
    }

    static <RESULT> ConditionResult<RESULT> satisfied(RESULT result) {
        return new Satisfied<>(result, INSTANCE);
    }

    static <RESULT> ConditionResult<RESULT> unsatisfied(String mismatch) {
        return new Unsatisfied<>(mismatch, null, INSTANCE);
    }

    static <RESULT> ConditionResult<RESULT> assertionUnsatisfied(String mismatch, AssertionError cause) {
        return new Unsatisfied<>(nonBlank(mismatch, "mismatch"), requireNonNull(cause, "cause must not be null"), INSTANCE);
    }

    static <RESULT> ConditionResult<RESULT> uncontrolled(Throwable cause) {
        return new Uncontrolled<>(cause, INSTANCE);
    }

    record Satisfied<RESULT>(RESULT result, Context context) implements ConditionResult<RESULT> {

        public Satisfied {
            requireNonNull(context, "context must not be null");
        }

    }

    record Unsatisfied<RESULT>(String mismatch, AssertionError assertion, Context context) implements ConditionResult<RESULT> {

        public Unsatisfied {
            mismatch = nonBlank(mismatch, "mismatch");
            requireNonNull(context, "context must not be null");
        }

    }

    record Uncontrolled<RESULT>(Throwable cause, Context context) implements ConditionResult<RESULT> {

        public Uncontrolled {
            requireNonNull(cause, "cause must not be null");
            requireNonNull(context, "context must not be null");
        }

    }

    sealed interface Context {

        enum Plain implements Context { INSTANCE }

        record Expectation(String description, Reference<?> reference) implements Context {

            public Expectation {
                description = nonBlank(description, "description");
            }
        }

        record Sequence(int capturedStages, int totalStages, int evaluatedStageNumber,
                String expectation, String importance, Reference<?> reference) implements Context {

            public Sequence {
                if (capturedStages < 0 || capturedStages > totalStages
                        || evaluatedStageNumber <= 0 || evaluatedStageNumber > totalStages) {
                    throw new IllegalArgumentException("invalid sequence progress");
                }
                requireNonNull(expectation, "expectation must not be null");
            }
        }
    }

    record Reference<VALUE>(String label, VALUE value) {

        public Reference {
            label = nonBlank(label, "reference label");
        }
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

}

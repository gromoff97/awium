package io.github.gromoff97.awium;

import java.util.function.Function;

import static io.github.gromoff97.awium.ConditionResult.Context.Plain.INSTANCE;
import static java.util.Objects.requireNonNull;

/**
 * Result and diagnostic context of one condition invocation; polling and timeout policy belong to the wait engine.
 *
 * @param <Result> value produced by a satisfied condition
 */
public sealed interface ConditionResult<Result> {

    Context context();

    default <Next> ConditionResult<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionResult<? extends Next>> continuation) {
        requireNonNull(continuation, "continuation must not be null");
        return switch (this) {
            case Satisfied<? extends Result> value -> {
                var next = continuation.apply(value.result());
                yield next == null ? null : next.mapSatisfied(result -> result);
            }
            case Unsatisfied<?> value -> new Unsatisfied<>(value.mismatch(), value.assertion(), value.context());
            case Uncontrolled<?> value -> new Uncontrolled<>(value.cause(), value.context());
        };
    }

    default <Next> ConditionResult<Next> mapSatisfied(Function<? super Result, ? extends Next> mapping) {
        requireNonNull(mapping, "mapping must not be null");
        return switch (this) {
            case Satisfied<? extends Result> value -> new Satisfied<>(mapping.apply(value.result()), value.context());
            case Unsatisfied<?> value -> new Unsatisfied<>(value.mismatch(), value.assertion(), value.context());
            case Uncontrolled<?> value -> new Uncontrolled<>(value.cause(), value.context());
        };
    }

    default ConditionResult<Result> withContext(Context replacement) {
        requireNonNull(replacement, "context must not be null");
        return switch (this) {
            case Satisfied<? extends Result> value -> new Satisfied<>(value.result(), replacement);
            case Unsatisfied<?> value -> new Unsatisfied<>(value.mismatch(), value.assertion(), replacement);
            case Uncontrolled<?> value -> new Uncontrolled<>(value.cause(), replacement);
        };
    }

    static <Result> ConditionResult<Result> satisfied(Result result) {
        return new Satisfied<>(result, INSTANCE);
    }

    static <Result> ConditionResult<Result> unsatisfied(String mismatch) {
        return new Unsatisfied<>(mismatch, null, INSTANCE);
    }

    static <Result> ConditionResult<Result> assertionUnsatisfied(String mismatch, AssertionError cause) {
        return new Unsatisfied<>(nonBlank(mismatch, "mismatch"), requireNonNull(cause, "cause must not be null"), INSTANCE);
    }

    static <Result> ConditionResult<Result> uncontrolled(Throwable cause) {
        return new Uncontrolled<>(cause, INSTANCE);
    }

    record Satisfied<Result>(Result result, Context context) implements ConditionResult<Result> {

        public Satisfied {
            requireNonNull(context, "context must not be null");
        }

    }

    record Unsatisfied<Result>(String mismatch, AssertionError assertion, Context context) implements ConditionResult<Result> {

        public Unsatisfied {
            mismatch = nonBlank(mismatch, "mismatch");
            requireNonNull(context, "context must not be null");
        }

    }

    record Uncontrolled<Result>(Throwable cause, Context context) implements ConditionResult<Result> {

        public Uncontrolled {
            requireNonNull(cause, "cause must not be null");
            requireNonNull(context, "context must not be null");
        }

    }

    sealed interface Context {

        enum Plain implements Context { INSTANCE }

        record Expectation(String description, Reference<?> reference) implements Context {

            public Expectation {
                if (requireNonNull(description, "description must not be null").isBlank()) {
                    throw new IllegalArgumentException("description must not be blank");
                }
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

    record Reference<Value>(String label, Value value) {

        public Reference {
            if (requireNonNull(label, "reference label must not be null").isBlank()) {
                throw new IllegalArgumentException("reference label must not be blank");
            }
        }
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

}

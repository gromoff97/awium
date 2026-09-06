package io.github.gromoff97.awium.condition;

import io.github.gromoff97.awium.results.AwaitAttempt;

import java.util.function.Function;

import static io.github.gromoff97.awium.results.AwaitAttempt.Context.Plain.INSTANCE;
import static java.util.Objects.requireNonNull;

/**
 * Result and diagnostic context of one condition invocation; polling and timeout policy belong to the wait engine.
 *
 * @param <Result> value produced by a satisfied condition
 */
public sealed interface ConditionEvaluation<Result> {

    AwaitAttempt.Context context();

    default <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation) {
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

    default <Next> ConditionEvaluation<Next> mapSatisfied(Function<? super Result, ? extends Next> mapping) {
        requireNonNull(mapping, "mapping must not be null");
        return switch (this) {
            case Satisfied<? extends Result> value -> new Satisfied<>(mapping.apply(value.result()), value.context());
            case Unsatisfied<?> value -> new Unsatisfied<>(value.mismatch(), value.assertion(), value.context());
            case Uncontrolled<?> value -> new Uncontrolled<>(value.cause(), value.context());
        };
    }

    default ConditionEvaluation<Result> withContext(AwaitAttempt.Context replacement) {
        requireNonNull(replacement, "context must not be null");
        return switch (this) {
            case Satisfied<? extends Result> value -> new Satisfied<>(value.result(), replacement);
            case Unsatisfied<?> value -> new Unsatisfied<>(value.mismatch(), value.assertion(), replacement);
            case Uncontrolled<?> value -> new Uncontrolled<>(value.cause(), replacement);
        };
    }

    static <Result> ConditionEvaluation<Result> satisfied(Result result) {
        return new Satisfied<>(result, INSTANCE);
    }

    static <Result> ConditionEvaluation<Result> unsatisfied(String mismatch) {
        return new Unsatisfied<>(mismatch, null, INSTANCE);
    }

    static <Result> ConditionEvaluation<Result> assertionUnsatisfied(String mismatch, AssertionError cause) {
        return new Unsatisfied<>(nonBlank(mismatch, "mismatch"), requireNonNull(cause, "cause must not be null"), INSTANCE);
    }

    static <Result> ConditionEvaluation<Result> uncontrolled(Throwable cause) {
        return new Uncontrolled<>(cause, INSTANCE);
    }

    record Satisfied<Result>(Result result, AwaitAttempt.Context context) implements ConditionEvaluation<Result> {

        public Satisfied {
            requireNonNull(context, "context must not be null");
        }

    }

    record Unsatisfied<Result>(String mismatch, AssertionError assertion, AwaitAttempt.Context context) implements ConditionEvaluation<Result> {

        public Unsatisfied {
            mismatch = nonBlank(mismatch, "mismatch");
            requireNonNull(context, "context must not be null");
        }

    }

    record Uncontrolled<Result>(Throwable cause, AwaitAttempt.Context context) implements ConditionEvaluation<Result> {

        public Uncontrolled {
            requireNonNull(cause, "cause must not be null");
            requireNonNull(context, "context must not be null");
        }

    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

}

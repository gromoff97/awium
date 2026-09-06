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

    enum Status { SATISFIED, UNSATISFIED, UNCONTROLLED }

    Status status();

    AwaitAttempt.Context context();

    <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation);

    default <Next> ConditionEvaluation<Next> mapSatisfied(Function<? super Result, ? extends Next> mapping) {
        requireNonNull(mapping, "mapping must not be null");
        return switch (this) {
            case Satisfied<? extends Result> value -> new Satisfied<>(mapping.apply(value.result()), value.context());
            case Unsatisfied<?> value -> new Unsatisfied<>(value.mismatch(), value.context());
            case AssertionUnsatisfied<?> value ->
                    new AssertionUnsatisfied<>(value.mismatch(), value.cause(), value.context());
            case Uncontrolled<?> value -> new Uncontrolled<>(value.cause(), value.context());
        };
    }

    default ConditionEvaluation<Result> withContext(AwaitAttempt.Context replacement) {
        requireNonNull(replacement, "context must not be null");
        return switch (this) {
            case Satisfied<? extends Result> value -> new Satisfied<>(value.result(), replacement);
            case Unsatisfied<?> value -> new Unsatisfied<>(value.mismatch(), replacement);
            case AssertionUnsatisfied<?> value ->
                    new AssertionUnsatisfied<>(value.mismatch(), value.cause(), replacement);
            case Uncontrolled<?> value -> new Uncontrolled<>(value.cause(), replacement);
        };
    }

    static <Result> ConditionEvaluation<Result> satisfied(Result result) {
        return new Satisfied<>(result, INSTANCE);
    }

    static <Result> ConditionEvaluation<Result> unsatisfied(String mismatch) {
        return new Unsatisfied<>(mismatch, INSTANCE);
    }

    static <Result> ConditionEvaluation<Result> assertionUnsatisfied(String mismatch, AssertionError cause) {
        return new AssertionUnsatisfied<>(mismatch, cause, INSTANCE);
    }

    static <Result> ConditionEvaluation<Result> uncontrolled(Throwable cause) {
        return new Uncontrolled<>(cause, INSTANCE);
    }

    record Satisfied<Result>(Result result, AwaitAttempt.Context context) implements ConditionEvaluation<Result> {

        public Satisfied {
            requireNonNull(context, "context must not be null");
        }

        @Override
        public Status status() {
            return Status.SATISFIED;
        }

        @Override
        public <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation) {
            return typed(requireNonNull(continuation, "continuation must not be null").apply(result));
        }

    }

    record Unsatisfied<Result>(String mismatch, AwaitAttempt.Context context) implements ConditionEvaluation<Result> {

        public Unsatisfied {
            mismatch = nonBlank(mismatch, "mismatch");
            requireNonNull(context, "context must not be null");
        }

        @Override
        public Status status() {
            return Status.UNSATISFIED;
        }

        @Override
        public <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation) {
            requireNonNull(continuation, "continuation must not be null");
            return new Unsatisfied<>(mismatch, context);
        }
    }

    record AssertionUnsatisfied<Result>(String mismatch, AssertionError cause,
            AwaitAttempt.Context context) implements ConditionEvaluation<Result> {

        public AssertionUnsatisfied {
            mismatch = nonBlank(mismatch, "mismatch");
            requireNonNull(cause, "cause must not be null");
            requireNonNull(context, "context must not be null");
        }

        @Override
        public Status status() {
            return Status.UNSATISFIED;
        }

        @Override
        public <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation) {
            requireNonNull(continuation, "continuation must not be null");
            return new AssertionUnsatisfied<>(mismatch, cause, context);
        }
    }

    record Uncontrolled<Result>(Throwable cause, AwaitAttempt.Context context) implements ConditionEvaluation<Result> {

        public Uncontrolled {
            requireNonNull(cause, "cause must not be null");
            requireNonNull(context, "context must not be null");
        }

        @Override
        public Status status() {
            return Status.UNCONTROLLED;
        }

        @Override
        public <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation) {
            requireNonNull(continuation, "continuation must not be null");
            return new Uncontrolled<>(cause, context);
        }
    }

    private static <Result> ConditionEvaluation<Result> typed(ConditionEvaluation<? extends Result> evaluation) {
        return switch (evaluation) {
            case null -> null;
            case Satisfied<? extends Result> satisfied -> new Satisfied<>(satisfied.result(), satisfied.context());
            case Unsatisfied<?> unsatisfied -> new Unsatisfied<>(unsatisfied.mismatch(), unsatisfied.context());
            case AssertionUnsatisfied<?> unsatisfied ->
                    new AssertionUnsatisfied<>(unsatisfied.mismatch(), unsatisfied.cause(), unsatisfied.context());
            case Uncontrolled<?> uncontrolled -> new Uncontrolled<>(uncontrolled.cause(), uncontrolled.context());
        };
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

}

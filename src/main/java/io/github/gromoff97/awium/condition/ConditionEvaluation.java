package io.github.gromoff97.awium.condition;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Result of one condition invocation; polling and timeout policy belong to the wait engine.
 *
 * @param <Result> value produced by a satisfied condition
 */
public sealed interface ConditionEvaluation<Result> {

    enum Status { SATISFIED, UNSATISFIED, UNCONTROLLED }

    Status status();

    <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation);

    static <Result> ConditionEvaluation<Result> satisfied(Result result) {
        return new Satisfied<>(result);
    }

    static <Result> ConditionEvaluation<Result> unsatisfied(String mismatch) {
        return new Unsatisfied<>(mismatch);
    }

    static <Result> ConditionEvaluation<Result> assertionUnsatisfied(String mismatch, AssertionError cause) {
        return new AssertionUnsatisfied<>(mismatch, cause);
    }

    static <Result> ConditionEvaluation<Result> uncontrolled(Throwable cause) {
        return new Uncontrolled<>(cause);
    }

    record Satisfied<Result>(Result result) implements ConditionEvaluation<Result> {

        @Override
        public Status status() {
            return Status.SATISFIED;
        }

        @Override
        public <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation) {
            return typed(requireNonNull(continuation, "continuation must not be null").apply(result));
        }

    }

    record Unsatisfied<Result>(String mismatch) implements ConditionEvaluation<Result> {

        public Unsatisfied {
            mismatch = nonBlank(mismatch, "mismatch");
        }

        @Override
        public Status status() {
            return Status.UNSATISFIED;
        }

        @Override
        public <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation) {
            requireNonNull(continuation, "continuation must not be null");
            return new Unsatisfied<>(mismatch);
        }
    }

    record AssertionUnsatisfied<Result>(String mismatch, AssertionError cause) implements ConditionEvaluation<Result> {

        public AssertionUnsatisfied {
            mismatch = nonBlank(mismatch, "mismatch");
            requireNonNull(cause, "cause must not be null");
        }

        @Override
        public Status status() {
            return Status.UNSATISFIED;
        }

        @Override
        public <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation) {
            requireNonNull(continuation, "continuation must not be null");
            return new AssertionUnsatisfied<>(mismatch, cause);
        }
    }

    record Uncontrolled<Result>(Throwable cause) implements ConditionEvaluation<Result> {

        public Uncontrolled {
            requireNonNull(cause, "cause must not be null");
        }

        @Override
        public Status status() {
            return Status.UNCONTROLLED;
        }

        @Override
        public <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation) {
            requireNonNull(continuation, "continuation must not be null");
            return new Uncontrolled<>(cause);
        }
    }

    private static <Result> ConditionEvaluation<Result> typed(ConditionEvaluation<? extends Result> evaluation) {
        return switch (evaluation) {
            case null -> null;
            case Satisfied<? extends Result> satisfied -> new Satisfied<>(satisfied.result());
            case Unsatisfied<?> unsatisfied -> new Unsatisfied<>(unsatisfied.mismatch());
            case AssertionUnsatisfied<?> unsatisfied ->
                    new AssertionUnsatisfied<>(unsatisfied.mismatch(), unsatisfied.cause());
            case Uncontrolled<?> uncontrolled -> new Uncontrolled<>(uncontrolled.cause());
        };
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

}

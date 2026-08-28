package io.github.gromoff97.awium.evaluation;

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

    Context context();

    <Next> ConditionEvaluation<Next> continueIfSatisfied(Function<? super Result, ? extends ConditionEvaluation<? extends Next>> continuation);

    ConditionEvaluation<Result> withContext(Context context);

    static <Result> ConditionEvaluation<Result> satisfied(Result result) {
        return new Satisfied<>(result, Context.Plain.INSTANCE);
    }

    static <Result> ConditionEvaluation<Result> unsatisfied(String mismatch) {
        return new Unsatisfied<>(mismatch, Context.Plain.INSTANCE);
    }

    static <Result> ConditionEvaluation<Result> assertionUnsatisfied(String mismatch, AssertionError cause) {
        return new AssertionUnsatisfied<>(mismatch, cause, Context.Plain.INSTANCE);
    }

    static <Result> ConditionEvaluation<Result> uncontrolled(Throwable cause) {
        return new Uncontrolled<>(cause, Context.Plain.INSTANCE);
    }

    record Satisfied<Result>(Result result, Context context) implements ConditionEvaluation<Result> {

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

        @Override
        public ConditionEvaluation<Result> withContext(Context context) {
            return new Satisfied<>(result, context);
        }
    }

    record Unsatisfied<Result>(String mismatch, Context context) implements ConditionEvaluation<Result> {

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

        @Override
        public ConditionEvaluation<Result> withContext(Context context) {
            return new Unsatisfied<>(mismatch, context);
        }
    }

    record AssertionUnsatisfied<Result>(String mismatch, AssertionError cause,
            Context context) implements ConditionEvaluation<Result> {

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

        @Override
        public ConditionEvaluation<Result> withContext(Context context) {
            return new AssertionUnsatisfied<>(mismatch, cause, context);
        }
    }

    record Uncontrolled<Result>(Throwable cause, Context context) implements ConditionEvaluation<Result> {

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

        @Override
        public ConditionEvaluation<Result> withContext(Context context) {
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

    sealed interface Context {

        enum Plain implements Context { INSTANCE }

        record Sequence(int capturedStages, int totalStages, int evaluatedStageNumber,
                String expectation, String importance) implements Context {

            public Sequence {
                if (capturedStages < 0 || capturedStages > totalStages
                        || evaluatedStageNumber <= 0 || evaluatedStageNumber > totalStages) {
                    throw new IllegalArgumentException("invalid sequence progress");
                }
                requireNonNull(expectation, "expectation must not be null");
            }
        }
    }
}

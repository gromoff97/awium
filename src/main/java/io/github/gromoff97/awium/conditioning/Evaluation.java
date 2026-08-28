package io.github.gromoff97.awium.conditioning;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public final class Evaluation<R> {

    public enum Status { SATISFIED, UNSATISFIED, UNCONTROLLED }

    private final R result;
    private final String mismatch;
    private final AssertionError assertionCause;
    private final Throwable uncontrolledCause;
    private final Context context;

    private Evaluation(R result, String mismatch, AssertionError assertionCause,
            Throwable uncontrolledCause, Context context) {
        this.result = result;
        this.mismatch = mismatch;
        this.assertionCause = assertionCause;
        this.uncontrolledCause = uncontrolledCause;
        this.context = requireNonNull(context, "context must not be null");
    }

    public static <R> Evaluation<R> satisfied(R result) {
        return new Evaluation<>(result, null, null, null, Context.Plain.INSTANCE);
    }

    public static <R> Evaluation<R> unsatisfied(String mismatch) {
        return new Evaluation<>(null, nonBlank(mismatch, "mismatch"), null, null,
                Context.Plain.INSTANCE);
    }

    public static <R> Evaluation<R> assertionUnsatisfied(String mismatch, AssertionError cause) {
        return new Evaluation<>(null, nonBlank(mismatch, "mismatch"),
                requireNonNull(cause, "cause must not be null"), null,
                Context.Plain.INSTANCE);
    }

    public static <R> Evaluation<R> uncontrolled(Throwable cause) {
        return new Evaluation<>(null, null, null,
                requireNonNull(cause, "cause must not be null"), Context.Plain.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    public <T> Evaluation<T> continueIfSatisfied(Function<? super R, ? extends Evaluation<? extends T>> continuation) {
        requireNonNull(continuation, "continuation must not be null");
        return (Evaluation<T>) (status() == Status.SATISFIED ? continuation.apply(result) : this);
    }

    public Status status() {
        if (mismatch != null) {
            return Status.UNSATISFIED;
        }
        return uncontrolledCause == null ? Status.SATISFIED : Status.UNCONTROLLED;
    }

    public R result() {
        return result;
    }

    public String mismatch() {
        return mismatch;
    }

    public AssertionError assertionCause() {
        return assertionCause;
    }

    public Throwable uncontrolledCause() {
        return uncontrolledCause;
    }

    public Context context() {
        return context;
    }

    public Evaluation<R> withContext(Context context) {
        return new Evaluation<>(result, mismatch, assertionCause, uncontrolledCause, context);
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public sealed interface Context {

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

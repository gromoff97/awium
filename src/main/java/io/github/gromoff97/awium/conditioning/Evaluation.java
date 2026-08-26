package io.github.gromoff97.awium.conditioning;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public final class Evaluation<R> {

    public enum Status { SATISFIED, UNSATISFIED, UNCONTROLLED }

    private final R result;
    private final String mismatch;
    private final AssertionError assertionCause;
    private final Throwable uncontrolledCause;

    private Evaluation(R result, String mismatch, AssertionError assertionCause, Throwable uncontrolledCause) {
        this.result = result;
        this.mismatch = mismatch;
        this.assertionCause = assertionCause;
        this.uncontrolledCause = uncontrolledCause;
    }

    public static <R> Evaluation<R> satisfied(R result) {
        return new Evaluation<>(result, null, null, null);
    }

    public static <R> Evaluation<R> unsatisfied(String mismatch) {
        return new Evaluation<>(null, nonBlank(mismatch, "mismatch"), null, null);
    }

    public static <R> Evaluation<R> assertionUnsatisfied(String mismatch, AssertionError cause) {
        return new Evaluation<>(null, nonBlank(mismatch, "mismatch"), requireNonNull(cause, "cause must not be null"), null);
    }

    public static <R> Evaluation<R> uncontrolled(Throwable cause) {
        return new Evaluation<>(null, null, null, requireNonNull(cause, "cause must not be null"));
    }

    public <T> Evaluation<T> continueIfSatisfied(
            Function<? super R, ? extends Evaluation<? extends T>> continuation) {
        requireNonNull(continuation, "continuation must not be null");
        return status() == Status.SATISFIED ? copy(continuation.apply(result)) : failure(this);
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

    private static <T> Evaluation<T> copy(Evaluation<? extends T> evaluation) {
        if (evaluation == null) {
            return null;
        }
        return evaluation.status() == Status.SATISFIED
                ? satisfied(evaluation.result()) : failure(evaluation);
    }

    private static <T> Evaluation<T> failure(Evaluation<?> evaluation) {
        return switch (evaluation.status()) {
            case UNSATISFIED -> evaluation.assertionCause() == null
                    ? unsatisfied(evaluation.mismatch())
                    : assertionUnsatisfied(evaluation.mismatch(), evaluation.assertionCause());
            case UNCONTROLLED -> uncontrolled(evaluation.uncontrolledCause());
            case SATISFIED -> throw new IllegalArgumentException("evaluation must not be satisfied");
        };
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

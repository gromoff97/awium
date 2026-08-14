package io.github.gromoff97.awium.conditioning;

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

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

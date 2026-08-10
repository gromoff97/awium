package io.github.gromoff97.awium;

import java.util.Objects;

public final class Evaluation<R> {

    enum Status { SATISFIED, UNSATISFIED, UNCONTROLLED }

    private final Status status;
    private final R result;
    private final String mismatch;
    private final AssertionError assertionCause;
    private final Throwable uncontrolledCause;

    private Evaluation(Status status, R result, String mismatch,
            AssertionError assertionCause, Throwable uncontrolledCause) {
        this.status = status;
        this.result = result;
        this.mismatch = mismatch;
        this.assertionCause = assertionCause;
        this.uncontrolledCause = uncontrolledCause;
    }

    public static <R> Evaluation<R> satisfied(R result) {
        return new Evaluation<>(Status.SATISFIED, result, null, null, null);
    }

    public static <R> Evaluation<R> unsatisfied(String mismatch) {
        return new Evaluation<>(Status.UNSATISFIED, null,
                Validation.nonBlank(mismatch, "mismatch"), null, null);
    }

    static <R> Evaluation<R> assertionUnsatisfied(
            String mismatch, AssertionError cause) {
        return new Evaluation<>(Status.UNSATISFIED, null,
                Validation.nonBlank(mismatch, "mismatch"),
                Objects.requireNonNull(cause), null);
    }

    static <R> Evaluation<R> uncontrolled(Throwable cause) {
        return new Evaluation<>(Status.UNCONTROLLED, null, null, null,
                Objects.requireNonNull(cause));
    }

    Status status() {
        return status;
    }

    R result() {
        return result;
    }

    String mismatch() {
        return mismatch;
    }

    AssertionError assertionCause() {
        return assertionCause;
    }

    Throwable uncontrolledCause() {
        return uncontrolledCause;
    }

    static <R> Evaluation<R> narrow(Evaluation<? extends R> source) {
        if (source == null) {
            return null;
        }
        return switch (source.status) {
            case SATISFIED -> Evaluation.satisfied(source.result);
            case UNSATISFIED -> source.assertionCause == null
                    ? Evaluation.unsatisfied(source.mismatch)
                    : Evaluation.assertionUnsatisfied(
                            source.mismatch, source.assertionCause);
            case UNCONTROLLED -> Evaluation.uncontrolled(source.uncontrolledCause);
        };
    }
}

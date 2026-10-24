package io.github.gromoff97.awium;

/** An uncontrolled source, condition, waiting, or interruption failure. */
public final class AwaitExecutionException extends RuntimeException {

    private final AwaitFailure failure;

    AwaitExecutionException(AwaitFailure failure) {
        super(failure.message(), failure.cause());
        this.failure = failure;
        failure.suppressed().forEach(this::addSuppressed);
    }

    public AwaitFailure failure() {
        return failure;
    }
}

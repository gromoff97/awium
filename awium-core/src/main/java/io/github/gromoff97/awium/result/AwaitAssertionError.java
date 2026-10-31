package io.github.gromoff97.awium.result;

public final class AwaitAssertionError extends AssertionError {

    private final AwaitFailure failure;

    AwaitAssertionError(AwaitFailure failure) {
        super(failure.message(), failure.cause());
        this.failure = failure;
        failure.suppressed().forEach(this::addSuppressed);
    }

    public AwaitFailure failure() {
        return failure;
    }
}

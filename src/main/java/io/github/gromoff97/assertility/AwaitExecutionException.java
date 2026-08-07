package io.github.gromoff97.assertility;

public final class AwaitExecutionException extends RuntimeException {
    AwaitExecutionException(Throwable cause) {
        super("Unexpected checked exception while evaluating await source", cause);
    }
}

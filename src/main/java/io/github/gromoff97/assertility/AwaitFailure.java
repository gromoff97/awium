package io.github.gromoff97.assertility;

public final class AwaitFailure extends AssertionError {
    AwaitFailure(String message, Throwable cause) {
        super(message, cause);
    }
}

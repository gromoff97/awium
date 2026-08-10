package io.github.gromoff97.awium.exceptions;

public abstract class AwaitFailure extends AssertionError {

    AwaitFailure(String message, Throwable cause) {
        super(message, cause);
    }
}

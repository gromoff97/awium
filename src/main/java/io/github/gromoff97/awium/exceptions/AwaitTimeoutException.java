package io.github.gromoff97.awium.exceptions;

public final class AwaitTimeoutException extends AwaitFailure {

    public AwaitTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

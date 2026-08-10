package io.github.gromoff97.awium.exceptions;

public final class AwaitInterruptedException extends AwaitUncontrolledException {

    public AwaitInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}

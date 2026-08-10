package io.github.gromoff97.awium.exceptions;

public final class AwaitUnhandledException extends AwaitUncontrolledException {

    public AwaitUnhandledException(String message, Throwable cause) {
        super(message, cause);
    }
}

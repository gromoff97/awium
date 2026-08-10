package io.github.gromoff97.awium.exceptions;

public abstract class AwaitUncontrolledException extends RuntimeException {

    AwaitUncontrolledException(String message, Throwable cause) {
        super(message, cause);
    }
}

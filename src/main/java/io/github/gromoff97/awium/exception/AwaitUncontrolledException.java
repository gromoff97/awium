package io.github.gromoff97.awium.exception;

import java.io.Serial;

public abstract class AwaitUncontrolledException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    AwaitUncontrolledException(String message, Throwable cause) {
        super(message, cause);
    }
}

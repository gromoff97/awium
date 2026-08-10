package io.github.gromoff97.awium.exceptions;

import java.io.Serial;

public final class AwaitUnhandledException extends AwaitUncontrolledException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AwaitUnhandledException(String message, Throwable cause) {
        super(message, cause);
    }
}

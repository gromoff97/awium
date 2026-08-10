package io.github.gromoff97.awium;

import java.io.Serial;

public final class AwaitInterruptedException extends AwaitUncontrolledException {

    @Serial
    private static final long serialVersionUID = 1L;

    AwaitInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}

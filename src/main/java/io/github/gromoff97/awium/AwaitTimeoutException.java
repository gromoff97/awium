package io.github.gromoff97.awium;

import java.io.Serial;

public final class AwaitTimeoutException extends AwaitFailure {

    @Serial
    private static final long serialVersionUID = 1L;

    AwaitTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

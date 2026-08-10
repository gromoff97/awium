package io.github.gromoff97.awium.exception;

import java.io.Serial;

public final class AwaitStabilizationException extends AwaitFailure {

    @Serial
    private static final long serialVersionUID = 1L;

    public AwaitStabilizationException(String message, Throwable cause) {
        super(message, cause);
    }
}

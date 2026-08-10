package io.github.gromoff97.awium;

import java.io.Serial;

public final class AwaitStabilizationException extends AwaitFailure {

    @Serial
    private static final long serialVersionUID = 1L;

    AwaitStabilizationException(String message, Throwable cause) {
        super(message, cause);
    }
}

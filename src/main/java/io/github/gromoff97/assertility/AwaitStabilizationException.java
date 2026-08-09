package io.github.gromoff97.assertility;

import java.io.Serial;

public final class AwaitStabilizationException extends AwaitFailure {

    @Serial
    private static final long serialVersionUID = 1L;

    AwaitStabilizationException(String message, Throwable cause) {
        super(message, cause);
    }
}

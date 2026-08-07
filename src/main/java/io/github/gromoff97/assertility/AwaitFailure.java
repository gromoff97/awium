package io.github.gromoff97.assertility;

import java.io.Serial;

public final class AwaitFailure extends AssertionError {
    @Serial
    private static final long serialVersionUID = 1L;

    AwaitFailure(String message, Throwable cause) {
        super(message, cause);
    }
}

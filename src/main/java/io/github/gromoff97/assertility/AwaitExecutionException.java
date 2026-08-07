package io.github.gromoff97.assertility;

import java.io.Serial;

public final class AwaitExecutionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    AwaitExecutionException(Throwable cause) {
        super("Unexpected checked exception while evaluating await source", cause);
    }
}

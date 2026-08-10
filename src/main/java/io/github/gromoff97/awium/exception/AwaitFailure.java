package io.github.gromoff97.awium.exception;

import java.io.Serial;

public abstract class AwaitFailure extends AssertionError {

    @Serial
    private static final long serialVersionUID = 1L;

    AwaitFailure(String message, Throwable cause) {
        super(message, cause);
    }
}

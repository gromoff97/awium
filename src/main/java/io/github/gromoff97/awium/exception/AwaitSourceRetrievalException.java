package io.github.gromoff97.awium.exception;

import java.io.Serial;

public final class AwaitSourceRetrievalException extends AwaitUncontrolledException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AwaitSourceRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}

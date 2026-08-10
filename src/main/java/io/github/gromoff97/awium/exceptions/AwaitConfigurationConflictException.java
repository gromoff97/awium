package io.github.gromoff97.awium.exceptions;

import java.io.Serial;

public final class AwaitConfigurationConflictException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AwaitConfigurationConflictException(String message) {
        super(message);
    }
}

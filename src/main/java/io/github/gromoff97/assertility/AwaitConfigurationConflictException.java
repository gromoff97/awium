package io.github.gromoff97.assertility;

import java.io.Serial;

public final class AwaitConfigurationConflictException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    AwaitConfigurationConflictException(String message) {
        super(message);
    }
}

package io.github.gromoff97.awium.exception;

import java.io.Serial;

public final class AwaitConditionEvaluationException
        extends AwaitUncontrolledException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AwaitConditionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}

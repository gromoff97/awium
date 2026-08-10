package io.github.gromoff97.awium;

import java.io.Serial;

public final class AwaitConditionEvaluationException
        extends AwaitUncontrolledException {

    @Serial
    private static final long serialVersionUID = 1L;

    AwaitConditionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}

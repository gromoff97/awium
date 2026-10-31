package io.github.gromoff97.awium.result;

import java.io.Serializable;
import java.util.List;

import static java.util.Objects.requireNonNull;

public record AwaitFailure(Reason reason, String message, Throwable cause, List<Throwable> suppressed) implements Serializable {

    public AwaitFailure {
        requireNonNull(reason, "reason must not be null");
        requireNonNull(message, "message must not be null");
        suppressed = List.copyOf(suppressed);
    }

    @Override
    public String toString() {
        return message;
    }

    public enum Reason {
        TIMEOUT,
        PERSISTENCE_FAILED,
        SOURCE_FAILED,
        CONDITION_FAILED,
        INTERRUPTED,
        WAIT_FAILED
    }
}

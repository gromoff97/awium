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
        TIMEOUT("Wait timed out"),
        PERSISTENCE_FAILED("Condition did not stay satisfied"),
        SOURCE_FAILED("Source retrieval failed"),
        CONDITION_FAILED("Condition evaluation failed"),
        INTERRUPTED("Wait interrupted"),
        WAIT_FAILED("Waiting failed");

        private final String title;

        Reason(String title) { this.title = title; }

        public String title() { return title; }
    }
}

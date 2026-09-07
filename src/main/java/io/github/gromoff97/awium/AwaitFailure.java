package io.github.gromoff97.awium;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Structured failure returned by {@code tryUntil} and attached to exceptions from {@code until}.
 * The cause is the original throwable, or {@code null} for an ordinary mismatch.
 * If diagnostics fail, their cause takes precedence and the original cause is suppressed.
 */
public record AwaitFailure(Reason reason, String message, Throwable cause, List<Throwable> suppressed) {

    public AwaitFailure {
        requireNonNull(reason, "reason must not be null");
        requireNonNull(message, "message must not be null");
        suppressed = List.copyOf(suppressed);
    }

    public enum Reason {
        TIMEOUT, PERSISTENCE_FAILED, SOURCE_FAILED, CONDITION_FAILED,
        INTERRUPTED, WAIT_FAILED, DIAGNOSTICS_FAILED
    }
}

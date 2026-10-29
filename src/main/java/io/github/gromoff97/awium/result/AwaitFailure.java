package io.github.gromoff97.awium.result;

import java.io.Serializable;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Structured failure returned by {@code tryAwait(...).until(...)} and attached to exceptions from {@code await(...).until(...)}.
 * The cause is the original throwable, or {@code null} for an ordinary mismatch.
 * Diagnostic failures are retained in {@code suppressed} without replacing the reason or cause.
 * The message is saved when the wait ends. The cause and suppressed throwables
 * remain original references; later changes to them do not rewrite that message.
 */
public record AwaitFailure(Reason reason, String message, Throwable cause, List<Throwable> suppressed) implements Serializable {

    public AwaitFailure {
        requireNonNull(reason, "reason must not be null");
        requireNonNull(message, "message must not be null");
        suppressed = List.copyOf(suppressed);
    }

    /** Returns the saved diagnostic message without inspecting the original throwables again. */
    @Override
    public String toString() {
        return message;
    }

    public enum Reason {
        TIMEOUT, PERSISTENCE_FAILED, SOURCE_FAILED, CONDITION_FAILED,
        INTERRUPTED, WAIT_FAILED
    }
}

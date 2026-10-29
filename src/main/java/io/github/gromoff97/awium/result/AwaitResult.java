package io.github.gromoff97.awium.result;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Public {@code tryAwait(...).until(...)} result containing the terminal value or failure and its attempt history.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <RESULT> final value returned by the wait
 */
public sealed interface AwaitResult<OBSERVED, RESULT> {

    /**
     * Returns the retained attempts as an immutable list in chronological order.
     * The default retains the latest 256 attempts; -1 retains all attempts.
     * Observed values are retained by reference and may change after a check.
     */
    List<HistoryEntry<OBSERVED>> attempts();

    /**
     * Returns the successful value, including {@code null}, or throws the same kind of failure
     * as {@code await(...).until(...)}. Reading a result does not run the source or conditions again.
     *
     * @return the final value retained by a successful wait
     * @throws AwaitAssertionError if the wait timed out or its persistence check failed
     * @throws AwaitExecutionException if execution failed or the wait was interrupted
     */
    default RESULT orThrow() {
        return switch (this) {
            case Satisfied<OBSERVED, RESULT> success -> success.result();
            case Failed<OBSERVED, RESULT> failed -> {
                AwaitFailure failure = failed.failure();
                switch (failure.reason()) {
                    case TIMEOUT, PERSISTENCE_FAILED -> throw new AwaitAssertionError(failure);
                    default -> throw new AwaitExecutionException(failure);
                }
            }
        };
    }

    record Satisfied<OBSERVED, RESULT>(List<HistoryEntry<OBSERVED>> attempts,
            RESULT result) implements AwaitResult<OBSERVED, RESULT> {

        public Satisfied {
            attempts = List.copyOf(attempts);
        }
    }

    record Failed<OBSERVED, RESULT>(List<HistoryEntry<OBSERVED>> attempts,
            AwaitFailure failure) implements AwaitResult<OBSERVED, RESULT> {

        public Failed {
            attempts = List.copyOf(attempts);
            requireNonNull(failure, "failure must not be null");
        }
    }
}

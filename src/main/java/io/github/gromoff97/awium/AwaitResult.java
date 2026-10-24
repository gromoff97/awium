package io.github.gromoff97.awium;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Public {@code tryUntil} result containing the terminal value or failure and its bounded attempt history.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <RESULT> value produced by the condition
 */
public sealed interface AwaitResult<OBSERVED, RESULT> {

    /**
     * Returns the immutable records retained by this result. Engine-produced results keep
     * at most 256 records: the first attempt and the most recent 255 attempts.
     * Observed values and causes are retained by reference.
     */
    List<AttemptRecord<OBSERVED, RESULT>> attempts();

    /** Returns the last retained attempt's number, or zero when no attempts are retained. */
    default long totalAttempts() {
        return attempts().isEmpty() ? 0 : attempts().getLast().number();
    }

    record Satisfied<OBSERVED, RESULT>(List<AttemptRecord<OBSERVED, RESULT>> attempts,
            RESULT result) implements AwaitResult<OBSERVED, RESULT> {

        public Satisfied {
            attempts = List.copyOf(attempts);
        }
    }

    record Failed<OBSERVED, RESULT>(List<AttemptRecord<OBSERVED, RESULT>> attempts,
            AwaitFailure failure) implements AwaitResult<OBSERVED, RESULT> {

        public Failed {
            attempts = List.copyOf(attempts);
            requireNonNull(failure, "failure must not be null");
        }
    }
}

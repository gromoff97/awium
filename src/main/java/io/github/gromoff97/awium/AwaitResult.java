package io.github.gromoff97.awium;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Public {@code tryUntil} result containing the terminal value or failure and its bounded, compressed attempt history.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <RESULT> value produced by the condition
 */
public sealed interface AwaitResult<OBSERVED, RESULT> {

    List<AttemptRecord<OBSERVED, RESULT>> attempts();

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

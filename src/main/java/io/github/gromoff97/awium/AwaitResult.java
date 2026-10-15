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

    List<AwaitAttempt<OBSERVED, RESULT>> attempts();

    long totalAttempts();

    record Satisfied<OBSERVED, RESULT>(List<AwaitAttempt<OBSERVED, RESULT>> attempts,
            long totalAttempts, RESULT result) implements AwaitResult<OBSERVED, RESULT> {

        public Satisfied {
            attempts = List.copyOf(attempts);
            requireCount(totalAttempts);
        }
    }

    record Failed<OBSERVED, RESULT>(List<AwaitAttempt<OBSERVED, RESULT>> attempts,
            long totalAttempts, AwaitFailure failure) implements AwaitResult<OBSERVED, RESULT> {

        public Failed {
            attempts = List.copyOf(attempts);
            requireCount(totalAttempts);
            requireNonNull(failure, "failure must not be null");
        }
    }

    private static void requireCount(long totalAttempts) {
        if (totalAttempts < 0) {
            throw new IllegalArgumentException("total attempts must be non-negative");
        }
    }
}

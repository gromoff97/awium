package io.github.gromoff97.awium.results;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Public {@code tryAwait} result containing the terminal value or failure and its compressed attempt history.
 *
 * @param <Observed> complete value returned by the source
 * @param <Result> value produced by the condition
 */
public sealed interface AwaitResult<Observed, Result> {

    List<AwaitAttempt<Observed, Result>> attempts();

    long totalAttempts();

    record Satisfied<Observed, Result>(List<AwaitAttempt<Observed, Result>> attempts,
            long totalAttempts, Result result) implements AwaitResult<Observed, Result> {

        public Satisfied {
            attempts = List.copyOf(attempts);
            requireCount(totalAttempts);
        }
    }

    record Failed<Observed, Result>(List<AwaitAttempt<Observed, Result>> attempts,
            long totalAttempts, Throwable failure) implements AwaitResult<Observed, Result> {

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

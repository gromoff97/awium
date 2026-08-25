package io.github.gromoff97.awium.await;

import java.util.List;

import static java.util.Objects.requireNonNull;

public sealed interface AwaitResult<S, R> {

    List<AwaitAttempt<S, R>> attempts();

    long totalAttempts();

    record Satisfied<S, R>(List<AwaitAttempt<S, R>> attempts, long totalAttempts, R result) implements AwaitResult<S, R> {

        public Satisfied {
            attempts = List.copyOf(attempts);
            requireCount(totalAttempts);
        }
    }

    record Failed<S, R>(List<AwaitAttempt<S, R>> attempts, long totalAttempts, Throwable failure) implements AwaitResult<S, R> {

        public Failed {
            attempts = List.copyOf(attempts);
            requireCount(totalAttempts);
            requireNonNull(failure, "failure must not be null");
        }
    }

    private static void requireCount(long totalAttempts) {
        if (totalAttempts < 0) {
            throw new IllegalArgumentException("total attempts must not be negative");
        }
    }
}

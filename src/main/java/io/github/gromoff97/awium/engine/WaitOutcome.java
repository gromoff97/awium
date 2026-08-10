package io.github.gromoff97.awium.engine;

import static java.util.Objects.requireNonNull;

public sealed interface WaitOutcome<R> permits WaitOutcome.Success, WaitOutcome.TimeoutBetweenObservations, WaitOutcome.LateUnsatisfiedTimeout, WaitOutcome.LateSatisfiedTimeout, WaitOutcome.StabilityLoss, WaitOutcome.Uncontrolled {

    Attempt<R> attempt();

    default long completedAttempts() {
        return attempt().number();
    }

    record Success<R>(long startedNanos, long acquiredNanos, long completedNanos,
            Attempt.Satisfied<R> attempt) implements WaitOutcome<R> {
        public Success {
            requireNonNull(attempt);
        }
    }

    record TimeoutBetweenObservations<R>(long startedNanos, long completedNanos,
            Attempt.Unsatisfied<R> attempt) implements WaitOutcome<R> {
        public TimeoutBetweenObservations {
            requireNonNull(attempt);
        }
    }

    record LateUnsatisfiedTimeout<R>(long startedNanos, long completedNanos,
            Attempt.Unsatisfied<R> attempt) implements WaitOutcome<R> {
        public LateUnsatisfiedTimeout {
            requireNonNull(attempt);
        }
    }

    record LateSatisfiedTimeout<R>(long startedNanos, long completedNanos,
            Attempt.Satisfied<R> attempt) implements WaitOutcome<R> {
        public LateSatisfiedTimeout {
            requireNonNull(attempt);
        }
    }

    record StabilityLoss<R>(long startedNanos, long acquiredNanos, long completedNanos,
            Attempt.Unsatisfied<R> attempt) implements WaitOutcome<R> {
        public StabilityLoss {
            requireNonNull(attempt);
        }
    }

    record Uncontrolled<R>(Attempt.Uncontrolled<R> attempt) implements WaitOutcome<R> {
        public Uncontrolled {
            requireNonNull(attempt);
        }
    }
}

package io.github.gromoff97.awium.engine;

public sealed interface WaitOutcome<R> permits Attempt.Satisfied, Attempt.Uncontrolled, WaitOutcome.TimeoutBetweenObservations, WaitOutcome.LateUnsatisfiedTimeout, WaitOutcome.LateSatisfiedTimeout, WaitOutcome.StabilityLoss {

    Attempt<R> attempt();

    record TimeoutBetweenObservations<R>(long startedNanos, long completedNanos,
            Attempt.Unsatisfied<R> attempt) implements WaitOutcome<R> {}

    record LateUnsatisfiedTimeout<R>(long startedNanos,
            Attempt.Unsatisfied<R> attempt) implements WaitOutcome<R> {}

    record LateSatisfiedTimeout<R>(long startedNanos,
            Attempt.Satisfied<R> attempt) implements WaitOutcome<R> {}

    record StabilityLoss<R>(long startedNanos, long acquiredNanos,
            Attempt.Unsatisfied<R> attempt) implements WaitOutcome<R> {}
}

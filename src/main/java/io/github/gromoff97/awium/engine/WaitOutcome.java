package io.github.gromoff97.awium.engine;

public sealed interface WaitOutcome<R> {

    Attempt<R> attempt();

    sealed interface Attempt<R> {

        long number();

        long completedNanos();

        record Satisfied<R>(Object actual, R result, long number, long completedNanos) implements Attempt<R>, WaitOutcome<R> {
            @Override
            public Satisfied<R> attempt() {
                return this;
            }
        }

        record Unsatisfied<R>(Object actual, String mismatch, AssertionError assertionCause,
                long number, long completedNanos) implements Attempt<R> {}

        sealed interface Uncontrolled<R> extends Attempt<R>, WaitOutcome<R> {

            Origin origin();

            Throwable cause();

            @Override
            default Uncontrolled<R> attempt() {
                return this;
            }

            record BeforeObservation<R>(Origin origin, Throwable cause, long number,
                    long completedNanos) implements Uncontrolled<R> {}

            record AfterObservation<R>(Origin origin, Object actual, Throwable cause,
                    long number, long completedNanos) implements Uncontrolled<R> {}
        }

        enum Origin { WAITING, SOURCE, CONDITION }
    }

    record TimeoutBetweenObservations<R>(long startedNanos, long completedNanos,
            Attempt.Unsatisfied<R> attempt) implements WaitOutcome<R> {}

    record LateUnsatisfiedTimeout<R>(long startedNanos,
            Attempt.Unsatisfied<R> attempt) implements WaitOutcome<R> {}

    record LateSatisfiedTimeout<R>(long startedNanos,
            Attempt.Satisfied<R> attempt) implements WaitOutcome<R> {}

    record StabilityLoss<R>(long startedNanos, long acquiredNanos,
            Attempt.Unsatisfied<R> attempt) implements WaitOutcome<R> {}
}

package io.github.gromoff97.awium.engine;

public sealed interface Attempt<R> permits Attempt.Satisfied, Attempt.Unsatisfied, Attempt.Uncontrolled {

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

    sealed interface Uncontrolled<R> extends Attempt<R>, WaitOutcome<R> permits Uncontrolled.BeforeObservation, Uncontrolled.AfterObservation {

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

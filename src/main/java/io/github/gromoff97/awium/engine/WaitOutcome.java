package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.await.AwaitAttempt;

public sealed interface WaitOutcome<S, R> {

    AwaitAttempt<S, R> attempt();

    record Satisfied<S, R>(AwaitAttempt<S, R> attempt) implements WaitOutcome<S, R> {}

    record Uncontrolled<S, R>(AwaitAttempt<S, R> attempt) implements WaitOutcome<S, R> {}

    record TimeoutBetweenObservations<S, R>(long elapsedNanos,
            AwaitAttempt<S, R> attempt) implements WaitOutcome<S, R> {}

    record LateTimeout<S, R>(AwaitAttempt<S, R> attempt) implements WaitOutcome<S, R> {}

    record PersistenceFailure<S, R>(long acquiredAfterNanos,
            AwaitAttempt<S, R> attempt) implements WaitOutcome<S, R> {}
}

package io.github.gromoff97.awium.internal.engine;

import io.github.gromoff97.awium.results.AwaitAttempt;

/**
 * Terminal outcome of a complete acquisition and optional persistence execution.
 *
 * @param <Observed> complete value returned by the source
 * @param <Result> value produced by the condition
 */
public sealed interface WaitCompletion<Observed, Result> {

    AwaitAttempt<Observed, Result> attempt();

    record Satisfied<Observed, Result>(AwaitAttempt<Observed, Result> attempt) implements WaitCompletion<Observed, Result> {}

    record Uncontrolled<Observed, Result>(AwaitAttempt<Observed, Result> attempt) implements WaitCompletion<Observed, Result> {}

    record TimeoutBetweenObservations<Observed, Result>(long elapsedNanos,
            AwaitAttempt<Observed, Result> attempt) implements WaitCompletion<Observed, Result> {}

    record LateTimeout<Observed, Result>(AwaitAttempt<Observed, Result> attempt) implements WaitCompletion<Observed, Result> {}

    record PersistenceFailure<Observed, Result>(long acquiredAfterNanos,
            AwaitAttempt<Observed, Result> attempt) implements WaitCompletion<Observed, Result> {}
}

package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.AttemptRecord;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.AwaitFailure;
import java.time.Duration;

import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;

/** Terminal outcome and diagnostics of a complete acquisition and optional persistence execution. */
public sealed interface WaitCompletion {

    AttemptRecord<?> attempt();

    default AwaitFailure.Reason failureReason() {
        return switch (this) {
            case PersistenceFailure ignored -> PERSISTENCE_FAILED;
            case Uncontrolled failure -> failure.interrupted()
                    ? INTERRUPTED : switch (((AttemptResult.Uncontrolled) attempt().result()).operation()) {
                        case WAITING -> WAIT_FAILED;
                        case SOURCE -> SOURCE_FAILED;
                        case CONDITION -> CONDITION_FAILED;
                    };
            case Timeout ignored -> TIMEOUT;
            case Satisfied ignored -> throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    record Satisfied(AttemptRecord<?> attempt) implements WaitCompletion {}

    record Uncontrolled(AttemptRecord<?> attempt,
            boolean interrupted) implements WaitCompletion {

        public Uncontrolled(AttemptRecord<?> attempt) {
            this(attempt, Thread.currentThread().isInterrupted());
        }
    }

    record Timeout(Duration elapsed,
            AttemptRecord<?> attempt) implements WaitCompletion {

        public Timeout(AttemptRecord<?> attempt) {
            this(attempt.timing().completionOffset(), attempt);
        }
    }

    record PersistenceFailure(Duration acquiredAfter,
            AttemptRecord<?> attempt) implements WaitCompletion {}
}

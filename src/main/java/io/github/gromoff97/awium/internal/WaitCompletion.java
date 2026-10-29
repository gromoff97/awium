package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.HistoryEntry;
import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.AwaitFailure;
import java.time.Duration;

import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;

/** Terminal outcome and diagnostics of a complete acquisition and optional persistence execution. */
public sealed interface WaitCompletion {

    HistoryEntry<?> attempt();

    default AwaitFailure.Reason failureReason() {
        return switch (this) {
            case PersistenceFailure ignored -> PERSISTENCE_FAILED;
            case Uncontrolled failure -> failure.interrupted()
                    ? INTERRUPTED : switch (((AttemptStatus.Uncontrolled) attempt().result()).operation()) {
                        case WAITING -> WAIT_FAILED;
                        case SOURCE -> SOURCE_FAILED;
                        case CONDITION -> CONDITION_FAILED;
                    };
            case Timeout ignored -> TIMEOUT;
            case Satisfied ignored -> throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    record Satisfied(HistoryEntry<?> attempt) implements WaitCompletion {}

    record Uncontrolled(HistoryEntry<?> attempt,
            boolean interrupted) implements WaitCompletion {

        public Uncontrolled(HistoryEntry<?> attempt) {
            this(attempt, Thread.currentThread().isInterrupted());
        }
    }

    record Timeout(Duration elapsed,
            HistoryEntry<?> attempt) implements WaitCompletion {

        public Timeout(HistoryEntry<?> attempt) {
            this(attempt.timing().completionOffset(), attempt);
        }
    }

    record PersistenceFailure(Duration acquiredAfter,
            HistoryEntry<?> attempt) implements WaitCompletion {}
}

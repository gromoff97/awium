package io.github.gromoff97.awium;

import java.time.Duration;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;

/**
 * Terminal outcome of a complete acquisition and optional persistence execution.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <RESULT> value produced by the condition
 */
sealed interface WaitCompletion<OBSERVED, RESULT> {

    AttemptRecord<OBSERVED, RESULT> attempt();

    default AwaitFailure.Reason failureReason() {
        return switch (this) {
            case PersistenceFailure<?, ?> ignored -> PERSISTENCE_FAILED;
            case Uncontrolled<?, ?> failure -> failure.interrupted()
                    ? INTERRUPTED : switch (attempt().outcome()) {
                        case AttemptRecord.Outcome.WaitingFailed<?, ?> value -> WAIT_FAILED;
                        case AttemptRecord.Outcome.SourceRetrievalFailed<?, ?> value -> SOURCE_FAILED;
                        case AttemptRecord.Outcome.ConditionFailed<?, ?> value -> CONDITION_FAILED;
                        default -> throw new IllegalArgumentException("attempt is not uncontrolled");
                    };
            case Timeout<?, ?> ignored -> TIMEOUT;
            case Satisfied<?, ?> ignored -> throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    record Satisfied<OBSERVED, RESULT>(AttemptRecord<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {

        RESULT result() {
            var evaluated = (AttemptRecord.Outcome.Evaluated<OBSERVED, RESULT>) attempt.outcome();
            return ((ConditionResult.Satisfied<? extends RESULT>) evaluated.evaluation()).result();
        }
    }

    record Uncontrolled<OBSERVED, RESULT>(AttemptRecord<OBSERVED, RESULT> attempt,
            boolean interrupted) implements WaitCompletion<OBSERVED, RESULT> {

        Uncontrolled(AttemptRecord<OBSERVED, RESULT> attempt) {
            this(attempt, Thread.currentThread().isInterrupted());
        }
    }

    record Timeout<OBSERVED, RESULT>(Duration elapsed,
            AttemptRecord<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {

        Timeout(AttemptRecord<OBSERVED, RESULT> attempt) {
            this(attempt.timing().completionOffset(), attempt);
        }
    }

    record PersistenceFailure<OBSERVED, RESULT>(Duration acquiredAfter,
            AttemptRecord<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {}
}

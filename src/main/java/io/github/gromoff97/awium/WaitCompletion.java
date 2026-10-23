package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;

/**
 * Terminal outcome of a complete acquisition and optional persistence execution.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <RESULT> value produced by the condition
 */
sealed interface WaitCompletion<OBSERVED, RESULT> {

    AttemptRecord<OBSERVED, RESULT> attempt();

    default Throwable cause() {
        return switch (attempt().outcome()) {
            case AttemptRecord.Outcome.WaitingFailed<?, ?> value -> value.failure();
            case AttemptRecord.Outcome.SourceRetrievalFailed<?, ?> value -> value.failure();
            case AttemptRecord.Outcome.SourceInterrupted<?, ?> value -> value.failure();
            case AttemptRecord.Outcome.ConditionFailed<?, ?> value -> value.failure();
            case AttemptRecord.Outcome.Evaluated<?, ?> value -> switch (value.evaluation()) {
                case ConditionResult.Satisfied<?> ignored -> null;
                case ConditionResult.Unsatisfied<?> result -> result.assertion();
            };
        };
    }

    default AwaitFailure.Reason failureReason() {
        return switch (this) {
            case PersistenceFailure<?, ?> ignored -> PERSISTENCE_FAILED;
            case Uncontrolled<?, ?> ignored -> cause() instanceof InterruptedException
                    ? INTERRUPTED : switch (attempt().outcome()) {
                        case AttemptRecord.Outcome.WaitingFailed<?, ?> value -> WAIT_FAILED;
                        case AttemptRecord.Outcome.SourceRetrievalFailed<?, ?> value -> SOURCE_FAILED;
                        case AttemptRecord.Outcome.ConditionFailed<?, ?> value -> CONDITION_FAILED;
                        default -> throw new IllegalArgumentException("attempt is not uncontrolled");
                    };
            case TimeoutBetweenObservations<?, ?> ignored -> TIMEOUT;
            case LateTimeout<?, ?> ignored -> TIMEOUT;
            case Satisfied<?, ?> ignored -> throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    record Satisfied<OBSERVED, RESULT>(AttemptRecord<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {

        RESULT result() {
            var evaluated = (AttemptRecord.Outcome.Evaluated<OBSERVED, RESULT>) attempt.outcome();
            return ((ConditionResult.Satisfied<? extends RESULT>) evaluated.evaluation()).result();
        }
    }

    record Uncontrolled<OBSERVED, RESULT>(AttemptRecord<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {}

    record TimeoutBetweenObservations<OBSERVED, RESULT>(long elapsedNanos,
            AttemptRecord<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {}

    record LateTimeout<OBSERVED, RESULT>(AttemptRecord<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {}

    record PersistenceFailure<OBSERVED, RESULT>(long acquiredAfterNanos,
            AttemptRecord<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {}
}

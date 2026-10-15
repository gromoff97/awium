package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;

/**
 * Terminal outcome of a complete acquisition and optional persistence execution.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <RESULT> value produced by the condition
 */
sealed interface WaitCompletion<OBSERVED, RESULT> {

    AwaitAttempt<OBSERVED, RESULT> attempt();

    default Throwable cause() {
        return switch (attempt().outcome()) {
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> value -> value.failure();
            case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> value -> value.failure();
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> value -> value.failure();
            case AwaitAttempt.Outcome.Evaluated<?, ?> value -> switch (value.evaluation()) {
                case ConditionResult.Satisfied<?> ignored -> null;
                case ConditionResult.Unsatisfied<?> result -> result.assertion();
                case ConditionResult.Uncontrolled<?> result -> result.cause();
            };
        };
    }

    default AwaitFailure.Reason failureReason() {
        return switch (this) {
            case PersistenceFailure<?, ?> ignored -> PERSISTENCE_FAILED;
            case Uncontrolled<?, ?> ignored -> cause() instanceof InterruptedException
                    ? INTERRUPTED : switch (attempt().outcome()) {
                        case AwaitAttempt.Outcome.WaitingFailed<?, ?> value -> WAIT_FAILED;
                        case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> value -> SOURCE_FAILED;
                        case AwaitAttempt.Outcome.Evaluated<?, ?> value -> CONDITION_FAILED;
                        default -> throw new IllegalArgumentException("attempt is not uncontrolled");
                    };
            case TimeoutBetweenObservations<?, ?> ignored -> TIMEOUT;
            case LateTimeout<?, ?> ignored -> TIMEOUT;
            case Satisfied<?, ?> ignored -> throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    record Satisfied<OBSERVED, RESULT>(AwaitAttempt<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {

        RESULT result() {
            var evaluated = (AwaitAttempt.Outcome.Evaluated<OBSERVED, RESULT>) attempt.outcome();
            return ((ConditionResult.Satisfied<? extends RESULT>) evaluated.evaluation()).result();
        }
    }

    record Uncontrolled<OBSERVED, RESULT>(AwaitAttempt<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {}

    record TimeoutBetweenObservations<OBSERVED, RESULT>(long elapsedNanos,
            AwaitAttempt<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {}

    record LateTimeout<OBSERVED, RESULT>(AwaitAttempt<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {}

    record PersistenceFailure<OBSERVED, RESULT>(long acquiredAfterNanos,
            AwaitAttempt<OBSERVED, RESULT> attempt) implements WaitCompletion<OBSERVED, RESULT> {}
}

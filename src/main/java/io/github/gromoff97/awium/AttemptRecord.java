package io.github.gromoff97.awium;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * One polling attempt with its phase, timing, observation, and condition outcome.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <RESULT> value produced by the condition
 * @param number one-based attempt number
 * @param phase acquisition or persistence phase
 * @param condition condition associated with this attempt, including attempts that fail before evaluation
 * @param progress last known sequence progress, or null if no sequence stage has been evaluated
 * @param outcome terminal outcome of this attempt
 */
public record AttemptRecord<OBSERVED, RESULT>(long number, Phase phase,
        Condition<?> condition, Progress progress, Outcome<OBSERVED, RESULT> outcome) {

    public AttemptRecord {
        if (number <= 0) {
            throw new IllegalArgumentException("attempt number must be positive");
        }
        requireNonNull(phase, "phase must not be null");
        requireNonNull(condition, "condition must not be null");
        requireNonNull(outcome, "outcome must not be null");
    }

    public enum Phase { ACQUISITION, PERSISTENCE }

    public sealed interface Outcome<OBSERVED, RESULT> {

        Timing timing();

        Throwable cause();

        /** An observation and the exact result returned by its condition evaluation. */
        record Evaluated<OBSERVED, RESULT>(Timing timing,
                OBSERVED observed, ConditionResult<? extends RESULT> evaluation) implements Outcome<OBSERVED, RESULT> {

            public Evaluated {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(evaluation, "evaluation must not be null");
            }

            @Override
            public Throwable cause() {
                return evaluation.cause();
            }
        }

        record ConditionFailed<OBSERVED, RESULT>(Timing timing,
                OBSERVED observed, Throwable cause) implements Outcome<OBSERVED, RESULT> {

            public ConditionFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(cause, "cause must not be null");
            }
        }

        record WaitingFailed<OBSERVED, RESULT>(Timing timing,
                Throwable cause) implements Outcome<OBSERVED, RESULT> {

            public WaitingFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(cause, "cause must not be null");
            }
        }

        record SourceRetrievalFailed<OBSERVED, RESULT>(Timing timing,
                Throwable cause) implements Outcome<OBSERVED, RESULT> {

            public SourceRetrievalFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(cause, "cause must not be null");
            }
        }

        record SourceInterrupted<OBSERVED, RESULT>(Timing timing, OBSERVED observed,
                Throwable cause) implements Outcome<OBSERVED, RESULT> {

            public SourceInterrupted {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(cause, "cause must not be null");
            }
        }
    }

    /**
     * Last known sequence progress. An intermediate success moves the active condition
     * to the next stage; the final stage remains active for persistence checks. Attempts
     * that fail before condition evaluation retain this snapshot from the preceding attempt.
     *
     * @param capturedStages number of stages whose successful values are currently captured
     * @param totalStages number of stages in the sequence
     * @param lastEvaluatedStageNumber one-based number of the last evaluated stage
     * @param activeCondition condition for the next stage after an intermediate success,
     *                        otherwise for the evaluated stage
     */
    public record Progress(int capturedStages, int totalStages, int lastEvaluatedStageNumber,
            Condition<?> activeCondition) {

        public Progress {
            if (capturedStages < 0 || capturedStages > totalStages
                    || lastEvaluatedStageNumber <= 0 || lastEvaluatedStageNumber > totalStages) {
                throw new IllegalArgumentException("invalid sequence progress");
            }
            requireNonNull(activeCondition, "active condition must not be null");
        }
    }

    /**
     * Checkpoints measured from the start of the terminal call. The attempt starts before waiting;
     * retrieval starts before calling the source, and observation marks its successful return.
     * A null retrieval or observation offset means that checkpoint was not reached.
     */
    public record Timing(Duration startOffset, Duration retrievalOffset,
            Duration observationOffset, Duration completionOffset) {

        public Timing {
            requireNonNull(startOffset, "start offset must not be null");
            requireNonNull(completionOffset, "completion offset must not be null");
        }
    }
}

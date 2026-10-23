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
 * @param outcome terminal outcome of this attempt
 */
public record AttemptRecord<OBSERVED, RESULT>(long number, Phase phase,
        Outcome<OBSERVED, RESULT> outcome) {

    public AttemptRecord {
        if (number <= 0) {
            throw new IllegalArgumentException("attempt number must be positive");
        }
        requireNonNull(phase, "phase must not be null");
        requireNonNull(outcome, "outcome must not be null");
    }

    public enum Phase { ACQUISITION, PERSISTENCE }

    public sealed interface Outcome<OBSERVED, RESULT> {

        Timing timing();

        /** An observation and the exact result returned by its condition evaluation. */
        record Evaluated<OBSERVED, RESULT>(Timing.AfterObservation timing,
                OBSERVED observed, ConditionResult<? extends RESULT> evaluation,
                Condition<?, ?> condition, Progress progress) implements Outcome<OBSERVED, RESULT> {

            public Evaluated {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(evaluation, "evaluation must not be null");
                requireNonNull(condition, "condition must not be null");
            }
        }

        record ConditionFailed<OBSERVED, RESULT>(Timing.AfterObservation timing,
                OBSERVED observed, Throwable failure, Condition<?, ?> condition,
                Progress progress) implements Outcome<OBSERVED, RESULT> {

            public ConditionFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
                requireNonNull(condition, "condition must not be null");
            }
        }

        record WaitingFailed<OBSERVED, RESULT>(Timing.BeforeRetrieval timing,
                Throwable failure) implements Outcome<OBSERVED, RESULT> {

            public WaitingFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
            }
        }

        record SourceRetrievalFailed<OBSERVED, RESULT>(Timing.BeforeObservation timing,
                Throwable failure) implements Outcome<OBSERVED, RESULT> {

            public SourceRetrievalFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
            }
        }

        record SourceInterrupted<OBSERVED, RESULT>(Timing.AfterObservation timing, OBSERVED observed,
                InterruptedException failure) implements Outcome<OBSERVED, RESULT> {

            public SourceInterrupted {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
            }
        }
    }

    public record Progress(int capturedStages, int totalStages, int evaluatedStageNumber,
            Condition<?, ?> condition) {

        public Progress {
            if (capturedStages < 0 || capturedStages > totalStages
                    || evaluatedStageNumber <= 0 || evaluatedStageNumber > totalStages) {
                throw new IllegalArgumentException("invalid sequence progress");
            }
            requireNonNull(condition, "condition must not be null");
        }
    }

    public sealed interface Timing {

        Duration startOffset();

        Duration completionOffset();

        record BeforeRetrieval(Duration startOffset, Duration completionOffset) implements Timing {

            public BeforeRetrieval {
                requireNonNull(startOffset, "start offset must not be null");
                requireNonNull(completionOffset, "completion offset must not be null");
            }
        }

        record BeforeObservation(Duration startOffset, Duration retrievalOffset, Duration completionOffset) implements Timing {

            public BeforeObservation {
                requireNonNull(startOffset, "start offset must not be null");
                requireNonNull(retrievalOffset, "retrieval offset must not be null");
                requireNonNull(completionOffset, "completion offset must not be null");
            }
        }

        record AfterObservation(Duration startOffset, Duration retrievalOffset,
                Duration observationOffset, Duration completionOffset) implements Timing {

            public AfterObservation {
                requireNonNull(startOffset, "start offset must not be null");
                requireNonNull(retrievalOffset, "retrieval offset must not be null");
                requireNonNull(observationOffset, "observation offset must not be null");
                requireNonNull(completionOffset, "completion offset must not be null");
            }
        }
    }
}

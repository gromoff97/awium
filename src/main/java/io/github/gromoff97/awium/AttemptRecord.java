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

        Throwable cause();

        /** An observation and the exact result returned by its condition evaluation. */
        record Evaluated<OBSERVED, RESULT>(Timing timing,
                OBSERVED observed, ConditionResult<? extends RESULT> evaluation,
                Condition<?, ?> condition, Progress progress) implements Outcome<OBSERVED, RESULT> {

            public Evaluated {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(evaluation, "evaluation must not be null");
                requireNonNull(condition, "condition must not be null");
            }

            @Override
            public Throwable cause() {
                return evaluation.cause();
            }
        }

        record ConditionFailed<OBSERVED, RESULT>(Timing timing,
                OBSERVED observed, Throwable cause, Condition<?, ?> condition,
                Progress progress) implements Outcome<OBSERVED, RESULT> {

            public ConditionFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(cause, "cause must not be null");
                requireNonNull(condition, "condition must not be null");
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

    // A null checkpoint means this stage was not reached.
    public record Timing(Duration startOffset, Duration retrievalOffset,
            Duration observationOffset, Duration completionOffset) {

        public Timing {
            requireNonNull(startOffset, "start offset must not be null");
            requireNonNull(completionOffset, "completion offset must not be null");
        }
    }
}

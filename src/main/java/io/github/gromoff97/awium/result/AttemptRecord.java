package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.Condition;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * One polling attempt with its phase, timing, observation, and condition outcome.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param number one-based attempt number
 * @param phase acquisition or persistence phase
 * @param timing checkpoints measured from the start of the terminal call
 * @param condition condition associated with this attempt, including attempts that fail before evaluation
 * @param progress sequence snapshot, or null for a single check
 * @param observed complete source value, including a legitimate null; consult hasObservation() for availability
 * @param result status and diagnostics of this attempt
 */
public record AttemptRecord<OBSERVED>(long number, Phase phase, Timing timing,
        Condition condition, Progress progress, OBSERVED observed, AttemptResult result) {

    public AttemptRecord {
        if (number <= 0) {
            throw new IllegalArgumentException("attempt number must be positive");
        }
        requireNonNull(phase, "phase must not be null");
        requireNonNull(timing, "timing must not be null");
        requireNonNull(condition, "condition must not be null");
        requireNonNull(result, "result must not be null");
    }

    public enum Phase { ACQUISITION, PERSISTENCE }

    /** Distinguishes a returned null from a failure before the source produced a value. */
    public boolean hasObservation() {
        return timing.observationOffset() != null;
    }

    /**
     * Snapshot of the sequence during this attempt. The stage identifies the check targeted by
     * this attempt, including attempts that fail before evaluation. The count is taken after
     * evaluation, or retained unchanged when waiting or source retrieval fails.
     *
     * @param sequence immutable definition shared by all attempts of this sequence
     * @param stageNumber one-based stage targeted by this attempt
     * @param capturedStages number of successful source observations currently retained
     */
    public record Progress(Condition.Captured sequence, int stageNumber, int capturedStages) {

        public Progress {
            requireNonNull(sequence, "sequence must not be null");
            int total = sequence.stages().size();
            if (stageNumber <= 0 || stageNumber > total || capturedStages < 0 || capturedStages > total) {
                throw new IllegalArgumentException("invalid sequence progress");
            }
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

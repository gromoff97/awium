package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;

/**
 * Transient execution context needed by scheduling and terminal failure diagnostics.
 * Elapsed nanoseconds use unsigned arithmetic when acquisition plus persistence exceeds Long.MAX_VALUE.
 */
public record ExecutionStep<OBSERVED>(Condition definition, int stageNumber, int capturedStages,
        Phase phase, long completedAfterNanos, Attempt<OBSERVED> attempt) {

    public enum Phase { ACQUISITION, PERSISTENCE }

    public Condition condition() {
        return definition instanceof Condition.Captured sequence
                ? sequence.stages().get(stageNumber - 1) : definition;
    }
}

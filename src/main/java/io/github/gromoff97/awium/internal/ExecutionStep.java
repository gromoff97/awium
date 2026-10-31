package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;

public record ExecutionStep<OBSERVED>(Condition definition, int stageNumber, int completedStages,
        Phase phase, long completedAfterNanos, Attempt<OBSERVED> attempt) {

    public enum Phase {
        ACQUISITION,
        PERSISTENCE
    }

    Condition condition() {
        return definition instanceof Condition.Captured sequence
                ? sequence.stages().get(stageNumber - 1) : definition;
    }
}

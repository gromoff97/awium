package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.Attempt;

public record ExecutionStep<OBSERVED>(int totalStages, int stageNumber, int completedStages,
        Phase phase, long completedAfterNanos, Attempt<OBSERVED> attempt) {

    public enum Phase {
        ACQUISITION,
        PERSISTENCE
    }

    boolean isComplete() {
        return completedStages == totalStages;
    }
}

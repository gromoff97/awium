package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.result.Attempt;

public record ExecutionStep<OBSERVED>(boolean complete,
        Phase phase, long completedAfterNanos, Attempt<OBSERVED> attempt) {

    public enum Phase {
        ACQUISITION,
        PERSISTENCE
    }

}

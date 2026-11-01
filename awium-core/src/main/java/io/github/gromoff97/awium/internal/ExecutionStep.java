package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.Attempt;

record ExecutionStep<OBSERVED>(boolean complete,
        Phase phase, long completedAfterNanos, Attempt<OBSERVED> attempt) {

    public enum Phase {
        ACQUISITION,
        PERSISTENCE
    }

}

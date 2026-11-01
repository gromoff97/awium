package io.github.gromoff97.awium.internal;

import static java.lang.Thread.currentThread;

sealed interface WaitCompletion {

    ExecutionStep<?> step();

    record SatisfiedWait(ExecutionStep<?> step) implements WaitCompletion {}

    record Uncontrolled(ExecutionStep<?> step,
            boolean interrupted) implements WaitCompletion {

        public Uncontrolled(ExecutionStep<?> step) {
            this(step, currentThread().isInterrupted());
        }
    }

    record Timeout(long elapsedNanos,
            ExecutionStep<?> step) implements WaitCompletion {

        public Timeout(ExecutionStep<?> step) {
            this(step.completedAfterNanos(), step);
        }
    }

    record PersistenceFailure(long acquiredAfterNanos,
            ExecutionStep<?> step) implements WaitCompletion {}
}

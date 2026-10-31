package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitFailure;

import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;

public sealed interface WaitCompletion {

    ExecutionStep<?> step();

    default AwaitFailure.Reason failureReason() {
        return switch (this) {
            case PersistenceFailure ignored -> PERSISTENCE_FAILED;
            case Uncontrolled failure -> failure.interrupted()
                    ? INTERRUPTED : switch (((Attempt.Failure<?>) step().attempt()).operation()) {
                        case WAITING -> WAIT_FAILED;
                        case SOURCE -> SOURCE_FAILED;
                        case CONDITION -> CONDITION_FAILED;
                    };
            case Timeout ignored -> TIMEOUT;
            case Satisfied ignored -> throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    record Satisfied(ExecutionStep<?> step) implements WaitCompletion {}

    record Uncontrolled(ExecutionStep<?> step,
            boolean interrupted) implements WaitCompletion {

        Uncontrolled(ExecutionStep<?> step) {
            this(step, Thread.currentThread().isInterrupted());
        }
    }

    record Timeout(long elapsedNanos,
            ExecutionStep<?> step) implements WaitCompletion {

        Timeout(ExecutionStep<?> step) {
            this(step.completedAfterNanos(), step);
        }
    }

    record PersistenceFailure(long acquiredAfterNanos,
            ExecutionStep<?> step) implements WaitCompletion {}
}

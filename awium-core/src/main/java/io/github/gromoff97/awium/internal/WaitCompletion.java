package io.github.gromoff97.awium.internal;

import java.util.function.LongConsumer;

import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.PERSISTENCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.Attempt.Operation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

sealed interface WaitCompletion {

    ExecutionStep<?> step();

    Reason failureReason();

    default long elapsedNanos() { return step().completedAfterNanos(); }

    default void onPersistenceFailure(LongConsumer action) { requireNonNull(action, "action must not be null"); }

    default Uncontrolled withConditionFailure(Throwable cause, String location) {
        return withFailure(CONDITION, cause, location);
    }

    default Uncontrolled withFailure(Operation operation, Throwable cause, String location) {
        return new Uncontrolled(step().withFailure(operation, cause, location));
    }

    record SatisfiedWait(ExecutionStep<?> step) implements WaitCompletion {
        @Override public Reason failureReason() { return null; }
    }

    record Uncontrolled(ExecutionStep<?> step,
            boolean interrupted) implements WaitCompletion {

        Uncontrolled(ExecutionStep<?> step) {
            this(step, currentThread().isInterrupted());
        }
        @Override public Reason failureReason() { return interrupted ? INTERRUPTED : step.failureReason(); }
        @Override public Uncontrolled withFailure(Operation operation, Throwable cause, String location) {
            return new Uncontrolled(step.withFailure(operation, cause, location), interrupted);
        }
    }

    record Timeout(long elapsedNanos,
            ExecutionStep<?> step) implements WaitCompletion {

        Timeout(ExecutionStep<?> step) {
            this(step.completedAfterNanos(), step);
        }
        @Override public Reason failureReason() { return TIMEOUT; }
    }

    record PersistenceFailure(long acquiredAfterNanos,
            ExecutionStep<?> step) implements WaitCompletion {
        @Override public Reason failureReason() { return PERSISTENCE_FAILED; }
        @Override public void onPersistenceFailure(LongConsumer action) {
            requireNonNull(action, "action must not be null").accept(acquiredAfterNanos);
        }
    }
}

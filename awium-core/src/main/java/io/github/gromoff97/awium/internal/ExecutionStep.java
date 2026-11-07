package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.Attempt;
import java.util.ArrayList;
import java.util.List;

import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.Attempt.Observation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.Attempt.Operation;

record ExecutionStep<OBSERVED>(Phase phase, long completedAfterNanos,
        Attempt<OBSERVED> reportedAttempt, boolean conditionSatisfied, List<Throwable> relatedCauses) {

    ExecutionStep { relatedCauses = List.copyOf(relatedCauses); }

    ExecutionStep(Phase phase, long completedAfterNanos, Attempt<OBSERVED> reportedAttempt, boolean conditionSatisfied) {
        this(phase, completedAfterNanos, reportedAttempt, conditionSatisfied, List.of());
    }

    Reason failureReason() {
        var operation = reportedAttempt.operation();
        if (operation == null) return null;
        return operation.failureReason();
    }

    ExecutionStep<OBSERVED> withConditionFailure(Throwable cause, String location) {
        return withFailure(CONDITION, cause, location);
    }

    ExecutionStep<OBSERVED> withFailure(Operation operation, Throwable cause, String location) {
        return new ExecutionStep<>(phase, completedAfterNanos,
                failure(operation, cause, location), conditionSatisfied, relatedCauses)
                .withRelatedCause(reportedAttempt.cause()).withRelatedCause(cause);
    }

    ExecutionStep<OBSERVED> withRelatedCause(Throwable cause) {
        if (cause == null || cause == reportedAttempt.cause()) return this;
        var failures = new ArrayList<>(relatedCauses);
        failures.add(cause);
        return new ExecutionStep<>(phase, completedAfterNanos, reportedAttempt, conditionSatisfied, failures);
    }

    private Failure<OBSERVED> failure(Operation operation, Throwable cause, String location) {
        return switch (reportedAttempt) {
            case Failure<OBSERVED> failure -> failure;
            case Observation<OBSERVED> observation -> new FailedAfterObservation<>(
                    observation.observed(), operation, cause, location, observation.diagnostic());
        };
    }

    enum Phase {
        ACQUISITION,
        PERSISTENCE
    }

}

package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.Attempt;
import java.util.ArrayList;
import java.util.List;

import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;

record ExecutionStep<OBSERVED>(Phase phase, long completedAfterNanos,
        Attempt<OBSERVED> reportedAttempt, boolean conditionSatisfied, List<Throwable> relatedCauses) {

    ExecutionStep { relatedCauses = List.copyOf(relatedCauses); }

    ExecutionStep(Phase phase, long completedAfterNanos, Attempt<OBSERVED> reportedAttempt, boolean conditionSatisfied) {
        this(phase, completedAfterNanos, reportedAttempt, conditionSatisfied, List.of());
    }

    ExecutionStep<OBSERVED> withConditionFailure(Throwable cause, String location) {
        return new ExecutionStep<>(phase, completedAfterNanos,
                conditionFailure(cause, location), conditionSatisfied, relatedCauses)
                .withRelatedCause(reportedAttempt.cause()).withRelatedCause(cause);
    }

    ExecutionStep<OBSERVED> withRelatedCause(Throwable cause) {
        if (cause == null || cause == reportedAttempt.cause()) return this;
        var failures = new ArrayList<>(relatedCauses);
        failures.add(cause);
        return new ExecutionStep<>(phase, completedAfterNanos, reportedAttempt, conditionSatisfied, failures);
    }

    private Failure<OBSERVED> conditionFailure(Throwable cause, String location) {
        return switch (reportedAttempt) {
            case SatisfiedObservation<OBSERVED> observation -> new FailedAfterObservation<>(
                    observation.observed(), CONDITION, cause, location, observation.diagnostic());
            case UnsatisfiedObservation<OBSERVED> observation -> new FailedAfterObservation<>(
                    observation.observed(), CONDITION, cause, location, observation.diagnostic());
            case Failure<OBSERVED> failure -> failure;
        };
    }

    enum Phase {
        ACQUISITION,
        PERSISTENCE
    }

}

package io.github.gromoff97.assertility;

import java.util.Objects;

final class ObservationEvaluator<S, R> {

    private final AwaitSources.Source<S> source;
    private final ConditionRuntime<S, R> condition;
    private final InterruptGuard interruptGuard;

    ObservationEvaluator(
            AwaitSources.Source<S> source,
            ConditionRuntime<S, R> condition,
            InterruptGuard interruptGuard) {
        this.source = Objects.requireNonNull(source);
        this.condition = Objects.requireNonNull(condition);
        this.interruptGuard = Objects.requireNonNull(interruptGuard);
    }

    @SuppressWarnings("removal")
    ObservationOutcome<R> evaluate(long attempt) {
        S actual;
        try {
            actual = source.get();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (InterruptedException interrupted) {
            return interruptGuard.fromThrown(
                    ObservationOutcome.Origin.SOURCE, interrupted, attempt);
        } catch (Throwable uncontrolled) {
            return ObservationOutcome.uncontrolled(
                    ObservationOutcome.Origin.SOURCE, uncontrolled, attempt);
        }

        ObservationOutcome<R> interrupted =
                interruptGuard.checkSource(attempt, actual);
        if (interrupted != null) {
            return interrupted;
        }

        Evaluation<R> evaluation;
        try {
            evaluation = condition.evaluate(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (InterruptedException conditionInterrupted) {
            return interruptGuard.fromThrown(ObservationOutcome.Origin.CONDITION,
                    conditionInterrupted, attempt, actual);
        } catch (Throwable uncontrolled) {
            return ObservationOutcome.uncontrolled(
                    ObservationOutcome.Origin.CONDITION,
                    uncontrolled, attempt, actual);
        }

        interrupted = interruptGuard.checkCondition(attempt, actual);
        if (interrupted != null) {
            return interrupted;
        }
        if (evaluation == null) {
            return ObservationOutcome.uncontrolled(
                    ObservationOutcome.Origin.CONDITION,
                    new NullPointerException("condition returned null Evaluation"),
                    attempt, actual);
        }
        return switch (evaluation.status()) {
            case SATISFIED -> ObservationOutcome.satisfied(
                    actual, evaluation.result(), attempt);
            case UNSATISFIED -> ObservationOutcome.unsatisfied(
                    actual, evaluation.mismatch(), evaluation.assertionCause(), attempt);
            case UNCONTROLLED -> ObservationOutcome.uncontrolled(
                    ObservationOutcome.Origin.CONDITION,
                    evaluation.uncontrolledCause(), attempt, actual);
        };
    }
}

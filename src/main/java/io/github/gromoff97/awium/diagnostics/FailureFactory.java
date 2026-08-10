package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.engine.Attempt;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.exceptions.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.exceptions.AwaitInterruptedException;
import io.github.gromoff97.awium.exceptions.AwaitSourceRetrievalException;
import io.github.gromoff97.awium.exceptions.AwaitStabilizationException;
import io.github.gromoff97.awium.exceptions.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException;
import io.github.gromoff97.awium.exceptions.AwaitUnhandledException;

import java.util.Objects;

public final class FailureFactory {

    private final FailureMessage failureMessage;

    public FailureFactory() {
        this(new FailureMessage());
    }

    public FailureFactory(FailureMessage failureMessage) {
        this.failureMessage = Objects.requireNonNull(failureMessage);
    }

    @SuppressWarnings("removal")
    public <R> R complete(WaitOutcome<R> outcome,
            RuntimeCondition<?, R> condition,
            WaitConfiguration configuration) {
        if (outcome.kind() == WaitOutcome.Kind.SUCCESS) {
            return outcome.result();
        }

        String message;
        try {
            message = failureMessage.format(outcome, condition, configuration);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (FailureMessage.FormattingFailure formattingFailure) {
            Throwable cause = formattingFailure.getCause();
            throw new AwaitUnhandledException(
                    failureMessage.emergency(formattingFailure), cause);
        } catch (Throwable formattingFailure) {
            throw new AwaitUnhandledException(failureMessage.emergency(
                    outcome, condition, formattingFailure), formattingFailure);
        }

        Throwable cause = terminalCause(outcome);
        switch (outcome.kind()) {
            case TIMEOUT_BETWEEN_OBSERVATIONS, LATE_UNSATISFIED_TIMEOUT,
                    LATE_SATISFIED_TIMEOUT -> throw new AwaitTimeoutException(
                            message, cause);
            case STABILITY_LOSS -> throw new AwaitStabilizationException(
                            message, cause);
            case UNCONTROLLED -> throw uncontrolled(outcome.attempt(), message);
            case SUCCESS -> throw new AssertionError("unreachable");
        }
        throw new AssertionError("unreachable");
    }

    private static Throwable terminalCause(WaitOutcome<?> outcome) {
        return switch (outcome.kind()) {
            case TIMEOUT_BETWEEN_OBSERVATIONS, LATE_UNSATISFIED_TIMEOUT,
                    STABILITY_LOSS -> outcome.attempt().assertionCause();
            case UNCONTROLLED -> outcome.attempt().cause();
            case SUCCESS, LATE_SATISFIED_TIMEOUT -> null;
        };
    }

    private static AwaitUncontrolledException uncontrolled(
            Attempt<?> attempt, String message) {
        Throwable cause = attempt.cause();
        if (cause instanceof InterruptedException) {
            return new AwaitInterruptedException(message, cause);
        }
        return switch (attempt.origin()) {
            case SOURCE -> new AwaitSourceRetrievalException(message, cause);
            case CONDITION -> new AwaitConditionEvaluationException(message, cause);
            case WAITING -> new AwaitUnhandledException(message, cause);
        };
    }
}

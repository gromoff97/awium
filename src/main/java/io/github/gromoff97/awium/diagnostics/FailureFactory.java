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

import static java.util.Objects.requireNonNull;

public final class FailureFactory {

    private final FailureMessage failureMessage;

    public FailureFactory() {
        this(new FailureMessage());
    }

    public FailureFactory(FailureMessage failureMessage) {
        this.failureMessage = requireNonNull(failureMessage);
    }

    @SuppressWarnings("removal")
    public <R> R complete(WaitOutcome<R> outcome,
            RuntimeCondition<?, R> condition,
            WaitConfiguration configuration) {
        if (outcome instanceof WaitOutcome.Success<R> success) {
            return success.attempt().result();
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
        }

        Throwable cause = FailureMessage.terminalCause(outcome);
        switch (outcome) {
            case WaitOutcome.TimeoutBetweenObservations<R> ignored ->
                    throw new AwaitTimeoutException(message, cause);
            case WaitOutcome.LateUnsatisfiedTimeout<R> ignored ->
                    throw new AwaitTimeoutException(message, cause);
            case WaitOutcome.LateSatisfiedTimeout<R> ignored ->
                    throw new AwaitTimeoutException(message, cause);
            case WaitOutcome.StabilityLoss<R> ignored ->
                    throw new AwaitStabilizationException(message, cause);
            case WaitOutcome.Uncontrolled<R> uncontrolled ->
                    throw uncontrolled(uncontrolled.attempt(), message);
            case WaitOutcome.Success<R> ignored ->
                    throw new AssertionError("unreachable");
        }
    }

    private static AwaitUncontrolledException uncontrolled(
            Attempt.Uncontrolled<?> attempt, String message) {
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

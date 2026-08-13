package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.exceptions.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.exceptions.AwaitInterruptedException;
import io.github.gromoff97.awium.exceptions.AwaitSourceRetrievalException;
import io.github.gromoff97.awium.exceptions.AwaitStabilizationException;
import io.github.gromoff97.awium.exceptions.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUnhandledException;

import static io.github.gromoff97.awium.diagnostics.FailureMessage.terminalCause;
import static io.github.gromoff97.awium.engine.Attempt.Satisfied;
import static io.github.gromoff97.awium.engine.Attempt.Uncontrolled;
import static io.github.gromoff97.awium.engine.WaitOutcome.StabilityLoss;
import static java.lang.Thread.currentThread;

public final class FailureFactory {

    @SuppressWarnings("removal")
    public <R> R complete(WaitOutcome<R> outcome,
            RuntimeCondition<?, R> condition,
            WaitConfiguration configuration) {
        if (outcome instanceof Satisfied<R> success) {
            return success.result();
        }

        Throwable cause = terminalCause(outcome);
        if (cause instanceof VirtualMachineError fatal) {
            throw fatal;
        }
        if (cause instanceof ThreadDeath fatal) {
            throw fatal;
        }
        boolean restoreInterrupt = currentThread().isInterrupted()
                || cause instanceof InterruptedException;
        String message;
        try {
            message = FailureMessage.format(outcome, condition, configuration);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            suppress(fatal, cause);
            throw fatal;
        } catch (FailureMessage.FormattingFailure formattingFailure) {
            Throwable formattingCause = formattingFailure.getCause();
            Throwable engineCause = formattingFailure.engineCause();
            String emergencyMessage;
            try {
                emergencyMessage = FailureMessage.emergency(formattingFailure);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                suppress(fatal, formattingCause);
                if (engineCause != formattingCause) {
                    suppress(fatal, engineCause);
                }
                throw fatal;
            }
            var failure = new AwaitUnhandledException(
                    emergencyMessage, formattingCause);
            if (engineCause != null && engineCause != formattingCause) {
                suppress(failure, engineCause);
            }
            throw failure;
        } finally {
            if (restoreInterrupt) {
                currentThread().interrupt();
            }
        }

        if (outcome instanceof StabilityLoss<R>) {
            throw new AwaitStabilizationException(message, cause);
        }
        if (outcome instanceof Uncontrolled<R> uncontrolled) {
            if (cause instanceof InterruptedException) {
                throw new AwaitInterruptedException(message, cause);
            }
            throw switch (uncontrolled.origin()) {
                case SOURCE -> new AwaitSourceRetrievalException(message, cause);
                case CONDITION -> new AwaitConditionEvaluationException(
                        message, cause);
                case WAITING -> new AwaitUnhandledException(message, cause);
            };
        }
        throw new AwaitTimeoutException(message, cause);
    }

    private static void suppress(Throwable failure, Throwable cause) {
        if (cause != null && cause != failure) {
            failure.addSuppressed(cause);
        }
    }
}

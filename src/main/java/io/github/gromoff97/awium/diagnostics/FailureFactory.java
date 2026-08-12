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

import static io.github.gromoff97.awium.engine.Attempt.Satisfied;
import static io.github.gromoff97.awium.engine.Attempt.Uncontrolled;
import static io.github.gromoff97.awium.engine.WaitOutcome.StabilityLoss;
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
        if (outcome instanceof Satisfied<R> success) {
            return success.result();
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
}

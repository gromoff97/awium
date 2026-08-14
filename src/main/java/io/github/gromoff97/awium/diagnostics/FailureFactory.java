package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.exceptions.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.exceptions.AwaitInterruptedException;
import io.github.gromoff97.awium.exceptions.AwaitSourceRetrievalException;
import io.github.gromoff97.awium.exceptions.AwaitStabilizationException;
import io.github.gromoff97.awium.exceptions.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUnhandledException;

import java.util.function.Supplier;

import static io.github.gromoff97.awium.diagnostics.FailureMessage.render;
import static io.github.gromoff97.awium.diagnostics.FailureMessage.suppress;
import static io.github.gromoff97.awium.diagnostics.FailureMessage.terminalCause;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Satisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled;
import static io.github.gromoff97.awium.engine.WaitOutcome.StabilityLoss;
import static java.lang.Thread.currentThread;

public final class FailureFactory {

    private FailureFactory() {
        throw new AssertionError("Utility class");
    }

    @SuppressWarnings("removal")
    public static <R> R complete(WaitOutcome<R> outcome, Supplier<String> description, String explanation,
            WaitConfiguration configuration) {
        if (outcome instanceof Satisfied<R> success) {
            return success.result();
        }

        Throwable cause = terminalCause(outcome);
        if (cause instanceof Error fatal && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
            throw fatal;
        }
        boolean restoreInterrupt = currentThread().isInterrupted()
                || cause instanceof InterruptedException;
        FailureMessage.RenderResult rendered;
        try {
            rendered = render(outcome, description, explanation, configuration);
        } finally {
            if (restoreInterrupt) {
                currentThread().interrupt();
            }
        }

        Throwable renderingFailure = rendered.failure();
        if (renderingFailure != null) {
            var failure = new AwaitUnhandledException(rendered.message(), renderingFailure);
            if (cause != renderingFailure) {
                suppress(failure, cause);
            }
            throw failure;
        }

        String message = rendered.message();
        if (outcome instanceof StabilityLoss<R>) {
            throw new AwaitStabilizationException(message, cause);
        }
        if (outcome instanceof Uncontrolled<R> uncontrolled) {
            if (cause instanceof InterruptedException) {
                throw new AwaitInterruptedException(message, cause);
            }
            throw switch (uncontrolled.origin()) {
                case SOURCE -> new AwaitSourceRetrievalException(message, cause);
                case CONDITION -> new AwaitConditionEvaluationException(message, cause);
                case WAITING -> new AwaitUnhandledException(message, cause);
            };
        }
        throw new AwaitTimeoutException(message, cause);
    }

}

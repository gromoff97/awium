package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitStabilizationException;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitInterruptedException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitSourceRetrievalException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitUnhandledException;

import java.util.function.Supplier;

import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Satisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Unsatisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.StabilityLoss;
import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
public final class FailureFactory {

    private FailureFactory() {
        throw new AssertionError("Utility class");
    }

    public static <R> R complete(WaitOutcome<R> outcome, Supplier<String> description,
            String explanation, WaitConfiguration configuration) {
        if (outcome instanceof Satisfied<R> success) {
            return success.result();
        }

        Throwable cause = terminalCause(outcome);
        if (cause instanceof Error fatal
                && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
            throw fatal;
        }
        boolean restoreInterrupt = currentThread().isInterrupted()
                || cause instanceof InterruptedException;
        FailureMessageRenderer.Result rendered;
        try {
            rendered = FailureMessageRenderer.render(
                    outcome, description, explanation, configuration, cause);
        } finally {
            if (restoreInterrupt) {
                currentThread().interrupt();
            }
        }

        Throwable renderingFailure = rendered.failure();
        if (renderingFailure != null) {
            var failure = new AwaitUnhandledException(rendered.message(), renderingFailure);
            if (cause != renderingFailure) {
                addSuppressed(failure, cause);
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

    private static Throwable terminalCause(WaitOutcome<?> outcome) {
        return switch (outcome.attempt()) {
            case Satisfied<?> ignored -> null;
            case Unsatisfied<?> attempt -> attempt.assertionCause();
            case Uncontrolled<?> attempt -> attempt.cause();
        };
    }

    private static void addSuppressed(Throwable failure, Throwable cause) {
        if (cause != null && cause != failure) {
            failure.addSuppressed(cause);
        }
    }
}

package io.github.gromoff97.awium.internal.diagnostic;

import io.github.gromoff97.awium.exceptions.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.exceptions.AwaitInterruptedException;
import io.github.gromoff97.awium.exceptions.AwaitSourceRetrievalException;
import io.github.gromoff97.awium.exceptions.AwaitStabilizationException;
import io.github.gromoff97.awium.exceptions.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException;
import io.github.gromoff97.awium.exceptions.AwaitUnhandledException;
import io.github.gromoff97.awium.internal.engine.AttemptResult;
import io.github.gromoff97.awium.internal.engine.WaitConfiguration;
import io.github.gromoff97.awium.internal.engine.WaitResult;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class FailureFactory {

    private final Function<FailureContext<?>, String> formatter;

    public FailureFactory() {
        this(new Diagnostics());
    }

    public FailureFactory(Function<FailureContext<?>, String> formatter) {
        this.formatter = Objects.requireNonNull(formatter);
    }

    @SuppressWarnings("removal")
    public <R> R complete(WaitResult<R> outcome, Supplier<String> description,
            String explanation, WaitConfiguration config) {
        if (outcome.kind() == WaitResult.Kind.SUCCESS) {
            return outcome.result();
        }

        FailureContext<R> context = new FailureContext<>(outcome, description,
                explanation, config);
        String message;
        try {
            message = Objects.requireNonNull(formatter.apply(context),
                    "diagnostic formatter returned null");
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable formattingFailure) {
            throw new AwaitUnhandledException(
                    emergencyMessage(context, formattingFailure), formattingFailure);
        }

        Throwable cause = context.terminalCause();
        switch (outcome.kind()) {
            case TIMEOUT_BETWEEN_OBSERVATIONS, LATE_UNSATISFIED_TIMEOUT,
                    LATE_SATISFIED_TIMEOUT -> throw new AwaitTimeoutException(
                            message, cause);
            case STABILITY_LOSS -> throw new AwaitStabilizationException(
                    message, cause);
            case UNCONTROLLED -> throw uncontrolled(
                    outcome.observation(), message);
            case SUCCESS -> throw new AssertionError("unreachable");
        }
        throw new AssertionError("unreachable");
    }

    private static AwaitUncontrolledException uncontrolled(
            AttemptResult<?> observation, String message) {
        Throwable cause = observation.cause();
        if (cause instanceof InterruptedException) {
            return new AwaitInterruptedException(message, cause);
        }
        return switch (observation.origin()) {
            case SOURCE -> new AwaitSourceRetrievalException(message, cause);
            case CONDITION -> new AwaitConditionEvaluationException(message, cause);
            case WAITING -> new AwaitUnhandledException(message, cause);
        };
    }

    private static String emergencyMessage(FailureContext<?> context,
            Throwable failure) {
        StringBuilder out = new StringBuilder("Await execution was unhandled\n\n");
        Diagnostics.field(out, 0, "Attempt", Long.toString(context.attempt()));
        Diagnostics.field(out, 0, "Condition",
                context.materializedConditionDescription());
        if (context.hasActual()) {
            Diagnostics.field(out, 0, "Actual",
                    context.materializedActualValue());
        }
        if (context.explanation() != null) {
            Diagnostics.field(out, 0, "Because", context.explanation());
        }
        Diagnostics.field(out, 0, "Cause", ValueRenderer.typeName(failure));
        out.setLength(out.length() - 1);
        return out.toString();
    }
}

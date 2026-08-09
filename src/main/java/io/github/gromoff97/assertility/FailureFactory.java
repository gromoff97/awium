package io.github.gromoff97.assertility;

import java.util.Objects;

final class FailureFactory {

    private final DiagnosticFormatter formatter;

    FailureFactory() {
        this(new Diagnostics());
    }

    FailureFactory(DiagnosticFormatter formatter) {
        this.formatter = Objects.requireNonNull(formatter);
    }

    @SuppressWarnings("removal")
    <R> R complete(WaitOutcome<R> outcome, ConditionRuntime<?, R> runtime,
            WaitConfig config) {
        if (outcome.kind() == WaitOutcome.Kind.SUCCESS) {
            return outcome.result();
        }

        FailureContext<R> context = new FailureContext<>(outcome, runtime, config);
        String message;
        try {
            message = Objects.requireNonNull(formatter.format(context),
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
            ObservationOutcome<?> observation, String message) {
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

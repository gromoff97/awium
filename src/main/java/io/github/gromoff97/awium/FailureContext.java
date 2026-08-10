package io.github.gromoff97.awium;

import java.util.Objects;

final class FailureContext<R> {

    static final String DESCRIPTION_UNAVAILABLE =
            "condition description unavailable";
    static final String ACTUAL_DIAGNOSTICS_FAILED =
            "<value unavailable: diagnostics failed>";

    private final WaitOutcome<R> outcome;
    private final ConditionRuntime<?, R> runtime;
    private final WaitConfig config;

    private boolean descriptionMaterialized;
    private String description;
    private boolean actualMaterialized;
    private String actual;
    private boolean assertionMaterialized;
    private AssertionDiagnostic assertion;
    private boolean causeMaterialized;
    private String cause;

    FailureContext(WaitOutcome<R> outcome, ConditionRuntime<?, R> runtime,
            WaitConfig config) {
        this.outcome = Objects.requireNonNull(outcome);
        this.runtime = Objects.requireNonNull(runtime);
        this.config = Objects.requireNonNull(config);
    }

    WaitOutcome<R> outcome() {
        return outcome;
    }

    WaitConfig config() {
        return config;
    }

    String explanation() {
        return runtime.explanation();
    }

    @SuppressWarnings("removal")
    String conditionDescription() {
        if (!descriptionMaterialized) {
            descriptionMaterialized = true;
            try {
                String rendered = runtime.description().get();
                description = rendered == null || rendered.isBlank()
                        ? DESCRIPTION_UNAVAILABLE : rendered;
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                description = DESCRIPTION_UNAVAILABLE;
            }
        }
        return description;
    }

    String materializedConditionDescription() {
        return descriptionMaterialized ? description : DESCRIPTION_UNAVAILABLE;
    }

    boolean hasActual() {
        return outcome.observation() != null && outcome.observation().hasActual();
    }

    String actualValue() {
        if (!actualMaterialized) {
            actualMaterialized = true;
            actual = ValueRenderer.render(outcome.observation().actual());
        }
        return actual;
    }

    String materializedActualValue() {
        return actualMaterialized ? actual : ACTUAL_DIAGNOSTICS_FAILED;
    }

    long attempt() {
        return outcome.completedAttempts();
    }

    Throwable terminalCause() {
        return switch (outcome.kind()) {
            case TIMEOUT_BETWEEN_OBSERVATIONS ->
                    outcome.lastObservation().assertionCause();
            case LATE_UNSATISFIED_TIMEOUT, STABILITY_LOSS ->
                    outcome.observation().assertionCause();
            case UNCONTROLLED -> outcome.observation().cause();
            case SUCCESS, LATE_SATISFIED_TIMEOUT -> null;
        };
    }

    @SuppressWarnings("removal")
    AssertionDiagnostic assertionDiagnostic(String fallbackMismatch) {
        if (!assertionMaterialized) {
            assertionMaterialized = true;
            AssertionError assertionCause = (AssertionError) terminalCause();
            String type = ValueRenderer.typeName(assertionCause);
            try {
                String message = assertionCause.getMessage();
                assertion = message == null || message.isBlank()
                        ? new AssertionDiagnostic(fallbackMismatch, type)
                        : new AssertionDiagnostic(message, type + ": " + message);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                assertion = new AssertionDiagnostic(fallbackMismatch,
                        type + ": <message unavailable: getMessage() threw "
                                + ValueRenderer.typeName(failure) + ">");
            }
        }
        return assertion;
    }

    @SuppressWarnings("removal")
    String causeDiagnostic() {
        if (!causeMaterialized) {
            causeMaterialized = true;
            Throwable terminalCause = terminalCause();
            String type = ValueRenderer.typeName(terminalCause);
            try {
                String message = terminalCause.getMessage();
                cause = message == null || message.isBlank()
                        ? type : type + ": " + message;
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                cause = type + ": <message unavailable: getMessage() threw "
                        + ValueRenderer.typeName(failure) + ">";
            }
        }
        return cause;
    }

    record AssertionDiagnostic(String mismatch, String cause) {
    }
}

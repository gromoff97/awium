package io.github.gromoff97.awium.internal.diagnostic;

import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;

import java.util.Objects;
import java.util.function.Supplier;

public final class FailureContext<R> {

    static final String DESCRIPTION_UNAVAILABLE =
            "condition description unavailable";
    static final String ACTUAL_DIAGNOSTICS_FAILED =
            "<value unavailable: diagnostics failed>";

    private final WaitOutcome<R> outcome;
    private final Supplier<String> descriptionSupplier;
    private final String explanation;
    private final WaitConfiguration config;

    private boolean descriptionMaterialized;
    private String description;
    private boolean actualMaterialized;
    private String actual;
    private boolean assertionMaterialized;
    private AssertionDiagnostic assertion;
    private boolean causeMaterialized;
    private String cause;

    public FailureContext(WaitOutcome<R> outcome,
            Supplier<String> descriptionSupplier, String explanation,
            WaitConfiguration config) {
        this.outcome = Objects.requireNonNull(outcome);
        this.descriptionSupplier = Objects.requireNonNull(descriptionSupplier);
        this.explanation = explanation;
        this.config = Objects.requireNonNull(config);
    }

    public WaitOutcome<R> outcome() {
        return outcome;
    }

    public WaitConfiguration config() {
        return config;
    }

    public String explanation() {
        return explanation;
    }

    @SuppressWarnings("removal")
    public String conditionDescription() {
        if (!descriptionMaterialized) {
            descriptionMaterialized = true;
            try {
                String rendered = descriptionSupplier.get();
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

    public String materializedConditionDescription() {
        return descriptionMaterialized ? description : DESCRIPTION_UNAVAILABLE;
    }

    public boolean hasActual() {
        return outcome.attempt().hasActual();
    }

    public String actualValue() {
        if (!actualMaterialized) {
            actualMaterialized = true;
            actual = ValueRenderer.render(outcome.attempt().actual());
        }
        return actual;
    }

    public String materializedActualValue() {
        return actualMaterialized ? actual : ACTUAL_DIAGNOSTICS_FAILED;
    }

    public long attempt() {
        return outcome.completedAttempts();
    }

    public Throwable terminalCause() {
        return switch (outcome.kind()) {
            case TIMEOUT_BETWEEN_OBSERVATIONS ->
                    outcome.attempt().assertionCause();
            case LATE_UNSATISFIED_TIMEOUT, STABILITY_LOSS ->
                    outcome.attempt().assertionCause();
            case UNCONTROLLED -> outcome.attempt().cause();
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

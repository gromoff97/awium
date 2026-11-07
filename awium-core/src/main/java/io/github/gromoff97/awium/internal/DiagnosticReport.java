package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.ConditionHistory;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.internal.DiagnosticText.renderCause;
import static io.github.gromoff97.awium.internal.DiagnosticText.renderSafely;
import static io.github.gromoff97.awium.internal.DiagnosticText.renderValue;
import static io.github.gromoff97.awium.internal.DiagnosticText.unsignedDuration;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.Attempt.Observation;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;

public final class DiagnosticReport {

    static AwaitFailure failure(Condition<?, ?> condition, Function<Attempt<?>, Diagnostic> diagnostics,
            WaitCompletion outcome, WaitConfiguration configuration, long attemptNumber, List<? extends ConditionHistory<?>> histories) {
        var reason = outcome.failureReason();
        if (reason == null) throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        var step = outcome.step();
        var attempt = step.reportedAttempt();
        var cause = attempt.cause();
        var text = new DiagnosticReport(cause);
        step.relatedCauses().forEach(text::retain);
        text.line(reason.title());
        text.field("", "Attempt", attemptNumber + " (" + step.phase() + ")");
        if (histories.stream().noneMatch(history -> history.condition() == condition)) {
            text.line("");
            text.condition("Condition", condition, "");
        }
        text.render(() -> {
            text.diagnostic(diagnostics.apply(attempt));
            return "";
        });
        text.timing(outcome, configuration);
        text.histories(histories);
        if (histories.stream().noneMatch(history -> history.attempts().get(attemptNumber) == attempt)) {
            text.line("");
            text.appendAttempt("Last attempt #" + attemptNumber, attempt);
        }
        return new AwaitFailure(reason, text.finish(""), cause, text.secondaryCauses);
    }

    private void timing(WaitCompletion outcome, WaitConfiguration configuration) {
        var completedAfter = outcome.step().completedAfterNanos();
        var elapsed = outcome.elapsedNanos();
        line("");
        field("", "Timing", "elapsed " + unsignedDuration(elapsed)
                + "; timeout " + unsignedDuration(configuration.upToNanos())
                + "; interval " + unsignedDuration(configuration.everyNanos()));
        if (elapsed != completedAfter) field("", "Last completed after", unsignedDuration(completedAfter));
        outcome.onPersistenceFailure(acquiredAfter -> {
            field("", "Persistence", "failed after " + unsignedDuration(completedAfter - acquiredAfter)
                    + "; required " + unsignedDuration(configuration.persistenceNanos())
                    + "; acquired after " + unsignedDuration(acquiredAfter));
        });
    }

    private final StringBuilder out = new StringBuilder();
    private final Throwable primaryCause;
    private final List<Throwable> secondaryCauses = new ArrayList<>();

    private DiagnosticReport(Throwable primaryCause) {
        this.primaryCause = primaryCause;
    }

    private void line(String value) {
        out.append(value).append('\n');
    }

    private String finish(String indentation) {
        for (Throwable failure : secondaryCauses) field(indentation, "Suppressed", failure.getClass().getName());
        return out.substring(0, out.length() - 1);
    }

    public static String success(Object result, List<? extends ConditionHistory<?>> histories) {
        var text = new DiagnosticReport(null);
        text.line("Wait satisfied");
        text.value("Result", result);
        text.histories(histories);
        return text.finish("    ");
    }

    public static String attempt(Attempt<?> attempt) {
        var text = new DiagnosticReport(attempt.cause());
        text.appendAttempt("Result", attempt);
        return text.finish("    ");
    }

    public static String conditionHistory(ConditionHistory<?> history) {
        var text = new DiagnosticReport(null);
        text.appendHistory("Condition", history);
        return text.finish("    ");
    }

    private void histories(List<? extends ConditionHistory<?>> histories) {
        for (int i = 0; i < histories.size(); i++) {
            out.append('\n');
            appendHistory("Condition " + (i + 1), histories.get(i));
        }
    }

    private void appendHistory(String label, ConditionHistory<?> history) {
        condition(label, history.condition(), "");
        if (history.attempts().isEmpty()) field("Result", "not attempted");
        history.attempts().forEach((through, attempt) -> {
            out.append('\n');
            appendAttempt("Through #" + through, attempt);
        });
    }

    private void appendAttempt(String label, Attempt<?> attempt) {
        var cause = attempt.cause();
        switch (attempt) {
            case SatisfiedObservation<?> ignored -> field("", label, "satisfied");
            case UnsatisfiedObservation<?> ignored -> field("", label, "unsatisfied");
            case Failure<?> failure -> {
                field("", label, "failed (" + failure.operation() + ")");
                if (failure.location() != null) field("Within", failure.location());
            }
        }
        diagnostic(attempt.diagnostic());
        if (attempt instanceof Observation<?> observation) {
            value("Observed now", observation.observed());
        } else {
            field("Observed", "unavailable");
        }
        if (cause != null) field("Cause", renderCause(cause, this::retain));
    }

    private String render(Supplier<String> rendering) { return renderSafely(rendering, this::retain); }

    private void retain(Throwable failure) {
        if (failure != primaryCause && secondaryCauses.stream().noneMatch(previous -> previous == failure)) {
            secondaryCauses.add(failure);
        }
    }

    private void diagnostic(Diagnostic diagnostic) {
        if (diagnostic.mismatch() != null) field("Mismatch", diagnostic.mismatch());
        for (var detail : diagnostic.fields()) {
            if (detail.value() instanceof Condition<?, ?> condition) {
                condition(detail.label(), condition, "    ");
            } else {
                value(detail.label(), detail.value());
            }
        }
    }

    private void value(String label, Object value) { field(label, renderValue(value, this::retain)); }

    private void condition(String label, Condition<?, ?> condition, String indentation) {
        field(indentation, label, render(condition::description));
        String detailsIndentation = indentation + "    ";
        render(() -> {
            for (var reference : condition.references()) {
                field(detailsIndentation, reference.label(), renderValue(reference.value(), this::retain));
            }
            return "";
        });
        String explanation = render(condition::explanation);
        if (explanation != null) field(detailsIndentation, "Because", explanation);
    }

    private void field(String label, String value) {
        field("    ", label, value);
    }

    private void field(String indentation, String label, String value) {
        String normalized = String.valueOf(value).replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.contains("\n")) {
            out.append(indentation).append(label).append(": ")
                    .append(normalized).append('\n');
            return;
        }
        out.append(indentation).append(label).append(":\n");
        for (String line : normalized.split("\n", -1)) {
            if (!line.isEmpty()) {
                out.append(indentation).append("    ").append(line);
            }
            out.append('\n');
        }
    }
}

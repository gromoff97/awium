package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.HistoryEntry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.lang.Thread.currentThread;
import static java.util.Arrays.deepToString;

/** Formats diagnostic values on demand, retaining failures without replacing the original outcome. */
public final class DiagnosticText {

    private static final List<TimeUnit> DURATION_UNITS = List.of(
            TimeUnit.DAYS, TimeUnit.HOURS, TimeUnit.MINUTES, TimeUnit.SECONDS,
            TimeUnit.MILLISECONDS, TimeUnit.MICROSECONDS, TimeUnit.NANOSECONDS);

    private final Throwable primaryCause;
    private final List<Throwable> diagnosticFailures = new ArrayList<>();

    public DiagnosticText(Throwable primaryCause) {
        this.primaryCause = primaryCause;
    }

    public List<Throwable> failures() {
        return List.copyOf(diagnosticFailures);
    }

    /** Formats a cause without invoking an overridden Throwable.toString(). */
    public static String cause(Throwable cause) {
        if (cause == null) return "null";
        String message = new DiagnosticText(cause).render(cause::getMessage);
        return cause.getClass().getName() + (message == null ? "" : ": " + message);
    }

    /** Formats retained values on demand; a null last boundary denotes one invocation. */
    public static String history(String heading, HistoryEntry<?> first, HistoryEntry<?> last) {
        boolean repeated = last != null;
        var status = first.result();
        var cause = status.cause();
        var text = new DiagnosticText(cause);
        var out = new StringBuilder(heading);
        out.append(" (").append(first.phase()).append(")\n");
        String completed = duration(first.timing().completionOffset());
        if (repeated) completed += " → " + duration(last.timing().completionOffset());
        field(out, "Completed after", completed);
        var progress = first.progress();
        if (progress != null) {
            int total = progress.sequence().stages().size();
            field(out, "Stage", progress.stageNumber() + " of " + total
                    + " (captured " + progress.capturedStages() + ")");
            if (progress.sequence().explanation() != null) {
                field(out, "Sequence because", progress.sequence().explanation());
            }
        }
        text.condition(out, first.condition(), "    ");
        String result = switch (status) {
            case AttemptStatus.Satisfied ignored -> "satisfied";
            case AttemptStatus.Unsatisfied ignored -> "unsatisfied";
            case AttemptStatus.Uncontrolled failure -> "uncontrolled (" + failure.operation() + ")";
        };
        field(out, "Result", result);
        if (status instanceof AttemptStatus.Unsatisfied mismatch && mismatch.mismatch() != null) {
            field(out, "Mismatch", mismatch.mismatch());
        }
        if (status instanceof AttemptStatus.Uncontrolled failure && failure.location() != null) {
            field(out, "Within", failure.location());
        }
        boolean separateObservations = repeated && first.observed() != last.observed();
        if (first.hasObservation()) {
            field(out, separateObservations ? "First observed (current value)" : "Observed (current value)",
                    text.value(first.observed()));
        }
        if (separateObservations && last.hasObservation()) {
            field(out, "Last observed (current value)", text.value(last.observed()));
        }
        if (cause != null) {
            field(out, "Cause", cause.getClass().getName());
            String message = text.render(cause::getMessage);
            if (message != null && !message.isBlank()) field(out, "Message", message);
        }
        for (Throwable failure : text.failures()) field(out, "Diagnostics failure", failure.getClass().getName());
        return out.substring(0, out.length() - 1);
    }

    public static String duration(Duration remaining) {
        StringJoiner result = new StringJoiner(" ").setEmptyValue("0 nanoseconds");
        for (TimeUnit unit : DURATION_UNITS) {
            var unitDuration = Duration.ofNanos(unit.toNanos(1));
            long count = remaining.dividedBy(unitDuration);
            remaining = remaining.minus(unitDuration.multipliedBy(count));
            if (count != 0) {
                String name = unit.name().toLowerCase(Locale.ROOT);
                result.add(count + " " + (count == 1 ? name.substring(0, name.length() - 1) : name));
            }
        }
        return result.toString();
    }

    public String render(Supplier<String> rendering) {
        boolean interrupted = currentThread().isInterrupted();
        try {
            return rendering.get();
        } catch (Throwable failure) {
            if (failure != primaryCause && diagnosticFailures.stream().noneMatch(previous -> previous == failure)) {
                diagnosticFailures.add(failure);
            }
            return "<value unavailable: diagnostics failed>";
        } finally {
            if (interrupted) currentThread().interrupt();
        }
    }

    public String value(Object value) {
        return render(() -> {
            String rendered = deepToString(new Object[] {value});
            return rendered.substring(1, rendered.length() - 1);
        });
    }

    public void condition(StringBuilder out, Condition condition, String indentation) {
        field(out, indentation, "Condition", condition.description());
        String detailsIndentation = indentation + "    ";
        var reference = condition.reference();
        if (reference != null) {
            field(out, detailsIndentation, reference.label(), value(reference.value()));
        }
        if (condition.explanation() != null) {
            field(out, detailsIndentation, "Because", condition.explanation());
        }
    }

    public static void field(StringBuilder out, String label, String value) {
        field(out, "    ", label, value);
    }

    public static void field(StringBuilder out, String indentation,
            String label, String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
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

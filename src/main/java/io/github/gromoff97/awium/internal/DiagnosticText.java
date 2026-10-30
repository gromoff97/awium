package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionHistory;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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

    public void appendFailures(StringBuilder out, String indentation) {
        for (Throwable failure : diagnosticFailures) field(out, indentation, "Rendering failure", failure.getClass().getName());
    }

    /** Formats a cause without invoking an overridden Throwable.toString(). */
    public String cause(Throwable cause) {
        if (cause == null) return "null";
        String message = render(cause::getMessage);
        return cause.getClass().getName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    /** Formats a retained state without its condition or attempt number. */
    public static String attempt(Attempt<?> attempt) {
        var out = new StringBuilder();
        appendAttempt(out, "Result", attempt);
        return out.substring(0, out.length() - 1);
    }

    /** Formats one condition occurrence followed by its observation changes. */
    public static String conditionHistory(String label, ConditionHistory<?> history) {
        var out = new StringBuilder();
        var text = new DiagnosticText(null);
        text.condition(out, label, history.condition(), "");
        text.appendFailures(out, "    ");
        if (history.attempts().isEmpty()) field(out, "Result", "not attempted");
        history.attempts().forEach((through, attempt) -> {
            out.append('\n');
            appendAttempt(out, "Through #" + through, attempt);
        });
        return out.substring(0, out.length() - 1);
    }

    private static void appendAttempt(StringBuilder out, String label, Attempt<?> attempt) {
        var cause = attempt.cause();
        var text = new DiagnosticText(cause);
        switch (attempt) {
            case Attempt.Satisfied<?> ignored -> field(out, "", label, "satisfied");
            case Attempt.Unsatisfied<?> mismatch -> {
                field(out, "", label, "unsatisfied");
                if (mismatch.mismatch() != null) field(out, "Mismatch", mismatch.mismatch());
            }
            case Attempt.Failure<?> failure -> {
                field(out, "", label, "failed (" + failure.operation() + ")");
                if (failure.location() != null) field(out, "Within", failure.location());
            }
        }
        String observation = text.observation(attempt);
        if (observation != null) {
            field(out, "Observed now", observation);
        } else {
            field(out, "Observed", "unavailable");
        }
        if (cause != null) field(out, "Cause", text.cause(cause));
        text.appendFailures(out, "    ");
    }

    /** Formats unsigned elapsed nanoseconds, including acquisition plus persistence beyond Long.MAX_VALUE. */
    public static String duration(long remaining) {
        StringJoiner result = new StringJoiner(" ").setEmptyValue("0 ns");
        for (TimeUnit unit : DURATION_UNITS) {
            long unitNanos = unit.toNanos(1);
            long count = Long.divideUnsigned(remaining, unitNanos);
            remaining = Long.remainderUnsigned(remaining, unitNanos);
            if (count != 0) {
                String suffix = switch (unit) {
                    case DAYS -> "d";
                    case HOURS -> "h";
                    case MINUTES -> "min";
                    case SECONDS -> "s";
                    case MILLISECONDS -> "ms";
                    case MICROSECONDS -> "µs";
                    case NANOSECONDS -> "ns";
                };
                result.add(count + " " + suffix);
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
            return "<unprintable>";
        } finally {
            if (interrupted) currentThread().interrupt();
        }
    }

    /** Formats the returned value, or returns null when execution obtained no observation. */
    public String observation(Attempt<?> attempt) {
        return switch (attempt) {
            case Attempt.Satisfied<?> evaluated -> value(evaluated.observed());
            case Attempt.Unsatisfied<?> evaluated -> value(evaluated.observed());
            case Attempt.FailedAfterObservation<?> failed -> value(failed.observed());
            case Attempt.FailedBeforeObservation<?> ignored -> null;
        };
    }

    public String value(Object value) {
        return render(() -> {
            if (value instanceof CharSequence) return quoted(value.toString(), '"');
            if (value instanceof Character) return quoted(value.toString(), '\'');
            String rendered = deepToString(new Object[] {value});
            return rendered.substring(1, rendered.length() - 1);
        });
    }

    private static String quoted(String value, char quote) {
        var out = new StringBuilder().append(quote);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == quote || character == '\\') out.append('\\');
            switch (character) {
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (Character.isISOControl(character)) out.append("\\u").append(HexFormat.of().toHexDigits(character));
                    else out.append(character);
                }
            }
        }
        return out.append(quote).toString();
    }

    public void condition(StringBuilder out, String label, Condition condition, String indentation) {
        field(out, indentation, label, condition.description());
        String detailsIndentation = indentation + "    ";
        for (var reference : condition.references()) {
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

package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionHistory;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static java.lang.Character.isISOControl;
import static java.lang.Long.divideUnsigned;
import static java.lang.Long.remainderUnsigned;
import static java.lang.Thread.currentThread;
import static java.util.Arrays.deepToString;

public final class DiagnosticText {

    private static final List<TimeUnit> DURATION_UNITS = List.of(
            TimeUnit.DAYS, TimeUnit.HOURS, TimeUnit.MINUTES, TimeUnit.SECONDS,
            TimeUnit.MILLISECONDS, TimeUnit.MICROSECONDS, TimeUnit.NANOSECONDS);

    private final StringBuilder out = new StringBuilder();
    private final Throwable primaryCause;
    private final List<Throwable> secondaryCauses = new ArrayList<>();

    public DiagnosticText(Throwable primaryCause) {
        this.primaryCause = primaryCause;
    }

    List<Throwable> failures() {
        return List.copyOf(secondaryCauses);
    }

    public void line(String value) {
        out.append(value).append('\n');
    }

    public String finish(String indentation) {
        for (Throwable failure : secondaryCauses) field(indentation, "Suppressed", failure.getClass().getName());
        return out.substring(0, out.length() - 1);
    }

    public String cause(Throwable cause) {
        if (cause == null) return "null";
        String message = render(cause::getMessage);
        return cause.getClass().getName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    public static String attempt(Attempt<?> attempt) {
        var text = new DiagnosticText(attempt.cause());
        text.appendAttempt("Result", attempt);
        return text.finish("    ");
    }

    public static String conditionHistory(ConditionHistory<?> history) {
        var text = new DiagnosticText(null);
        text.appendHistory("Condition", history);
        return text.finish("    ");
    }

    public void histories(List<? extends ConditionHistory<?>> histories) {
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

    void appendAttempt(String label, Attempt<?> attempt) {
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
        String observation = observation(attempt);
        if (observation != null) {
            field("Observed now", observation);
        } else {
            field("Observed", "unavailable");
        }
        if (cause != null) field("Cause", cause(cause));
    }

    static String unsignedDuration(long remainingNanos) {
        StringJoiner result = new StringJoiner(" ").setEmptyValue("0 ns");
        for (TimeUnit unit : DURATION_UNITS) {
            long unitNanos = unit.toNanos(1);
            long count = divideUnsigned(remainingNanos, unitNanos);
            remainingNanos = remainderUnsigned(remainingNanos, unitNanos);
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

    String render(Supplier<String> rendering) {
        boolean interrupted = currentThread().isInterrupted();
        try {
            return rendering.get();
        } catch (Throwable failure) {
            retain(failure);
            return "<unprintable>";
        } finally {
            if (interrupted) currentThread().interrupt();
        }
    }

    void retain(Throwable failure) {
        if (failure != primaryCause && secondaryCauses.stream().noneMatch(previous -> previous == failure)) {
            secondaryCauses.add(failure);
        }
    }

    private String observation(Attempt<?> attempt) {
        return switch (attempt) {
            case SatisfiedObservation<?> evaluated -> value(evaluated.observed());
            case UnsatisfiedObservation<?> evaluated -> value(evaluated.observed());
            case FailedAfterObservation<?> failed -> value(failed.observed());
            case FailedBeforeObservation<?> ignored -> null;
        };
    }

    void diagnostic(Diagnostic diagnostic) {
        if (diagnostic.mismatch() != null) field("Mismatch", diagnostic.mismatch());
        for (var detail : diagnostic.fields()) {
            if (detail.value() instanceof AwaitCondition condition) {
                condition(detail.label(), condition, "    ");
            } else {
                field(detail.label(), value(detail.value()));
            }
        }
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
                    if (isISOControl(character)) out.append("\\u").append(HexFormat.of().toHexDigits(character));
                    else out.append(character);
                }
            }
        }
        return out.append(quote).toString();
    }

    void condition(String label, AwaitCondition condition, String indentation) {
        field(indentation, label, render(condition::description));
        String detailsIndentation = indentation + "    ";
        render(() -> {
            for (var reference : condition.references()) {
                field(detailsIndentation, reference.label(), value(reference.value()));
            }
            return "";
        });
        String explanation = render(condition::explanation);
        if (explanation != null) field(detailsIndentation, "Because", explanation);
    }

    public void field(String label, String value) {
        field("    ", label, value);
    }

    void field(String indentation, String label, String value) {
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

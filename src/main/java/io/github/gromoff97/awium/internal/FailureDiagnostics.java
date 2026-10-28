package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.AttemptRecord;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.AwaitFailure;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.lang.Thread.currentThread;
import static java.util.Arrays.deepToString;
import static java.util.Objects.requireNonNull;

public final class FailureDiagnostics {

    private static final List<TimeUnit> DURATION_UNITS = List.of(
            TimeUnit.DAYS, TimeUnit.HOURS, TimeUnit.MINUTES, TimeUnit.SECONDS,
            TimeUnit.MILLISECONDS, TimeUnit.MICROSECONDS, TimeUnit.NANOSECONDS);

    private final WaitCompletion outcome;
    private final Condition condition;
    private final WaitConfiguration configuration;
    private final Throwable cause;
    private final AttemptRecord.Progress sequence;

    private final List<Throwable> diagnosticFailures = new ArrayList<>();

    private FailureDiagnostics(WaitCompletion outcome, WaitConfiguration configuration) {
        this.outcome = requireNonNull(outcome, "outcome must not be null");
        this.configuration = requireNonNull(configuration, "configuration must not be null");
        var attempt = outcome.attempt();
        cause = attempt.result().cause();
        sequence = attempt.progress();
        condition = sequence == null ? attempt.condition() : sequence.sequence();
    }

    static String duration(Duration remaining) {
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

    public static AwaitFailure failure(WaitCompletion outcome, WaitConfiguration configuration) {
        var diagnostics = new FailureDiagnostics(outcome, configuration);
        return new AwaitFailure(outcome.failureReason(), diagnostics.message(), diagnostics.cause,
                diagnostics.diagnosticFailures);
    }

    private String uncontrolledHeading(boolean interrupted) {
        return switch (((AttemptResult.Uncontrolled) outcome.attempt().result()).operation()) {
            case WAITING -> interrupted
                    ? "Caller thread was interrupted while waiting" : "Waiting before the next attempt failed";
            case CONDITION -> interrupted
                    ? "Caller thread was interrupted during condition evaluation" : "Condition evaluation failed";
            case SOURCE -> interrupted
                    ? "Caller thread was interrupted during source retrieval" : "Source retrieval failed";
        };
    }

    private String title() {
        return switch (outcome) {
            case WaitCompletion.Timeout ignored -> "Acquisition deadline elapsed";
            case WaitCompletion.PersistenceFailure ignored ->
                    "Condition did not persist for the required duration";
            case WaitCompletion.Uncontrolled failure ->
                    uncontrolledHeading(failure.interrupted());
            case WaitCompletion.Satisfied ignored ->
                    throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    private String message() {
        AttemptRecord<?> attempt = outcome.attempt();
        StringBuilder out = new StringBuilder(title()).append("\n\n");
        condition(out, condition, "");
        String actual = attempt.hasObservation() ? render(() -> renderValue(attempt.observed())) : null;
        String mismatch = attempt.result() instanceof AttemptResult.Unsatisfied value ? value.mismatch() : null;
        attempt(out, attempt, actual, sequence == null ? mismatch : null);
        sequence(out, mismatch);
        timing(out);
        if (cause != null) {
            out.append('\n');
            field(out, "", "Cause", cause.getClass().getName());
            String message = render(cause::getMessage);
            if (message != null && !message.isBlank()) field(out, "Message", message);
        }
        for (Throwable failure : diagnosticFailures) {
            out.append('\n');
            field(out, "", "Diagnostics failure", failure.getClass().getName());
        }
        return out.deleteCharAt(out.length() - 1).toString();
    }

    private String render(Supplier<String> rendering) {
        boolean interrupted = currentThread().isInterrupted();
        try {
            return rendering.get();
        } catch (Throwable failure) {
            if (failure != cause && diagnosticFailures.stream().noneMatch(previous -> previous == failure)) {
                diagnosticFailures.add(failure);
            }
            return "<value unavailable: diagnostics failed>";
        } finally {
            if (interrupted) currentThread().interrupt();
        }
    }

    private static String renderValue(Object value) {
        String rendered = deepToString(new Object[] {value});
        return rendered.substring(1, rendered.length() - 1);
    }

    private void timing(StringBuilder out) {
        out.append('\n').append("Timing:\n");
        field(out, "Acquisition timeout",
                duration(Duration.ofNanos(configuration.upToNanos())));
        var completedAfter = outcome.attempt().timing().completionOffset();
        var elapsed = outcome instanceof WaitCompletion.Timeout timeout
                ? timeout.elapsed() : completedAfter;
        if (!elapsed.equals(completedAfter)) {
            field(out, "Last attempt completed after", duration(completedAfter));
        }
        field(out, "Elapsed", duration(elapsed));
        if (outcome instanceof WaitCompletion.PersistenceFailure failure) {
            var acquiredAfter = failure.acquiredAfter();
            field(out, "Acquired after", duration(acquiredAfter));
            field(out, "Required persistence", duration(Duration.ofNanos(configuration.persistenceNanos())));
            field(out, "Failure detected after", duration(completedAfter.minus(acquiredAfter)));
        }
        field(out, "Polling interval",
                duration(Duration.ofNanos(configuration.everyNanos())));
    }

    private static void attempt(StringBuilder out, AttemptRecord<?> attempt, String actual,
            String mismatch) {
        out.append('\n').append("Attempt: ").append(attempt.number())
                .append(" (").append(attempt.phase()).append(")\n");
        if (actual != null) {
            field(out, "Actual", actual);
        }
        if (mismatch != null) {
            field(out, "Mismatch", mismatch);
        }
    }

    private void condition(StringBuilder out, Condition condition, String indentation) {
        field(out, indentation, "Condition", condition.description());
        String detailsIndentation = indentation + "    ";
        var reference = condition.reference();
        if (reference != null) {
            field(out, detailsIndentation, reference.label(), render(() -> renderValue(reference.value())));
        }
        if (condition.explanation() != null) {
            field(out, detailsIndentation, "Because", condition.explanation());
        }
    }

    private void sequence(StringBuilder out, String mismatch) {
        if (sequence == null) {
            return;
        }
        out.append('\n').append("Sequence (captured ").append(sequence.capturedStages())
                .append(" of ").append(sequence.sequence().stages().size()).append("):\n");
        int stage = sequence.stageNumber();
        field(out, "Stage", stage + " of " + sequence.sequence().stages().size());
        condition(out, outcome.attempt().condition(), "    ");
        if (mismatch != null) {
            field(out, "        ", "Mismatch", mismatch);
        }
        if (outcome.attempt().result() instanceof AttemptResult.Satisfied
                && sequence.capturedStages() < sequence.sequence().stages().size()) {
            int next = sequence.capturedStages() + 1;
            field(out, "Next stage", next + " of " + sequence.sequence().stages().size());
            condition(out, sequence.sequence().stages().get(next - 1), "    ");
        }
    }

    private static void field(StringBuilder out, String label, String value) {
        field(out, "    ", label, value);
    }

    private static void field(StringBuilder out, String indentation,
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

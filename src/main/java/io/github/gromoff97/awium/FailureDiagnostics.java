package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.lang.Thread.currentThread;
import static java.util.Arrays.deepToString;
import static java.util.Objects.requireNonNull;

final class FailureDiagnostics {

    private static final List<TimeUnit> DURATION_UNITS = List.of(
            TimeUnit.DAYS, TimeUnit.HOURS, TimeUnit.MINUTES, TimeUnit.SECONDS,
            TimeUnit.MILLISECONDS, TimeUnit.MICROSECONDS, TimeUnit.NANOSECONDS);

    private static final int CAUSE_MESSAGE_LIMIT = 160;
    private static final String STACK_TRACE_HINT = "… <see stack trace>";

    private final WaitCompletion<?, ?> outcome;
    private final Condition<?, ?> condition;
    private final WaitConfiguration configuration;
    private final Object observed;
    private final String mismatch;
    private final AwaitFailure.Reason reason;
    private final Throwable cause;
    private final AttemptRecord.Progress sequence;

    private final List<Throwable> diagnosticFailures = new ArrayList<>();

    private FailureDiagnostics(WaitCompletion<?, ?> outcome,
            Condition<?, ?> condition, WaitConfiguration configuration) {
        this.outcome = requireNonNull(outcome, "outcome must not be null");
        this.configuration = requireNonNull(configuration, "configuration must not be null");
        requireNonNull(condition, "condition must not be null");
        var attempt = outcome.attempt().outcome();
        var evaluation = attempt instanceof AttemptRecord.Outcome.Evaluated<?, ?> value ? value.evaluation() : null;
        observed = switch (attempt) {
            case AttemptRecord.Outcome.Evaluated<?, ?> value -> value.observed();
            case AttemptRecord.Outcome.ConditionFailed<?, ?> value -> value.observed();
            case AttemptRecord.Outcome.SourceInterrupted<?, ?> value -> value.observed();
            default -> null;
        };
        mismatch = evaluation instanceof ConditionResult.Unsatisfied<?> value ? value.mismatch() : null;
        reason = outcome.failureReason();
        cause = outcome.cause();
        if (attempt instanceof AttemptRecord.Outcome.Evaluated<?, ?> value) {
            sequence = value.progress();
            this.condition = value.condition();
        } else if (attempt instanceof AttemptRecord.Outcome.ConditionFailed<?, ?> value) {
            sequence = value.progress();
            this.condition = value.condition();
        } else {
            sequence = null;
            this.condition = condition;
        }
    }

    static String duration(long nanos) {
        StringJoiner result = new StringJoiner(" ");
        for (TimeUnit unit : DURATION_UNITS) {
            long unitNanos = unit.toNanos(1);
            long count = nanos / unitNanos;
            nanos %= unitNanos;
            if (count != 0) {
                String name = unit.name().toLowerCase(Locale.ROOT);
                result.add(count + " " + (count == 1 ? name.substring(0, name.length() - 1) : name));
            }
        }
        return result.length() == 0 ? "0 nanoseconds" : result.toString();
    }

    static AwaitFailure failure(WaitCompletion<?, ?> outcome, Condition<?, ?> condition, WaitConfiguration configuration) {
        return new FailureDiagnostics(outcome, condition, configuration).failure();
    }

    private AwaitFailure failure() {
        boolean restoreInterrupt = currentThread().isInterrupted() || reason == AwaitFailure.Reason.INTERRUPTED;
        try {
            String message = message();
            return new AwaitFailure(reason, message, cause, diagnosticFailures);
        } finally {
            if (restoreInterrupt) {
                currentThread().interrupt();
            }
        }
    }

    private String uncontrolledHeading() {
        boolean interrupted = reason == AwaitFailure.Reason.INTERRUPTED;
        return switch (outcome.attempt().outcome()) {
            case AttemptRecord.Outcome.WaitingFailed<?, ?> ignored -> interrupted
                    ? "Caller thread was interrupted while waiting" : "Waiting before the next attempt failed";
            case AttemptRecord.Outcome.ConditionFailed<?, ?> ignored -> interrupted
                    ? "Caller thread was interrupted during condition evaluation" : "Condition evaluation failed";
            default -> interrupted
                    ? "Caller thread was interrupted during source retrieval" : "Source retrieval failed";
        };
    }

    private String title() {
        return switch (outcome) {
            case WaitCompletion.Timeout<?, ?> ignored -> "Acquisition deadline elapsed";
            case WaitCompletion.PersistenceFailure<?, ?> ignored ->
                    "Condition did not persist for the required duration";
            case WaitCompletion.Uncontrolled<?, ?> ignored ->
                    uncontrolledHeading();
            case WaitCompletion.Satisfied<?, ?> ignored ->
                    throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    private String message() {
        AttemptRecord<?, ?> attempt = outcome.attempt();
        StringBuilder out = new StringBuilder(title()).append("\n\n");
        condition(out);
        String actual = attempt.outcome().timing() instanceof AttemptRecord.Timing.AfterObservation
                ? render(() -> renderValue(observed)) : null;
        attempt(out, attempt.number(), actual, sequence == null ? mismatch : null);
        sequence(out);
        if (!(outcome instanceof WaitCompletion.Uncontrolled<?, ?>) || !diagnosticFailures.isEmpty()) {
            timing(out);
        }
        if (cause != null) {
            out.append('\n');
            field(out, "", "Cause", typeName(cause));
            String message = render(() -> causeMessage(cause));
            if (message != null) field(out, "Message", message);
        }
        for (Throwable failure : diagnosticFailures) {
            out.append('\n');
            field(out, "", "Diagnostics failure", typeName(failure));
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
                duration(configuration.upToNanos()));
        long completedAfter = outcome.attempt().outcome().timing().completionOffset().toNanos();
        switch (outcome) {
            case WaitCompletion.Timeout<?, ?> outcome -> {
                if (outcome.elapsedNanos() != completedAfter) {
                    field(out, "Last attempt completed after", duration(completedAfter));
                }
                field(out, "Elapsed", duration(outcome.elapsedNanos()));
            }
            case WaitCompletion.PersistenceFailure<?, ?> outcome -> {
                long acquiredAfter = outcome.acquiredAfterNanos();
                field(out, "Acquired after", duration(acquiredAfter));
                field(out, "Required persistence",
                        duration(configuration.persistenceNanos()));
                field(out, "Failure detected after", duration(completedAfter - acquiredAfter));
            }
            case WaitCompletion.Uncontrolled<?, ?> ignored -> {}
            case WaitCompletion.Satisfied<?, ?> ignored -> {}
        }
        field(out, "Polling interval",
                duration(configuration.everyNanos()));
    }

    private static void attempt(StringBuilder out, long number, String actual,
            String mismatch) {
        out.append('\n').append("Attempt: ").append(number).append('\n');
        if (actual != null) {
            field(out, "Actual", actual);
        }
        if (mismatch != null) {
            field(out, "Mismatch", mismatch);
        }
    }

    private void condition(StringBuilder out) {
        field(out, "", "Condition", condition.description());
        reference(out, condition.reference());
        if (condition.explanation() != null) {
            field(out, "Importance", condition.explanation());
        }
    }

    private void sequence(StringBuilder out) {
        if (sequence == null) {
            return;
        }
        out.append('\n').append("Sequence (captured ").append(sequence.capturedStages())
                .append(" of ").append(sequence.totalStages()).append("):\n");
        field(out, "Expectation", sequence.condition().description());
        reference(out, sequence.condition().reference());
        if (sequence.condition().explanation() != null) {
            field(out, "Importance", sequence.condition().explanation());
        }
        if (mismatch != null) {
            field(out, "Mismatch", mismatch);
        }
    }

    private void reference(StringBuilder out, Reference<?> reference) {
        if (reference != null) {
            field(out, reference.label(), render(() -> renderValue(reference.value())));
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

    private static String typeName(Throwable failure) {
        String simpleName = failure.getClass().getSimpleName();
        return simpleName.isBlank() ? failure.getClass().getName() : simpleName;
    }

    private static String causeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.codePointCount(0, normalized.length()) <= CAUSE_MESSAGE_LIMIT) {
            return normalized;
        }
        int prefixLength = CAUSE_MESSAGE_LIMIT
                - STACK_TRACE_HINT.codePointCount(0, STACK_TRACE_HINT.length());
        return normalized.substring(0, normalized.offsetByCodePoints(0, prefixLength))
                .stripTrailing() + STACK_TRACE_HINT;
    }
}

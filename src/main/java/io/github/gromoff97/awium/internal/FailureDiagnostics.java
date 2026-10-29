package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.HistoryEntry;
import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.AwaitFailure;
import java.time.Duration;

import static java.util.Objects.requireNonNull;
import static io.github.gromoff97.awium.internal.DiagnosticText.duration;
import static io.github.gromoff97.awium.internal.DiagnosticText.field;

public final class FailureDiagnostics {

    private final WaitCompletion outcome;
    private final Condition condition;
    private final WaitConfiguration configuration;
    private final Throwable cause;
    private final HistoryEntry.Progress sequence;

    private final DiagnosticText text;

    private FailureDiagnostics(WaitCompletion outcome, WaitConfiguration configuration) {
        this.outcome = requireNonNull(outcome, "outcome must not be null");
        this.configuration = requireNonNull(configuration, "configuration must not be null");
        var attempt = outcome.attempt();
        cause = attempt.result().cause();
        text = new DiagnosticText(cause);
        sequence = attempt.progress();
        condition = sequence == null ? attempt.condition() : sequence.sequence();
    }

    public static AwaitFailure failure(WaitCompletion outcome, WaitConfiguration configuration, long attemptNumber) {
        var diagnostics = new FailureDiagnostics(outcome, configuration);
        return new AwaitFailure(outcome.failureReason(), diagnostics.message(attemptNumber), diagnostics.cause,
                diagnostics.text.failures());
    }

    private String uncontrolledHeading(boolean interrupted) {
        return switch (((AttemptStatus.Uncontrolled) outcome.attempt().result()).operation()) {
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

    private String message(long attemptNumber) {
        HistoryEntry<?> attempt = outcome.attempt();
        StringBuilder out = new StringBuilder(title()).append("\n\n");
        text.condition(out, condition, "");
        String actual = attempt.hasObservation() ? text.value(attempt.observed()) : null;
        String mismatch = attempt.result() instanceof AttemptStatus.Unsatisfied value ? value.mismatch() : null;
        attempt(out, attemptNumber, attempt, actual, sequence == null ? mismatch : null);
        sequence(out, mismatch);
        timing(out);
        if (cause != null) {
            out.append('\n');
            field(out, "", "Cause", cause.getClass().getName());
            String message = text.render(cause::getMessage);
            if (message != null && !message.isBlank()) field(out, "Message", message);
        }
        for (Throwable failure : text.failures()) {
            out.append('\n');
            field(out, "", "Diagnostics failure", failure.getClass().getName());
        }
        return out.deleteCharAt(out.length() - 1).toString();
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

    private static void attempt(StringBuilder out, long number, HistoryEntry<?> attempt, String actual,
            String mismatch) {
        out.append('\n').append("Attempt: ").append(number)
                .append(" (").append(attempt.phase()).append(")\n");
        if (actual != null) {
            field(out, "Actual", actual);
        }
        if (mismatch != null) {
            field(out, "Mismatch", mismatch);
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
        text.condition(out, outcome.attempt().condition(), "    ");
        if (mismatch != null) {
            field(out, "        ", "Mismatch", mismatch);
        }
        if (outcome.attempt().result() instanceof AttemptStatus.Satisfied
                && sequence.capturedStages() < sequence.sequence().stages().size()) {
            int next = sequence.capturedStages() + 1;
            field(out, "Next stage", next + " of " + sequence.sequence().stages().size());
            text.condition(out, sequence.sequence().stages().get(next - 1), "    ");
        }
    }


}

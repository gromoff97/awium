package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.internal.DiagnosticText;

import static io.github.gromoff97.awium.internal.DiagnosticText.duration;
import static io.github.gromoff97.awium.internal.DiagnosticText.field;
import static java.util.Objects.requireNonNull;

/**
 * Consecutive attempts with the same check, phase, sequence progress, and diagnostic outcome.
 * Only the first and last invocations are retained. Their observations may differ;
 * a group describes repeated diagnostics, not equality of the observed objects.
 * Different exception instances always belong to different groups, even when
 * their types and messages match.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param firstNumber one-based number of the first invocation
 * @param lastNumber one-based number of the last invocation, inclusive
 * @param first first invocation in this group
 * @param last last invocation in this group; the same record as first for a single invocation
 */
public record AttemptGroup<OBSERVED>(long firstNumber, long lastNumber,
        HistoryEntry<OBSERVED> first, HistoryEntry<OBSERVED> last) {

    public AttemptGroup {
        if (firstNumber < 1 || lastNumber < firstNumber) {
            throw new IllegalArgumentException("attempt numbers must form a positive inclusive range");
        }
        requireNonNull(first, "first attempt must not be null");
        requireNonNull(last, "last attempt must not be null");
    }

    /** Returns the number of invocations represented by this group. */
    public long count() {
        return lastNumber - firstNumber + 1;
    }

    /**
     * Formats the group without repeating its shared condition and outcome.
     * Observed objects are rendered when this method is called, not snapshotted at check time.
     * A failing value renderer is replaced with a diagnostic placeholder.
     */
    @Override
    public String toString() {
        boolean repeated = count() > 1;
        var status = first.result();
        var cause = status.cause();
        var text = new DiagnosticText(cause);
        var out = new StringBuilder(repeated ? "Attempts " : "Attempt ").append(firstNumber);
        if (repeated) out.append('–').append(lastNumber);
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
        if (first.hasObservation()) field(out, repeated ? "First observed" : "Observed", text.value(first.observed()));
        if (repeated && last.hasObservation()) field(out, "Last observed", text.value(last.observed()));
        if (cause != null) {
            field(out, "Cause", cause.getClass().getName());
            String message = text.render(cause::getMessage);
            if (message != null && !message.isBlank()) field(out, "Message", message);
        }
        for (Throwable failure : text.failures()) field(out, "Diagnostics failure", failure.getClass().getName());
        return out.substring(0, out.length() - 1);
    }
}

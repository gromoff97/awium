package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.internal.DiagnosticText;

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
        return DiagnosticText.group(this, null);
    }
}

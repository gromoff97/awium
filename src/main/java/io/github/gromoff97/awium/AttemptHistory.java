package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AttemptGroup;
import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.HistoryEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/** Assigns attempt numbers and optionally groups consecutive diagnostic repetitions. */
final class AttemptHistory<OBSERVED> {

    private final List<AttemptGroup<OBSERVED>> completed;
    private long count;
    private long firstNumber;
    private HistoryEntry<OBSERVED> first;
    private HistoryEntry<OBSERVED> last;

    AttemptHistory(boolean recording) {
        completed = recording ? new ArrayList<>() : null;
    }

    void add(HistoryEntry<OBSERVED> attempt) {
        requireNonNull(attempt, "attempt must not be null");
        count++;
        if (completed == null) return;
        if (last == null || !sameDiagnostic(last, attempt)) {
            if (last != null) completed.add(new AttemptGroup<>(firstNumber, count - 1, first, last));
            firstNumber = count;
            first = attempt;
        }
        last = attempt;
    }

    long count() {
        return count;
    }

    List<AttemptGroup<OBSERVED>> snapshot() {
        if (last == null) return List.of();
        var groups = new ArrayList<AttemptGroup<OBSERVED>>(completed.size() + 1);
        groups.addAll(completed);
        groups.add(new AttemptGroup<>(firstNumber, count, first, last));
        return List.copyOf(groups);
    }

    private static boolean sameDiagnostic(HistoryEntry<?> previous, HistoryEntry<?> next) {
        return previous.phase() == next.phase()
                && previous.hasObservation() == next.hasObservation()
                && previous.result().cause() == next.result().cause()
                && Objects.equals(previous.progress(), next.progress())
                && sameCondition(previous.condition(), next.condition())
                && sameStatus(previous.result(), next.result());
    }

    private static boolean sameCondition(Condition previous, Condition next) {
        if (previous == next) return true;
        return previous.identity == next.identity
                && Objects.equals(previous.explanation(), next.explanation());
    }

    private static boolean sameStatus(AttemptStatus previous, AttemptStatus next) {
        // Only controlled outcomes repeat; an uncontrolled outcome ends the wait.
        return previous instanceof AttemptStatus.Satisfied && next instanceof AttemptStatus.Satisfied
                || previous instanceof AttemptStatus.Unsatisfied first && next instanceof AttemptStatus.Unsatisfied last
                        && Objects.equals(first.mismatch(), last.mismatch());
    }
}

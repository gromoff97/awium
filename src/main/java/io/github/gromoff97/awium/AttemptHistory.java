package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import static io.github.gromoff97.awium.Condition.Captured;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static java.util.Collections.emptyNavigableMap;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;

final class AttemptHistory<OBSERVED> {

    private final List<NavigableMap<Long, Attempt<OBSERVED>>> histories;
    private long count;

    AttemptHistory(boolean recording) {
        histories = recording ? new ArrayList<>() : null;
    }

    void add(int stageNumber, Attempt<OBSERVED> attempt) {
        requireNonNull(attempt, "attempt must not be null");
        count++;
        if (histories == null) return;
        while (histories.size() < stageNumber) histories.add(new TreeMap<>());
        var attempts = histories.get(stageNumber - 1);
        var previous = attempts.lastEntry();
        if (previous != null && sameGroup(previous.getValue(), attempt)) attempts.pollLastEntry();
        attempts.put(count, attempt);
    }

    long count() {
        return count;
    }

    List<ConditionHistory<OBSERVED>> conditions(Condition definition) {
        var definitions = definition instanceof Captured<?> captured ? captured.stages() : List.of(definition);
        var result = new ArrayList<ConditionHistory<OBSERVED>>(definitions.size());
        for (int i = 0; i < definitions.size(); i++) {
            NavigableMap<Long, Attempt<OBSERVED>> attempts = histories != null && i < histories.size()
                    ? histories.get(i) : emptyNavigableMap();
            result.add(new ConditionHistory<>(definitions.get(i), attempts));
        }
        return List.copyOf(result);
    }

    private static boolean sameGroup(Attempt<?> previous, Attempt<?> next) {
        return switch (previous) {
            case SatisfiedObservation<?> first -> next instanceof SatisfiedObservation<?> last
                    && sameObservation(first.observed(), last.observed());
            case UnsatisfiedObservation<?> first -> next instanceof UnsatisfiedObservation<?> last
                    && first.cause() == last.cause() && Objects.equals(first.mismatch(), last.mismatch())
                    && sameObservation(first.observed(), last.observed());
            default -> false;
        };
    }

    private static boolean sameObservation(Object previous, Object next) {
        try {
            return deepEquals(previous, next);
        } catch (Throwable failure) {
            // A failed diagnostic comparison must not merge values or replace the wait's outcome.
            return false;
        }
    }
}

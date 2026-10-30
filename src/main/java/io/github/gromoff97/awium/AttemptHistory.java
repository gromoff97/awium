package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.ExecutionStep;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import static java.util.Collections.emptyNavigableMap;
import static java.util.Objects.requireNonNull;

/** Assigns global attempt numbers and compacts repeated observations for each condition occurrence. */
final class AttemptHistory<OBSERVED> {

    private final List<NavigableMap<Long, Attempt<OBSERVED>>> histories;
    private long count;

    AttemptHistory(boolean recording) {
        histories = recording ? new ArrayList<>() : null;
    }

    void add(ExecutionStep<OBSERVED> step) {
        requireNonNull(step, "step must not be null");
        count++;
        if (histories == null) return;
        while (histories.size() < step.stageNumber()) histories.add(new TreeMap<>());
        var attempts = histories.get(step.stageNumber() - 1);
        var previous = attempts.lastEntry();
        if (previous != null && sameGroup(previous.getValue(), step.attempt())) attempts.pollLastEntry();
        attempts.put(count, step.attempt());
    }

    long count() {
        return count;
    }

    List<ConditionHistory<OBSERVED>> conditions(List<Condition> definitions) {
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
            case Attempt.Satisfied<?> first -> next instanceof Attempt.Satisfied<?> last
                    && sameObservation(first.observed(), last.observed());
            case Attempt.Unsatisfied<?> first -> next instanceof Attempt.Unsatisfied<?> last
                    && first.cause() == last.cause() && Objects.equals(first.mismatch(), last.mismatch())
                    && sameObservation(first.observed(), last.observed());
            default -> false;
        };
    }

    private static boolean sameObservation(Object previous, Object next) {
        try {
            return Objects.deepEquals(previous, next);
        } catch (Throwable failure) {
            // A failed diagnostic comparison must not merge values or replace the wait's outcome.
            return false;
        }
    }
}

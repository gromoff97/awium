package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static java.util.Collections.emptyNavigableMap;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;

final class AttemptHistory<OBSERVED> {

    private final List<NavigableMap<Long, Attempt<OBSERVED>>> histories = new ArrayList<>();
    private long count;

    void add(int stageNumber, Attempt<OBSERVED> attempt) {
        requireNonNull(attempt, "attempt must not be null");
        count++;
        while (histories.size() < stageNumber) histories.add(new TreeMap<>());
        var attempts = histories.get(stageNumber - 1);
        var previous = attempts.lastEntry();
        if (previous != null && sameGroup(previous.getValue(), attempt)) attempts.pollLastEntry();
        attempts.put(count, attempt);
    }

    long count() {
        return count;
    }

    List<ConditionHistory<OBSERVED>> conditions(List<? extends AwaitCondition> definitions) {
        var result = new ArrayList<ConditionHistory<OBSERVED>>(definitions.size());
        for (int i = 0; i < definitions.size(); i++) {
            NavigableMap<Long, Attempt<OBSERVED>> attempts = i < histories.size()
                    ? histories.get(i) : emptyNavigableMap();
            result.add(new ConditionHistory<>(definitions.get(i), attempts));
        }
        return List.copyOf(result);
    }

    private static boolean sameGroup(Attempt<?> previous, Attempt<?> next) {
        return switch (previous) {
            case SatisfiedObservation<?> first -> next instanceof SatisfiedObservation<?> last
                    && sameValue(first.diagnostic(), last.diagnostic())
                    && sameValue(first.observed(), last.observed());
            case UnsatisfiedObservation<?> first -> next instanceof UnsatisfiedObservation<?> last
                    && first.cause() == last.cause()
                    && sameValue(first.diagnostic(), last.diagnostic())
                    && sameValue(first.observed(), last.observed());
            default -> false;
        };
    }

    private static boolean sameValue(Object previous, Object next) {
        try {
            return deepEquals(previous, next);
        } catch (Throwable failure) {
            // A failed diagnostic comparison must not merge values or replace the wait's outcome.
            return false;
        }
    }
}

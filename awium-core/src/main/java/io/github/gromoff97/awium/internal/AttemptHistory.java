package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionHistory;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static java.util.Collections.emptyNavigableMap;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;

final class AttemptHistory<OBSERVED> {

    private final Map<Integer, NavigableMap<Long, Attempt<OBSERVED>>> histories = new HashMap<>();
    private long count;

    void add(int stageNumber, Attempt<OBSERVED> attempt) {
        requireNonNull(attempt, "attempt must not be null");
        count++;
        var attempts = histories.computeIfAbsent(stageNumber, ignored -> new TreeMap<>());
        var previous = attempts.lastEntry();
        if (previous != null && sameGroup(previous.getValue(), attempt)) attempts.pollLastEntry();
        attempts.put(count, attempt);
    }

    long count() {
        return count;
    }

    ConditionHistory<OBSERVED> flattened(AwaitCondition definition) {
        var attempts = new TreeMap<Long, Attempt<OBSERVED>>();
        for (var history : histories.values()) attempts.putAll(history);
        return new ConditionHistory<>(definition, attempts);
    }

    List<ConditionHistory<OBSERVED>> conditions(List<? extends AwaitCondition> definitions) {
        if (!definitions.isEmpty() && histories.keySet().stream().anyMatch(slot -> slot > definitions.size())) {
            throw new IllegalArgumentException("history slot exceeds condition count");
        }
        var result = new ArrayList<ConditionHistory<OBSERVED>>(definitions.size());
        for (int i = 0; i < definitions.size(); i++) {
            var attempts = histories.getOrDefault(i + 1, emptyNavigableMap());
            result.add(new ConditionHistory<>(definitions.get(i), attempts));
        }
        return List.copyOf(result);
    }

    private static boolean sameGroup(Attempt<?> previous, Attempt<?> next) {
        return switch (previous) {
            case SatisfiedObservation<?> first -> next instanceof SatisfiedObservation<?> last
                    && sameDiagnostic(first.diagnostic(), last.diagnostic())
                    && sameValue(first.observed(), last.observed());
            case UnsatisfiedObservation<?> first -> next instanceof UnsatisfiedObservation<?> last
                    && first.cause() == last.cause()
                    && sameDiagnostic(first.diagnostic(), last.diagnostic())
                    && sameValue(first.observed(), last.observed());
            default -> false;
        };
    }

    private static boolean sameDiagnostic(Diagnostic previous, Diagnostic next) {
        if (!sameValue(previous.mismatch(), next.mismatch()) || previous.fields().size() != next.fields().size()) return false;
        for (int i = 0; i < previous.fields().size(); i++) {
            var first = previous.fields().get(i);
            var last = next.fields().get(i);
            if (!first.label().equals(last.label()) || !sameValue(first.value(), last.value())) return false;
        }
        return true;
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

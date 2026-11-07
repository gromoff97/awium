package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionHistory;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static java.util.Collections.emptyNavigableMap;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;

public class DefaultAwaitHandler<OBSERVED> implements AwaitHandler<OBSERVED> {

    private final Map<Integer, NavigableMap<Long, Attempt<OBSERVED>>> histories = new HashMap<>();
    private Condition<?, ?> definition;

    public DefaultAwaitHandler() {}

    @Override
    public void onStart(WaitStarted start) {
        definition = requireNonNull(start, "start must not be null").condition();
        histories.clear();
    }

    @Override
    public void onAttemptFinish(AttemptFinished<OBSERVED> event) {
        var attempts = histories.computeIfAbsent(event.historySlot(), ignored -> new TreeMap<>());
        var previous = attempts.lastEntry();
        if (previous != null && sameGroup(previous.getValue(), event.historyAttempt())) attempts.pollLastEntry();
        attempts.put(event.number(), event.historyAttempt());
    }

    @Override
    public List<ConditionHistory<OBSERVED>> onFinish(WaitFinished finish) {
        if (finish.historyConditions().isEmpty()) {
            var attempts = new TreeMap<Long, Attempt<OBSERVED>>();
            for (var history : histories.values()) attempts.putAll(history);
            return List.of(new ConditionHistory<>(definition, attempts));
        }
        var definitions = finish.historyConditions().orElseThrow();
        if (!definitions.isEmpty() && histories.keySet().stream().anyMatch(slot -> slot > definitions.size())) {
            throw new IllegalArgumentException("history slot exceeds condition count");
        }
        return IntStream.range(0, definitions.size())
                .mapToObj(i -> new ConditionHistory<>(definitions.get(i), histories.getOrDefault(i + 1, emptyNavigableMap())))
                .toList();
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

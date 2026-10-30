package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.internal.DiagnosticText;
import java.util.NavigableMap;
import java.util.TreeMap;

import static java.util.Collections.unmodifiableNavigableMap;
import static java.util.Objects.requireNonNull;

/**
 * History of one condition occurrence in an ordinary or captured wait.
 * Reusing a condition in multiple captured positions produces separate histories.
 * An empty attempt map means execution did not reach this condition.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param condition original condition for this position in the wait
 * @param attempts immutable state changes keyed by their inclusive last attempt number across the wait
 */
public record ConditionHistory<OBSERVED>(Condition condition, NavigableMap<Long, Attempt<OBSERVED>> attempts) {

    public ConditionHistory {
        requireNonNull(condition, "condition must not be null");
        requireNonNull(attempts, "attempts must not be null");
        var copy = new TreeMap<Long, Attempt<OBSERVED>>();
        attempts.forEach((number, attempt) -> {
            if (number == null || number < 1) throw new IllegalArgumentException("attempt numbers must be positive");
            copy.put(number, requireNonNull(attempt, "attempt must not be null"));
        });
        attempts = unmodifiableNavigableMap(copy);
    }

    /** Formats this condition's history, including conditions execution did not reach. */
    @Override
    public String toString() {
        return DiagnosticText.conditionHistory("Condition", this);
    }
}

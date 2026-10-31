package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.internal.DiagnosticText;
import java.util.NavigableMap;
import java.util.TreeMap;

import static java.util.Collections.unmodifiableNavigableMap;
import static java.util.Objects.requireNonNull;

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

    @Override
    public String toString() {
        return DiagnosticText.conditionHistory("Condition", this);
    }
}

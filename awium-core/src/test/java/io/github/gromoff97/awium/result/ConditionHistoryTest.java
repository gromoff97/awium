package io.github.gromoff97.awium.result;

import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.support.TestConditions.accepted;
import static java.util.Collections.singletonMap;
import static java.util.Comparator.nullsFirst;
import static java.util.Comparator.reverseOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConditionHistoryTest {
    private final Attempt<String> attempt = new Attempt.SatisfiedObservation<>("ready");

    @Test
    void requiresAConditionAndNonNullAttempts() {
        assertThrows(NullPointerException.class, () -> new ConditionHistory<>(null, new TreeMap<>()));
        assertThrows(NullPointerException.class, () -> new ConditionHistory<>(accepted, null));
        assertThrows(NullPointerException.class, () -> new ConditionHistory<>(accepted, new TreeMap<>(singletonMap(1L, null))));
    }

    @Test
    void copiesTheSuppliedAttempts() {
        var supplied = new TreeMap<>(Map.of(1L, attempt));
        var history = new ConditionHistory<>(accepted, supplied);
        supplied.clear();

        assertThat(history.attempts()).containsOnlyKeys(1L).extractingByKey(1L).isSameAs(attempt);
    }

    @Test
    void ordersAttemptsByTheirNumbers() {
        var supplied = new TreeMap<Long, Attempt<String>>(reverseOrder());
        supplied.put(2L, attempt);
        supplied.put(1L, attempt);
        var history = new ConditionHistory<>(accepted, supplied);

        assertThat(history.attempts().keySet()).containsExactly(1L, 2L);
    }

    @Test
    void attemptsCannotBeChangedThroughMapViews() {
        var history = new ConditionHistory<>(accepted, new TreeMap<>(Map.of(1L, attempt, 2L, attempt)));
        var attempts = history.attempts();

        assertThrows(UnsupportedOperationException.class, attempts::clear);
        assertThrows(UnsupportedOperationException.class, () -> attempts.firstEntry().setValue(attempt));
        assertThrows(UnsupportedOperationException.class, () -> attempts.descendingMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> attempts.subMap(1L, 2L).clear());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsMissingOrNonpositiveAttemptNumbers(Long number) {
        var attempts = new TreeMap<Long, Attempt<String>>(nullsFirst(Long::compare));
        attempts.put(number, attempt);
        assertThrows(IllegalArgumentException.class, () -> new ConditionHistory<>(accepted, attempts));
    }
}

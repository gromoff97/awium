package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionHistory;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AwaitResultTest {

    @Test
    void resultsCopyConditionHistoriesAndSuccessRetainsLegitimateNull() {
        Attempt<String> attempt = new Attempt.SatisfiedObservation<>(null);
        var mutableAttempts = new TreeMap<>(Map.of(1L, attempt));
        var history = new ConditionHistory<>(diagnosticCondition("ready", null), mutableAttempts);
        var mutable = new ArrayList<>(List.of(history));

        var result = new AwaitResult.Succeeded<>(mutable, null);
        var failed = new AwaitResult.Failed<>(mutable,
                new AwaitFailure(AwaitFailure.Reason.TIMEOUT, "timeout", null, List.of()));
        mutable.clear();
        mutableAttempts.clear();

        for (var outcome : List.of(result, failed)) {
            assertThat(outcome.conditions()).singleElement().isSameAs(history);
            assertThrows(UnsupportedOperationException.class, () -> outcome.conditions().clear());
        }
        assertThat(history.attempts()).containsOnlyKeys(1L).extractingByKey(1L).isSameAs(attempt);
        assertThrows(UnsupportedOperationException.class, () -> history.attempts().clear());
        assertNull(result.result());
        assertNull(result.orThrow());
    }

    @Test
    void failedResultRequiresStructuredFailure() {
        var failure = new AwaitFailure(AwaitFailure.Reason.SOURCE_FAILED, "failed",
                new IllegalStateException("failed"), List.of());
        var result = new AwaitResult.Failed<String, String>(List.of(), failure);

        assertSame(failure, result.failure());
        assertEquals(0, result.conditions().size());
        assertThrows(NullPointerException.class,
                () -> new AwaitResult.Failed<String, String>(result.conditions(), null));
    }

    @Test
    void resultsRequireConditionHistoriesAndRejectNullEntries() {
        var failure = new AwaitFailure(AwaitFailure.Reason.TIMEOUT, "timeout", null, List.of());
        assertThrows(NullPointerException.class, () -> new AwaitResult.Succeeded<>(null, "ready"));
        assertThrows(NullPointerException.class, () -> new AwaitResult.Failed<>(null, failure));
        assertThrows(NullPointerException.class, () -> new AwaitResult.Succeeded<>(singletonList(null), "ready"));
        assertThrows(NullPointerException.class, () -> new AwaitResult.Failed<>(singletonList(null), failure));
        var condition = diagnosticCondition("ready", null);
        assertThrows(NullPointerException.class, () -> new ConditionHistory<>(null, new TreeMap<>()));
        assertThrows(NullPointerException.class, () -> new ConditionHistory<>(condition, null));
        assertThrows(NullPointerException.class, () -> new ConditionHistory<>(condition, new TreeMap<>(java.util.Collections.singletonMap(1L, null))));
    }

    @Test
    void failuresRequireAnOperationAndACause() {
        var cause = new IllegalStateException("failed");
        assertThrows(NullPointerException.class, () -> new Attempt.FailedAfterObservation<>(null, null, cause, null));
        assertThrows(NullPointerException.class, () -> new Attempt.FailedBeforeObservation<>(null, cause));
        assertThrows(NullPointerException.class, () -> new Attempt.FailedAfterObservation<>(null, Attempt.Operation.CONDITION, null, null));
        assertThrows(NullPointerException.class, () -> new Attempt.FailedBeforeObservation<>(Attempt.Operation.SOURCE, null));
    }

    @Test
    void historyOrdersNumbersNaturallyAndPreventsMutationThroughViews() {
        var condition = diagnosticCondition("ready", null);
        var attempt = new Attempt.SatisfiedObservation<>("ready");
        var supplied = new TreeMap<Long, Attempt<String>>(java.util.Comparator.reverseOrder());
        supplied.put(2L, attempt);
        supplied.put(1L, attempt);
        var history = new ConditionHistory<>(condition, supplied);
        var attempts = history.attempts();
        assertThat(attempts.keySet()).containsExactly(1L, 2L);
        assertThrows(UnsupportedOperationException.class, () -> attempts.firstEntry().setValue(attempt));
        assertThrows(UnsupportedOperationException.class, () -> attempts.descendingMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> attempts.subMap(1L, 2L).clear());
        supplied.put(0L, attempt);
        assertThrows(IllegalArgumentException.class, () -> new ConditionHistory<>(condition, supplied));
        var nullableNumbers = new TreeMap<Long, Attempt<String>>(java.util.Comparator.nullsFirst(Long::compare));
        nullableNumbers.put(null, attempt);
        assertThrows(IllegalArgumentException.class, () -> new ConditionHistory<>(condition, nullableNumbers));
    }
}

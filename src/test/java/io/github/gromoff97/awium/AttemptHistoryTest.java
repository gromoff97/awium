package io.github.gromoff97.awium;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static java.util.Arrays.asList;

class AttemptHistoryTest {

    @Test
    void snapshotsRemainUnchangedAsTheLastNumberMovesAndAnotherStateBegins() {
        var history = new AttemptHistory<String>(true);
        var condition = diagnosticCondition("ready", null);
        var empty = history.conditions(condition).getFirst().attempts();
        var first = new UnsatisfiedObservation<>(new String("pending"), "pending", null);
        var second = new UnsatisfiedObservation<>(new String("pending"), "pending", null);
        var third = new SatisfiedObservation<>("third");
        history.add(1, first);
        var before = history.conditions(condition).getFirst().attempts();
        history.add(1, second);
        var repeated = history.conditions(condition).getFirst().attempts();
        assertThrows(NullPointerException.class, () -> history.add(1, null));
        assertEquals(2, history.count());
        history.add(1, third);

        assertTrue(empty.isEmpty());
        assertEquals(Map.of(1L, first), before);
        assertEquals(Map.of(2L, second), repeated);
        assertSame(second, repeated.lastEntry().getValue());
        assertEquals(Map.of(2L, second, 3L, third),
                history.conditions(condition).getFirst().attempts());
        assertEquals(3, history.count());
        assertThrows(UnsupportedOperationException.class, repeated::pollLastEntry);
    }

    @Test
    void countsWithoutRetainingOrInspectingRecordsForOrdinaryAwait() {
        var history = new AttemptHistory<String>(false);
        var cause = new AssertionError("unreadable") {
            @Override public String getMessage() { throw new AssertionError("must not be read"); }
        };
        var condition = diagnosticCondition("ready", null);
        var attempt = new UnsatisfiedObservation<>("pending", "waiting", cause);
        assertEquals(0, history.count());
        for (int i = 0; i < 300; i++) history.add(1, attempt);
        assertEquals(300, history.count());
        assertTrue(history.conditions(condition).getFirst().attempts().isEmpty());
    }

    @Test
    void diagnosticChangesRemainInOrderEvenWhenAnEarlierMessageReturns() {
        var history = new AttemptHistory<String>(true);
        var condition = diagnosticCondition("ready", null);
        for (String mismatch : List.of("queued", "queued", "running", "queued", "queued")) {
            history.add(1, new UnsatisfiedObservation<>("observation", mismatch, null));
        }
        var attempts = history.conditions(condition).getFirst().attempts();
        assertThat(attempts.keySet()).containsExactly(2L, 3L, 5L);
        assertThat(attempts.values())
                .extracting(attempt -> assertInstanceOf(UnsatisfiedObservation.class, attempt).mismatch())
                .containsExactly("queued", "running", "queued");
    }

    @Test
    void observationEqualityHandlesNullArraysAndReturningToAnEarlierValue() {
        var history = new AttemptHistory<Object>(true);
        var condition = diagnosticCondition("ready", null);
        var observations = asList(null, null,
                new int[]{1}, new int[]{1}, new int[]{2}, new int[]{2}, new int[]{1});
        for (Object observation : observations) history.add(1, new UnsatisfiedObservation<>(observation, null, null));
        var attempts = history.conditions(condition).getFirst().attempts();
        assertThat(attempts.keySet()).containsExactly(2L, 4L, 6L, 7L);
        attempts.forEach((through, attempt) -> {
            assertInstanceOf(UnsatisfiedObservation.class, attempt);
            assertSame(observations.get(through.intValue() - 1), observation(attempt));
        });
    }

}

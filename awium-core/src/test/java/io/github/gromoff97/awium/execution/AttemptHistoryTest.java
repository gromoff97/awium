package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.support.TestCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class AttemptHistoryTest {

    @Test
    void failingDiagnosticEqualityKeepsAttemptsSeparate() {
        record Uncomparable() {
            @Override public boolean equals(Object other) { throw new AssertionError("cannot compare"); }
        }
        var history = new AttemptHistory<String>();
        var condition = new TestCondition<>("ready", null, AttemptResult::satisfied);
        for (int i = 0; i < 2; i++) {
            history.add(1, new SatisfiedObservation<>("same observation",
                    new Diagnostic(null, new Reference<>("Detail", new Uncomparable()))));
        }

        assertThat(history.conditions(List.of(condition)).getFirst().attempts().keySet()).containsExactly(1L, 2L);
    }

    @Test
    void snapshotsRemainUnchangedAsTheLastNumberMovesAndAnotherStateBegins() {
        var history = new AttemptHistory<String>();
        var condition = new TestCondition<>("ready", null, AttemptResult::satisfied).because("ready");
        var empty = history.conditions(List.of(condition)).getFirst().attempts();
        var first = new UnsatisfiedObservation<>(new String("pending"), "pending", null);
        var second = new UnsatisfiedObservation<>(new String("pending"), "pending", null);
        var third = new SatisfiedObservation<>("third");
        history.add(1, first);
        var before = history.conditions(List.of(condition)).getFirst().attempts();
        history.add(1, second);
        var repeated = history.conditions(List.of(condition)).getFirst().attempts();
        history.add(1, third);

        assertTrue(empty.isEmpty());
        assertEquals(Map.of(1L, first), before);
        assertEquals(Map.of(2L, second), repeated);
        assertSame(second, repeated.lastEntry().getValue());
        assertEquals(Map.of(2L, second, 3L, third),
                history.conditions(List.of(condition)).getFirst().attempts());
        assertEquals(3, history.count());
        assertThrows(UnsupportedOperationException.class, repeated::pollLastEntry);
    }

    @Test
    void rejectsNullAttemptsWithoutAdvancingTheNumber() {
        var history = new AttemptHistory<String>();
        history.add(1, new SatisfiedObservation<>("ready"));

        assertThrows(NullPointerException.class, () -> history.add(1, null));
        assertEquals(1, history.count());
    }

    @Test
    void groupsRepeatedAttemptsWithoutReadingCauseMessages() {
        var history = new AttemptHistory<String>();
        var cause = new AssertionError("unreadable") {
            @Override public String getMessage() { throw new AssertionError("must not be read"); }
        };
        var condition = new TestCondition<>("ready", null, AttemptResult::satisfied).because("ready");
        var attempt = new UnsatisfiedObservation<>("pending", "waiting", cause);
        assertEquals(0, history.count());
        for (int i = 0; i < 300; i++) history.add(1, attempt);
        assertEquals(300, history.count());
        assertEquals(Map.of(300L, attempt), history.conditions(List.of(condition)).getFirst().attempts());
    }

    @Test
    void diagnosticChangesRemainInOrderEvenWhenAnEarlierMessageReturns() {
        var history = new AttemptHistory<String>();
        var condition = new TestCondition<>("ready", null, AttemptResult::satisfied).because("ready");
        for (String mismatch : List.of("queued", "queued", "running", "queued", "queued")) {
            history.add(1, new UnsatisfiedObservation<>("observation", mismatch, null));
        }
        var attempts = history.conditions(List.of(condition)).getFirst().attempts();
        assertThat(attempts.keySet()).containsExactly(2L, 3L, 5L);
        assertThat(attempts.values())
                .extracting(attempt -> assertInstanceOf(UnsatisfiedObservation.class, attempt).mismatch())
                .containsExactly("queued", "running", "queued");
    }

    @Test
    void observationEqualityHandlesNullArraysAndReturningToAnEarlierValue() {
        var history = new AttemptHistory<Object>();
        var condition = new TestCondition<>("ready", null, AttemptResult::satisfied).because("ready");
        var observations = asList(null, null,
                new int[]{1}, new int[]{1}, new int[]{2}, new int[]{2}, new int[]{1});
        for (Object observation : observations) history.add(1, new UnsatisfiedObservation<>(observation, "not ready", null));
        var attempts = history.conditions(List.of(condition)).getFirst().attempts();
        assertThat(attempts.keySet()).containsExactly(2L, 4L, 6L, 7L);
        attempts.forEach((through, attempt) -> {
            assertInstanceOf(UnsatisfiedObservation.class, attempt);
            assertSame(observations.get(through.intValue() - 1), ((UnsatisfiedObservation<?>) attempt).observed());
        });
    }

}

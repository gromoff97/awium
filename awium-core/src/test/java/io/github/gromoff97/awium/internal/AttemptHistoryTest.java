package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.support.TestConditions.accepted;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class AttemptHistoryTest {

    @Test
    void failingDiagnosticEqualityKeepsAttemptsSeparate() {
        record Uncomparable() {
            @Override public boolean equals(Object other) { throw new AssertionError("cannot compare"); }
        }
        var history = new AttemptHistory<String>();
        for (int i = 0; i < 2; i++) {
            history.add(1, new SatisfiedObservation<>("same observation",
                    new Diagnostic(null, new Reference<>("Detail", new Uncomparable()))));
        }

        assertThat(history.conditions(List.of(accepted)).getFirst().attempts().keySet()).containsExactly(1L, 2L);
    }

    @Test
    void snapshotsRemainUnchangedAsTheLastNumberMovesAndAnotherStateBegins() {
        var history = new AttemptHistory<String>();
        var empty = history.conditions(List.of(accepted)).getFirst().attempts();
        var first = new UnsatisfiedObservation<>(new String("pending"), "pending", null);
        var second = new UnsatisfiedObservation<>(new String("pending"), "pending", null);
        var third = new SatisfiedObservation<>("third");
        history.add(1, first);
        var before = history.conditions(List.of(accepted)).getFirst().attempts();
        history.add(1, second);
        var repeated = history.conditions(List.of(accepted)).getFirst().attempts();
        history.add(1, third);

        assertTrue(empty.isEmpty());
        assertEquals(Map.of(1L, first), before);
        assertEquals(Map.of(2L, second), repeated);
        assertSame(second, repeated.lastEntry().getValue());
        assertEquals(Map.of(2L, second, 3L, third),
                history.conditions(List.of(accepted)).getFirst().attempts());
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
        var attempt = new UnsatisfiedObservation<>("pending", "waiting", cause);
        assertEquals(0, history.count());
        for (int i = 0; i < 300; i++) history.add(1, attempt);
        assertEquals(300, history.count());
        assertEquals(Map.of(300L, attempt), history.conditions(List.of(accepted)).getFirst().attempts());
    }

    @Test
    void diagnosticChangesRemainInOrderEvenWhenAnEarlierMessageReturns() {
        var history = new AttemptHistory<String>();
        for (String mismatch : List.of("queued", "queued", "running", "queued", "queued")) {
            history.add(1, new UnsatisfiedObservation<>("observation", mismatch, null));
        }
        var attempts = history.conditions(List.of(accepted)).getFirst().attempts();
        assertThat(attempts.keySet()).containsExactly(2L, 3L, 5L);
        assertThat(attempts.values())
                .extracting(attempt -> assertInstanceOf(UnsatisfiedObservation.class, attempt).mismatch())
                .containsExactly("queued", "running", "queued");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void arrayFieldsRetainContentChangesRatherThanNewArrayInstances(boolean satisfied) {
        var history = new AttemptHistory<String>();
        for (int value : new int[]{1, 1, 2, 2, 1}) {
            var diagnostic = new Diagnostic(satisfied ? null : "pending",
                    new Reference<>("Progress", new Object[]{new int[]{value}, null}));
            var attempt = satisfied ? new SatisfiedObservation<>("same observation", diagnostic)
                    : new UnsatisfiedObservation<>("same observation", diagnostic, null);
            history.add(1, attempt);
        }

        assertThat(history.conditions(List.of(accepted)).getFirst().attempts().keySet()).containsExactly(2L, 4L, 5L);
    }

    @ParameterizedTest
    @MethodSource("changedFields")
    void changedFieldLabelsCountsAndOrderRemainVisible(Diagnostic first, Diagnostic next) {
        var history = new AttemptHistory<String>();
        history.add(1, new SatisfiedObservation<>("same observation", first));
        history.add(1, new SatisfiedObservation<>("same observation", next));

        assertThat(history.conditions(List.of(accepted)).getFirst().attempts().keySet()).containsExactly(1L, 2L);
    }

    private static Stream<Arguments> changedFields() {
        var first = new Reference<>("First", 1);
        var second = new Reference<>("Second", 2);
        return Stream.of(
                argumentSet("label", new Diagnostic(null, first), new Diagnostic(null, new Reference<>("Renamed", 1))),
                argumentSet("count", new Diagnostic(null), new Diagnostic(null, first)),
                argumentSet("order", new Diagnostic(null, first, second), new Diagnostic(null, second, first)));
    }

    @Test
    void observationEqualityHandlesNullArraysAndReturningToAnEarlierValue() {
        var history = new AttemptHistory<Object>();
        var observations = asList(null, null,
                new int[]{1}, new int[]{1}, new int[]{2}, new int[]{2}, new int[]{1});
        for (Object observation : observations) history.add(1, new UnsatisfiedObservation<>(observation, "not ready", null));
        var attempts = history.conditions(List.of(accepted)).getFirst().attempts();
        assertThat(attempts.keySet()).containsExactly(2L, 4L, 6L, 7L);
        attempts.forEach((through, attempt) -> {
            assertInstanceOf(UnsatisfiedObservation.class, attempt);
            assertSame(observations.get(through.intValue() - 1), ((UnsatisfiedObservation<?>) attempt).observed());
        });
    }

}

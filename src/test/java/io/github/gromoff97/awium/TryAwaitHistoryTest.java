package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.result.HistoryEntry;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.result.HistoryEntry.Phase.ACQUISITION;
import static io.github.gromoff97.awium.result.HistoryEntry.Phase.PERSISTENCE;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryAwaitHistoryTest {

    @Test
    void keepsEveryAttemptWithoutCallingUserObjectMethods() {
        var time = new FakeTime(0);
        var probe = new ThrowingProbe();
        var execution = timedTryAwait(() -> probe,
                config(1, 10, 3), time, time).until(condition("ready", actual -> satisfied()));

        assertEquals(4, execution.attempts().size());
        assertEquals(List.of(ACQUISITION, PERSISTENCE, PERSISTENCE, PERSISTENCE),
                execution.attempts().stream().map(HistoryEntry::phase).toList());
        assertThrows(UnsupportedOperationException.class, execution.attempts()::clear);
    }

    @Test
    void boundsHistoryToTheLatestAttempts() {
        for (int count : new int[]{255, 256, 257, 300}) {
            var time = new FakeTime(0);
            int[] calls = {0};
            var result = timedTryAwait(() -> ++calls[0], config(1, count, 0), time, time).until(
                    condition("never ready", value -> unsatisfied("not ready")));

            assertEquals(count, calls[0]);
            assertEquals(IntStream.rangeClosed(1, count)
                    .filter(number -> number > count - 256)
                    .boxed().toList(), result.attempts().stream().map(HistoryEntry::observed).toList());
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertTrue(failure.message().contains("Attempt: " + count + " (ACQUISITION)"), failure.message());
            assertThrows(UnsupportedOperationException.class, result.attempts()::clear);
        }
    }

    @Test
    void configuredLimitRetainsRecordsWithoutLimitingPolling() {
        var expected = Map.of(
                1, List.of(5),
                2, List.of(4, 5),
                3, List.of(3, 4, 5),
                8, List.of(1, 2, 3, 4, 5));
        expected.forEach((limit, retained) -> {
            var time = new FakeTime(0);
            int[] calls = {0};
            var result = tryAwait(() -> ++calls[0]).historyTail(limit)
                    .every(ofNanos(1)).upTo(ofNanos(10)).usingTime(time, time)
                    .until(equalTo(5));

            assertEquals(5, result.orThrow());
            assertEquals(5, calls[0]);
            assertEquals(retained, result.attempts().stream().map(HistoryEntry::observed).toList());
            assertThrows(UnsupportedOperationException.class, result.attempts()::clear);
        });
    }

    @Test
    void unlimitedHistoryRetainsEveryAttemptAfterSuccessAndTimeout() {
        for (int expected : new int[]{300, 301}) {
            var time = new FakeTime(0);
            int[] calls = {0};
            var result = tryAwait(() -> ++calls[0]).historyTail(-1)
                    .every(ofNanos(1)).upTo(ofNanos(300)).usingTime(time, time)
                    .until(equalTo(expected));

            assertEquals(300, calls[0]);
            assertEquals(300, result.attempts().size());
            assertEquals(IntStream.rangeClosed(1, 300).boxed().toList(),
                    result.attempts().stream().map(HistoryEntry::observed).toList());
            if (expected == 300) {
                assertEquals(300, result.orThrow());
            } else {
                assertThrows(AwaitAssertionError.class, result::orThrow);
            }
        }
    }

    @Test
    void historyConfigurationIsIndependentAndSurvivesOtherSettings() {
        var time = new FakeTime(0);
        var original = tryAwait(() -> "ready").usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(10)).persisting(ofNanos(3));
        var otherTime = new FakeTime(0);
        var limited = original.historyTail(2).every(ofNanos(2)).upTo(ofNanos(20))
                .persisting(ofNanos(6)).usingTime(otherTime, otherTime);

        assertEquals(List.of(ofNanos(4), ofNanos(6)), completionOffsets(limited.until(isNotNull)));
        assertEquals(6, otherTime.getAsLong());
        assertEquals(0, time.getAsLong());
        assertEquals(List.of(ofNanos(0), ofNanos(1), ofNanos(2), ofNanos(3)), completionOffsets(original.until(isNotNull)));
        assertEquals(3, time.getAsLong());
    }

    @Test
    void historyTailDoesNotTruncateCapturedObservations() {
        var time = new FakeTime(0);
        int[] calls = {0};
        var result = tryAwait(() -> ++calls[0]).historyTail(1)
                .usingTime(time, time).every(ofNanos(1))
                .until(captured(equalTo(1), equalTo(2), equalTo(3)));

        assertEquals(List.of(1, 2, 3), result.orThrow());
        assertEquals(List.of(3), result.attempts().stream().map(HistoryEntry::observed).toList());
        assertEquals(3, result.attempts().getFirst().progress().capturedStages());
    }

    @Test
    void historyTailPreservesSelectionTypesForEveryContainerSource() {
        var time = new FakeTime(0);
        String optional = tryAwait(() -> Optional.of("ready")).historyTail(1)
                .every(ofNanos(1)).upTo(ofNanos(10)).usingTime(time, time)
                .until(OptionalConditions.present).orThrow();
        String element = tryAwait(() -> List.of("ready")).historyTail(1)
                .persisting(ofNanos(0)).usingTime(time, time)
                .until(CollectionConditions.singleElement).orThrow();
        var map = tryAwait(() -> Map.of("user", "ready")).historyTail(1)
                .every(ofNanos(1)).upTo(ofNanos(10)).persisting(ofNanos(0))
                .usingTime(time, time).historyTail(2);
        String value = map.until(MapConditions.valueFor("user")).orThrow();
        Map.Entry<String, String> entry = map.until(MapConditions.singleEntry).orThrow();

        assertEquals("ready", optional);
        assertEquals("ready", element);
        assertEquals("ready", value);
        assertEquals(Map.entry("user", "ready"), entry);
    }

    @Test
    void rejectsInvalidHistoryLimitsBeforeCallingTheSource() {
        int[] calls = {0};
        var waiting = tryAwait(() -> ++calls[0]);
        for (int limit : new int[]{0, -2, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> waiting.historyTail(limit));
        }
        assertEquals(0, calls[0]);
    }

    @Test
    void capturesEachStageEvenWhenDiagnosticsMatch() {
        var time = new FakeTime(0);
        var actual = new Object();
        var result = timedTryAwait(() -> actual, config(1, 10, 0), time, time).until(captured(
                condition("same description", ignored -> ConditionResult.satisfied()),
                condition("same description", ignored -> ConditionResult.satisfied()),
                condition("same description", ignored -> ConditionResult.satisfied())));

        assertEquals(3, result.attempts().size());
        assertEquals(List.of(1, 2, 3), result.attempts().stream().map(attempt -> attempt.progress().stageNumber()).toList());
    }

    private static List<Duration> completionOffsets(AwaitResult<?, ?> execution) {
        return execution.attempts().stream().map(attempt -> attempt.timing().completionOffset()).toList();
    }

    private static final class ThrowingProbe {

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("equals must not be called");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }

        @Override
        public String toString() {
            throw new AssertionError("toString must not be called");
        }
    }
}

package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AttemptGroup;
import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryAwaitHistoryTest {

    @Test
    void groupsRepeatedOutcomesWithoutCallingUserObjectMethods() {
        var time = new FakeTime(0);
        var probe = new ThrowingProbe();
        var execution = timedTryAwait(() -> probe,
                config(1, 10, 3), time, time).until(condition("ready", actual -> satisfied()));

        assertEquals(2, execution.attempts().size());
        assertEquals(List.of(ACQUISITION, PERSISTENCE),
                execution.attempts().stream().map(group -> group.first().phase()).toList());
        var persistence = execution.attempts().getLast();
        assertEquals(2, persistence.firstNumber());
        assertEquals(4, persistence.lastNumber());
        assertEquals(3, persistence.count());
        assertSame(probe, persistence.first().observed());
        assertSame(probe, persistence.last().observed());
        assertEquals(ofNanos(1), persistence.first().timing().completionOffset());
        assertEquals(ofNanos(3), persistence.last().timing().completionOffset());
        assertThrows(UnsupportedOperationException.class, execution.attempts()::clear);
    }

    @Test
    void repeatedDiagnosticsKeepBothBoundariesWithoutATailLimit() {
        for (int count : new int[]{255, 256, 257, 10_000}) {
            var time = new FakeTime(0);
            int[] calls = {0};
            var result = timedTryAwait(() -> ++calls[0], config(1, count, 0), time, time).until(
                    condition("never ready", value -> unsatisfied("not ready")));

            assertEquals(count, calls[0]);
            assertEquals(1, result.attempts().size());
            var group = result.attempts().getFirst();
            assertEquals(count, group.count());
            assertEquals(1, group.firstNumber());
            assertEquals(count, group.lastNumber());
            assertEquals(1, group.first().observed());
            assertEquals(count, group.last().observed());
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertTrue(failure.message().contains("Attempt: " + count + " (ACQUISITION)"), failure.message());
            assertThrows(UnsupportedOperationException.class, result.attempts()::clear);
        }
    }

    @Test
    void everyDiagnosticTransitionSurvivesBeyondTheFormerTailLimit() {
        var time = new FakeTime(0);
        int[] calls = {0};
        var result = tryAwait(() -> ++calls[0]).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(1_000))
                .until(condition("changing", value -> unsatisfied("value " + value)));

        assertEquals(1_000, result.attempts().size());
        for (int i = 0; i < result.attempts().size(); i++) {
            var group = result.attempts().get(i);
            assertEquals(i + 1, group.firstNumber());
            assertEquals(1, group.count());
            assertSame(group.first(), group.last());
            assertEquals(i + 1, group.first().observed());
        }
    }

    @Test
    void successAndTimeoutKeepAllGroupsWithoutChangingTheTerminalValue() {
        for (int expected : new int[]{300, 301}) {
            var time = new FakeTime(0);
            int[] calls = {0};
            var result = tryAwait(() -> ++calls[0])
                    .every(ofNanos(1)).upTo(ofNanos(300)).usingTime(time, time)
                    .until(equalTo(expected));

            assertEquals(300, calls[0]);
            assertEquals(300, result.attempts().stream().mapToLong(AttemptGroup::count).sum());
            assertEquals(1, result.attempts().getFirst().first().observed());
            assertEquals(300, result.attempts().getLast().last().observed());
            if (expected == 300) {
                assertEquals(2, result.attempts().size());
                assertEquals(300, result.orThrow());
            } else {
                assertEquals(1, result.attempts().size());
                assertThrows(AwaitAssertionError.class, result::orThrow);
            }
        }
    }

    @Test
    void immutableConfigurationsAndRepeatedWaitsOwnIndependentHistories() {
        var time = new FakeTime(0);
        var original = tryAwait(() -> "ready").usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(10)).persisting(ofNanos(3));
        var otherTime = new FakeTime(0);
        var changed = original.every(ofNanos(2)).upTo(ofNanos(20))
                .persisting(ofNanos(6)).usingTime(otherTime, otherTime);

        var longer = changed.until(isNotNull);
        assertEquals(ofNanos(6), longer.attempts().getLast().last().timing().completionOffset());
        assertEquals(6, otherTime.getAsLong());
        assertEquals(0, time.getAsLong());
        var shorter = original.until(isNotNull);
        assertEquals(ofNanos(3), shorter.attempts().getLast().last().timing().completionOffset());
        assertEquals(3, time.getAsLong());
        assertEquals(1, original.until(isNotNull).attempts().getFirst().firstNumber());
        assertEquals(4, shorter.attempts().getLast().lastNumber());
    }

    @Test
    void capturedKeepsEverySuccessfulObservationAndTheIdentityOfEachStage() {
        var time = new FakeTime(0);
        int[] calls = {0};
        var stage = condition("same description", actual -> satisfied());
        var result = tryAwait(() -> ++calls[0])
                .usingTime(time, time).every(ofNanos(1))
                .until(captured(stage, stage, stage));

        assertEquals(List.of(1, 2, 3), result.orThrow());
        assertEquals(3, result.attempts().size());
        assertEquals(List.of(1, 2, 3), result.attempts().stream()
                .map(group -> group.first().progress().stageNumber()).toList());
        assertEquals(3, result.attempts().getLast().last().progress().capturedStages());
    }

    @Test
    void historyRetainsSourceTypesAlongsideEveryContainerSelection() {
        var time = new FakeTime(0);
        var optional = tryAwait(() -> Optional.of("ready"))
                .every(ofNanos(1)).upTo(ofNanos(10)).usingTime(time, time)
                .until(OptionalConditions.present);
        var collection = tryAwait(() -> List.of("ready"))
                .persisting(ofNanos(0)).usingTime(time, time)
                .until(CollectionConditions.singleElement);
        var map = tryAwait(() -> Map.of("user", "ready"))
                .every(ofNanos(1)).upTo(ofNanos(10)).persisting(ofNanos(0)).usingTime(time, time);
        var value = map.until(MapConditions.valueFor("user"));
        var entry = map.until(MapConditions.singleEntry);

        String selectedOptional = optional.orThrow();
        Optional<String> observedOptional = optional.attempts().getFirst().first().observed();
        String selectedElement = collection.orThrow();
        List<String> observedCollection = collection.attempts().getFirst().first().observed();
        String selectedValue = value.orThrow();
        Map<String, String> observedMap = value.attempts().getFirst().first().observed();
        Map.Entry<String, String> selectedEntry = entry.orThrow();
        assertEquals("ready", selectedOptional);
        assertEquals(Optional.of("ready"), observedOptional);
        assertEquals("ready", selectedElement);
        assertEquals(List.of("ready"), observedCollection);
        assertEquals("ready", selectedValue);
        assertEquals(Map.of("user", "ready"), observedMap);
        assertEquals(Map.entry("user", "ready"), selectedEntry);
    }

    @Test
    void nestedReasonCopiesDoNotSplitRepeatedStructuralFailures() {
        var time = new FakeTime(0);
        var result = tryAwait(() -> List.<String>of()).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(300))
                .until(CollectionConditions.singleElement(equalTo("ready")).because("one ready element"));

        assertEquals(1, result.attempts().size());
        assertEquals(300, result.attempts().getFirst().count());
        assertEquals("one ready element", result.attempts().getFirst().last().condition().explanation());
        assertInstanceOf(AttemptStatus.Unsatisfied.class, result.attempts().getFirst().first().result());
    }

    @Test
    void retainsEveryNewErrorWithoutReadingItsMessageOrComparingIt() {
        var time = new FakeTime(0);
        var errors = new java.util.ArrayList<Throwable>();
        int[] calls = {0};
        var result = tryAwait(() -> ++calls[0]).usingTime(time, time).every(ofNanos(1))
                .until(condition("ready", value -> {
                    if (value == 4) return satisfied();
                    var error = new AssertionError("same message") {
                        @Override public String getMessage() { throw new AssertionError("message must not be read"); }
                        @Override public boolean equals(Object other) { throw new AssertionError("equals must not be called"); }
                        @Override public int hashCode() { throw new AssertionError("hashCode must not be called"); }
                        @Override public String toString() { throw new AssertionError("toString must not be called"); }
                    };
                    errors.add(error);
                    return unsatisfied("pending", error);
                }));

        assertEquals(4, result.orThrow());
        assertEquals(4, result.attempts().size());
        for (int i = 0; i < errors.size(); i++) {
            assertSame(errors.get(i), result.attempts().get(i).first().result().cause());
            assertEquals(1, result.attempts().get(i).count());
        }
        String report = result.toString();
        assertEquals(3, report.lines().filter(line -> line.strip().startsWith("Cause:")).count());
        assertEquals(1, report.lines().filter(line -> line.contains("Condition:")).count());
    }

    @Test
    void repetitionsOfTheSameErrorCanStillBeGrouped() {
        var time = new FakeTime(0);
        var error = new AssertionError("same error");
        int[] calls = {0};
        var result = tryAwait(() -> ++calls[0]).usingTime(time, time).every(ofNanos(1))
                .until(condition("ready", value -> value == 4 ? satisfied() : unsatisfied("pending", error)));

        assertEquals(4, result.orThrow());
        assertEquals(2, result.attempts().size());
        assertEquals(3, result.attempts().getFirst().count());
        assertSame(error, result.attempts().getFirst().first().result().cause());
        assertSame(error, result.attempts().getFirst().last().result().cause());
    }

    @Test
    void repeatedSelectorsKeepTheirNestingLevelWithoutSplittingSameLevelRepetitions() {
        var time = new FakeTime(0);
        var emptyOuter = List.<List<String>>of();
        var emptyInner = List.of(List.<String>of());
        var ready = List.of(List.of("ready"));
        var observations = List.of(emptyOuter, emptyOuter, emptyInner, emptyInner, ready).iterator();
        var check = CollectionConditions.singleElement(
                CollectionConditions.singleElement(Conditions.instanceOf(String.class)));
        var result = tryAwait(observations::next).usingTime(time, time).every(ofNanos(1)).until(check);

        String selected = result.orThrow();
        assertEquals("ready", selected);
        assertEquals(List.of(2L, 2L, 1L), result.attempts().stream().map(AttemptGroup::count).toList());
        assertEquals(List.of("collection size was 0", "single element: collection size was 0"),
                result.attempts().subList(0, 2).stream().map(group ->
                        assertInstanceOf(AttemptStatus.Unsatisfied.class, group.first().result()).mismatch()).toList());
        assertSame(check, result.attempts().get(0).first().condition());
        assertSame(check, result.attempts().get(1).first().condition());
    }

    @Test
    void nestedSelectionScopeAccumulatesAcrossDifferentContainerKinds() {
        var time = new FakeTime(0);
        var observations = List.of(Optional.<List<List<String>>>empty(),
                Optional.of(List.<List<String>>of()), Optional.of(List.of(List.<String>of())),
                Optional.of(List.of(List.of("ready")))).iterator();
        var result = tryAwait(observations::next).usingTime(time, time).every(ofNanos(1))
                .until(OptionalConditions.hasValue(CollectionConditions.singleElement(
                        CollectionConditions.singleElement(Conditions.instanceOf(String.class)))));

        assertEquals("ready", result.orThrow());
        assertEquals(4, result.attempts().size());
        assertEquals(List.of("optional was empty", "optional value: collection size was 0",
                        "optional value: single element: collection size was 0"),
                result.attempts().subList(0, 3).stream().map(group ->
                        assertInstanceOf(AttemptStatus.Unsatisfied.class, group.first().result()).mismatch()).toList());
    }

    private static final class ThrowingProbe {
        @Override public boolean equals(Object other) { throw new AssertionError("equals must not be called"); }
        @Override public int hashCode() { throw new AssertionError("hashCode must not be called"); }
        @Override public String toString() { throw new AssertionError("toString must not be called"); }
    }
}

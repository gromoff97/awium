package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static io.github.gromoff97.awium.result.Attempt.Unsatisfied;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static java.util.Collections.nCopies;
import static java.util.Map.Entry;
import static java.util.stream.IntStream.range;
import static java.util.stream.Stream.concat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryAwaitHistoryTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void capturedRetainsEveryObservationTransitionWithinAnUnsuccessfulStage() {
        var observations = List.of(
                nCopies(4, 0),
                nCopies(2, 10),
                nCopies(6, 20),
                nCopies(12, 30),
                nCopies(6, 40)).stream().flatMap(List::stream).toList();
        var source = observations.iterator();
        var result = tryAwait(source::next).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(30))
                .until(captured(equalTo(100), equalTo(200)));

        assertInstanceOf(Failed.class, result);
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(4L, 6L, 12L, 24L, 30L);
        assertThat(attempts(result)).hasOnlyElementsOfType(Unsatisfied.class)
                .extracting(AwaitTestAccess::observation).containsExactly(0, 10, 20, 30, 40);
        assertTrue(result.conditions().getLast().attempts().isEmpty());
    }

    @Test
    void groupsRepeatedReferencesWithoutCallingTheirObjectMethods() {
        var probe = new ThrowingProbe();
        var execution = timedTryAwait(() -> probe,
                config(1, 10, 3), time, time).until(condition("ready", actual -> satisfied()));

        assertEquals(1, attempts(execution).size());
        assertEquals(4L, execution.conditions().getFirst().attempts().lastKey());
        assertSame(probe, observation(attempts(execution).getFirst()));
        assertEquals(3, time.getAsLong());
        assertThrows(UnsupportedOperationException.class, attempts(execution)::clear);
    }

    @ParameterizedTest(name = "count={0}")
    @ValueSource(ints = {255, 256, 257, 10_000})
    void repeatedObservationsAndDiagnosticsKeepTheLastNumberWithoutATailLimit(int count) {
        int[] calls = {0};
        var result = timedTryAwait(() -> {
            calls[0]++;
            return 1;
        }, config(1, count, 0), time, time).until(
                condition("never ready", value -> unsatisfied("not ready")));

        assertEquals(count, calls[0]);
        assertEquals(1, attempts(result).size());
        var attempt = attempts(result).getFirst();
        assertEquals((long) count, result.conditions().getFirst().attempts().lastKey());
        assertEquals(1, observation(attempt));
        assertThat(failure(result).message()).contains("Attempt: " + count + " (ACQUISITION)");
        assertThrows(UnsupportedOperationException.class, attempts(result)::clear);
    }

    @Test
    void everyDiagnosticTransitionSurvivesBeyondTheFormerTailLimit() {
        int[] calls = {0};
        var result = tryAwait(() -> ++calls[0]).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(1_000))
                .until(condition("changing", value -> unsatisfied("value " + value)));

        assertEquals(1_000, attempts(result).size());
        for (int i = 0; i < attempts(result).size(); i++) {
            var attempt = attempts(result).get(i);
            assertSame(attempt, result.conditions().getFirst().attempts().get((long) i + 1));
            assertEquals(i + 1, observation(attempt));
        }
    }

    @Test
    void successKeepsAllGroupsAndTheTerminalValue() {
        assertEquals(300, historyThrough300Attempts(300).orThrow());
    }

    @Test
    void timeoutKeepsAllGroupsAndTheTerminalFailure() {
        var result = historyThrough300Attempts(301);
        assertThrows(AwaitAssertionError.class, result::orThrow);
    }

    private AwaitResult<Integer, Integer> historyThrough300Attempts(int expected) {
        int[] calls = {0};
        var result = tryAwait(() -> ++calls[0])
                .every(ofNanos(1)).upTo(ofNanos(300)).usingTime(time, time)
                .until(equalTo(expected));

        assertEquals(300, calls[0]);
        assertEquals(300, result.conditions().getFirst().attempts().lastKey());
        assertEquals(1, observation(attempts(result).getFirst()));
        assertEquals(300, observation(attempts(result).getLast()));
        assertEquals(300, attempts(result).size());
        return result;
    }

    @Test
    void immutableConfigurationsAndRepeatedWaitsOwnIndependentHistories() {
        var original = tryAwait(() -> "ready").usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(10)).persisting(ofNanos(3));
        var otherTime = new FakeTime(0);
        var changed = original.every(ofNanos(2)).upTo(ofNanos(20))
                .persisting(ofNanos(6)).usingTime(otherTime, otherTime);

        changed.until(isNotNull);
        assertEquals(6, otherTime.getAsLong());
        assertEquals(0, time.getAsLong());
        var shorter = original.until(isNotNull);
        assertEquals(3, time.getAsLong());
        assertEquals(4L, original.until(isNotNull).conditions().getFirst().attempts().lastKey());
        assertEquals(4, shorter.conditions().getFirst().attempts().lastKey());
    }

    @Test
    void capturedKeepsEverySuccessfulObservationAndTheIdentityOfEachStage() {
        int[] calls = {0};
        var stage = condition("same description", actual -> satisfied());
        var result = tryAwait(() -> ++calls[0])
                .usingTime(time, time).every(ofNanos(1))
                .until(captured(stage, stage, stage));

        assertEquals(List.of(1, 2, 3), result.orThrow());
        assertEquals(3, attempts(result).size());
        assertThat(result.conditions()).extracting(history -> history.attempts().lastKey()).containsExactly(1L, 2L, 3L);
        result.conditions().forEach(history -> assertSame(stage, history.condition()));
    }

    @Test
    void historyRetainsSourceTypesAlongsideEveryContainerSelection() {
        var optional = tryAwait(() -> Optional.of("ready"))
                .every(ofNanos(1)).upTo(ofNanos(10)).usingTime(time, time)
                .until(present);
        var collection = tryAwait(() -> List.of("ready"))
                .persisting(ofNanos(0)).usingTime(time, time)
                .until(singleElement);
        var map = tryAwait(() -> Map.of("user", "ready"))
                .every(ofNanos(1)).upTo(ofNanos(10)).persisting(ofNanos(0)).usingTime(time, time);
        var value = map.until(valueFor("user"));
        var entry = map.until(singleEntry);

        String selectedOptional = optional.orThrow();
        Optional<String> observedOptional = observation(attempts(optional).getFirst());
        String selectedElement = collection.orThrow();
        List<String> observedCollection = observation(attempts(collection).getFirst());
        String selectedValue = value.orThrow();
        Map<String, String> observedMap = observation(attempts(value).getFirst());
        Entry<String, String> selectedEntry = entry.orThrow();
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
        var result = tryAwait(() -> List.<String>of()).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(300))
                .until(singleElement(equalTo("ready")).because("one ready element"));

        assertEquals(1, attempts(result).size());
        assertEquals(300, result.conditions().getFirst().attempts().firstKey());
        assertEquals("one ready element", result.conditions().getFirst().condition().explanation());
        assertInstanceOf(Unsatisfied.class, attempts(result).getFirst());
    }

    @Test
    void retainsEveryNewErrorWithoutReadingItsMessageOrComparingIt() {
        var errors = range(0, 3).mapToObj(ignored -> new AssertionError("same message") {
            @Override public String getMessage() { throw new AssertionError("message must not be read"); }
            @Override public boolean equals(Object other) { throw new AssertionError("equals must not be called"); }
            @Override public int hashCode() { throw new AssertionError("hashCode must not be called"); }
            @Override public String toString() { throw new AssertionError("toString must not be called"); }
        }).toList();
        var outcomes = concat(errors.stream().map(error -> unsatisfied("pending", error)), Stream.of(satisfied())).iterator();
        var result = tryAwait(() -> 1).usingTime(time, time).every(ofNanos(1))
                .until(condition("ready", value -> outcomes.next()));

        assertEquals(1, result.orThrow());
        assertEquals(4, attempts(result).size());
        for (int i = 0; i < errors.size(); i++) {
            assertSame(errors.get(i), attempts(result).get(i).cause());
            assertSame(attempts(result).get(i), result.conditions().getFirst().attempts().get((long) i + 1));
        }
        String report = result.toString();
        assertEquals(3, report.lines().filter(line -> line.strip().startsWith("Cause:")).count());
        assertEquals(1, report.lines().filter(line -> line.startsWith("Condition ")).count());
    }

    @Test
    void repetitionsOfTheSameErrorCanStillBeGrouped() {
        var error = new AssertionError("same error");
        var observations = List.of(1, 1, 1, 4).iterator();
        var outcomes = List.<ConditionResult>of(unsatisfied("pending", error), unsatisfied("pending", error),
                unsatisfied("pending", error), satisfied()).iterator();
        var result = tryAwait(observations::next).usingTime(time, time).every(ofNanos(1))
                .until(condition("ready", value -> outcomes.next()));

        assertEquals(4, result.orThrow());
        assertEquals(2, attempts(result).size());
        assertEquals(3, result.conditions().getFirst().attempts().firstKey());
        assertSame(error, attempts(result).getFirst().cause());
    }

    @Test
    void failedObservationComparisonsKeepSeparateAttemptsWithoutFailingTheWait() {
        var result = timedTryAwait(ThrowingProbe::new,
                config(1, 10, 3), time, time).until(condition("ready", actual -> satisfied()));

        assertInstanceOf(Satisfied.class, result);
        assertThat(attempts(result)).hasSize(4).hasOnlyElementsOfType(Attempt.Satisfied.class);
        assertSame(observation(attempts(result).getLast()), result.orThrow());
    }

    @Test
    void repeatedSelectorsKeepTheirNestingLevelWithoutSplittingSameLevelRepetitions() {
        var emptyOuter = List.<List<String>>of();
        var emptyInner = List.of(List.<String>of());
        var ready = List.of(List.of("ready"));
        var observations = List.of(emptyOuter, emptyOuter, emptyInner, emptyInner, ready).iterator();
        var check = singleElement(
                singleElement(instanceOf(String.class)));
        var result = tryAwait(observations::next).usingTime(time, time).every(ofNanos(1)).until(check);

        String selected = result.orThrow();
        assertEquals("ready", selected);
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(2L, 4L, 5L);
        assertThat(attempts(result).subList(0, 2))
                .extracting(attempt -> assertInstanceOf(Unsatisfied.class, attempt).mismatch())
                .containsExactly("collection size was 0", "single element: collection size was 0");
        assertSame(check, result.conditions().getFirst().condition());
    }

    @Test
    void nestedSelectionScopeAccumulatesAcrossDifferentContainerKinds() {
        var observations = List.of(Optional.<List<List<String>>>empty(),
                Optional.of(List.<List<String>>of()), Optional.of(List.of(List.<String>of())),
                Optional.of(List.of(List.of("ready")))).iterator();
        var result = tryAwait(observations::next).usingTime(time, time).every(ofNanos(1))
                .until(hasValue(singleElement(
                        singleElement(instanceOf(String.class)))));

        assertEquals("ready", result.orThrow());
        assertEquals(4, attempts(result).size());
        assertThat(attempts(result).subList(0, 3))
                .extracting(attempt -> assertInstanceOf(Unsatisfied.class, attempt).mismatch())
                .containsExactly("optional was empty", "optional value: collection size was 0",
                        "optional value: single element: collection size was 0");
    }

    private static final class ThrowingProbe {
        @Override public boolean equals(Object other) { throw new AssertionError("equals must not be called"); }
        @Override public int hashCode() { throw new AssertionError("hashCode must not be called"); }
        @Override public String toString() { throw new AssertionError("toString must not be called"); }
    }
}

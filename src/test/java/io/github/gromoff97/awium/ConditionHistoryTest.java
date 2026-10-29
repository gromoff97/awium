package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.CollectionConditions.all;
import static io.github.gromoff97.awium.CollectionConditions.any;
import static io.github.gromoff97.awium.MapConditions.noValue;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConditionHistoryTest {

    @Test
    void typeChecksRetainActualTypesEvenWhenValuesHaveTheSameText() {
        for (var condition : List.of(Conditions.instanceOf(String.class), Conditions.exactlyInstanceOf(String.class))) {
            var time = new FakeTime(0);
            var values = java.util.Arrays.asList(null, null,
                    new StringBuilder("pending"), new StringBuilder("pending"),
                    new StringBuffer("pending"), "ready").iterator();
            var result = tryAwait(values::next).usingTime(time, time).every(ofNanos(1)).until(condition);

            String value = result.orThrow();
            assertEquals("ready", value);
            assertEquals(List.of(2L, 2L, 1L, 1L), result.attempts().stream().map(group -> group.count()).toList());
            assertEquals(List.of("value was null", "value type was java.lang.StringBuilder",
                    "value type was java.lang.StringBuffer"), mismatches(result));
        }
    }

    @Test
    void sortingRetainsTheFirstOutOfOrderPairWithoutRepeatingComparisons() {
        var time = new FakeTime(0);
        var values = new ArrayList<>(List.of(3, 1, 2));
        int[] calls = {0};
        int[] comparisons = {0};
        var result = tryAwait(() -> {
            switch (++calls[0]) {
                case 1 -> { return null; }
                case 3 -> { values.set(0, 1); values.set(1, 3); }
                case 4 -> { values.set(1, 2); values.set(2, 3); }
                default -> {}
            }
            return values;
        }).usingTime(time, time).every(ofNanos(1)).until(CollectionConditions.sorted((left, right) -> {
            comparisons[0]++;
            return Integer.compare(left, right);
        }));

        assertSame(values, result.orThrow());
        assertEquals(4, calls[0]);
        assertEquals(5, comparisons[0]);
        assertEquals(List.of("collection was null", "collection was not sorted at indices 0 and 1",
                "collection was not sorted at indices 1 and 2"), mismatches(result));
    }

    @Test
    void duplicatePositionSurvivesMutationWithoutRepeatingEqualityChecks() {
        int[] comparisons = {0};
        class Element {
            final int number;
            Element(int number) { this.number = number; }
            @Override public boolean equals(Object other) {
                comparisons[0]++;
                return other instanceof Element element && number == element.number;
            }
            @Override public int hashCode() { return number; }
        }
        var time = new FakeTime(0);
        var values = new ArrayList<>(List.of(new Element(1), new Element(1), new Element(2)));
        int[] calls = {0};
        var result = tryAwait(() -> {
            switch (++calls[0]) {
                case 1 -> { return null; }
                case 3 -> values.set(1, new Element(2));
                case 4 -> values.set(2, new Element(3));
                default -> {}
            }
            return values;
        }).usingTime(time, time).every(ofNanos(1)).until(CollectionConditions.hasNoDuplicates);

        assertSame(values, result.orThrow());
        assertEquals(4, calls[0]);
        assertEquals(7, comparisons[0]);
        assertEquals(List.of("collection was null",
                "collection contained a duplicate at iteration index 1",
                "collection contained a duplicate at iteration index 2"), mismatches(result));
    }

    @Test
    void allRetainsProgressThroughAMutableCollectionWithoutRecheckingItsElements() {
        var time = new FakeTime(0);
        var values = new ArrayList<>(List.of(false, false, false));
        var check = new CountedCheck();
        int[] calls = {0};
        var result = tryAwait(() -> {
            if (calls[0] > 0) values.set(calls[0] - 1, true);
            calls[0]++;
            return values;
        }).usingTime(time, time).every(ofNanos(1)).until(all(check::test));

        assertSame(values, result.orThrow());
        assertEquals(4, calls[0]);
        assertEquals(9, check.calls);
        assertEquals(List.of(
                "not all collection elements matched (checked 1): value did not match",
                "not all collection elements matched (checked 2): value did not match",
                "not all collection elements matched (checked 3): value did not match"), mismatches(result));
    }

    @Test
    void anyRecordsHowManyElementsWereAvailableIncludingAnEmptyObservation() {
        var time = new FakeTime(0);
        var values = new ArrayList<Boolean>();
        var check = new CountedCheck();
        int[] calls = {0};
        var result = tryAwait(() -> {
            if (calls[0] > 0) values.add(calls[0] == 3);
            calls[0]++;
            return values;
        }).usingTime(time, time).every(ofNanos(1)).until(any(check::test));

        assertSame(values, result.orThrow());
        assertEquals(4, calls[0]);
        assertEquals(6, check.calls);
        assertEquals(List.of("no collection elements matched (checked 0)",
                "no collection elements matched (checked 1): value did not match",
                "no collection elements matched (checked 2): value did not match"), mismatches(result));
    }

    @Test
    void mapAbsenceChecksKeepTheirFirstMatchPositionInIterationOrder() {
        var time = new FakeTime(0);
        var keys = List.of("first", "second", "third");
        var values = new LinkedHashMap<String, Boolean>();
        keys.forEach(key -> values.put(key, true));
        var check = new CountedCheck();
        int[] calls = {0};
        var result = tryAwait(() -> {
            if (calls[0] > 0) values.put(keys.get(calls[0] - 1), false);
            calls[0]++;
            return values;
        }).usingTime(time, time).every(ofNanos(1)).until(noValue(check::test));

        assertSame(values, result.orThrow());
        assertEquals(4, calls[0]);
        assertEquals(9, check.calls);
        assertEquals(List.of("some map values matched (checked 1)",
                "some map values matched (checked 2)", "some map values matched (checked 3)"), mismatches(result));
    }

    @Test
    void membershipRetainsTheFirstMissingExpectedPositionWithoutExtraComparisons() {
        int[] comparisons = {0};
        class Element {
            final int number;
            Element(int number) { this.number = number; }
            @Override public boolean equals(Object other) {
                comparisons[0]++;
                return other instanceof Element element && number == element.number;
            }
            @Override public int hashCode() { return number; }
        }
        var time = new FakeTime(0);
        var values = new ArrayList<Element>();
        int[] calls = {0};
        var result = tryAwait(() -> {
            switch (++calls[0]) {
                case 1 -> { return null; }
                case 3 -> values.add(new Element(1));
                case 5 -> values.add(new Element(2));
                case 6 -> values.add(new Element(3));
                default -> {}
            }
            return values;
        }).usingTime(time, time).every(ofNanos(1)).until(CollectionConditions.containsAll(
                List.of(new Element(1), new Element(2), new Element(3))));

        assertSame(values, result.orThrow());
        assertEquals(6, calls[0]);
        assertEquals(15, comparisons[0]);
        assertEquals(List.of(1L, 1L, 2L, 1L, 1L), result.attempts().stream().map(group -> group.count()).toList());
        assertEquals(List.of("collection was null",
                "collection did not contain all expected elements; first missing at expected index 0",
                "collection did not contain all expected elements; first missing at expected index 1",
                "collection did not contain all expected elements; first missing at expected index 2"), mismatches(result));
    }

    @Test
    void mapMembershipRetainsMissingKeyValueAndEntryPositions() {
        var expected = new LinkedHashMap<String, String>();
        expected.put("first", "one");
        expected.put("second", "two");
        var entries = new ArrayList<>(expected.entrySet());
        for (String elements : List.of("keys", "values", "entries")) {
            Condition.PreservingCondition<? super Map<String, String>> condition = switch (elements) {
                case "keys" -> MapConditions.containsAllKeys(expected.keySet());
                case "values" -> MapConditions.containsAllValues(expected.values());
                default -> MapConditions.containsAllEntries(expected);
            };
            var time = new FakeTime(0);
            var values = new LinkedHashMap<String, String>();
            int[] calls = {0};
            var result = tryAwait(() -> {
                if (calls[0] > 0) {
                    var entry = entries.get(calls[0] - 1);
                    values.put(entry.getKey(), entry.getValue());
                }
                calls[0]++;
                return values;
            }).usingTime(time, time).every(ofNanos(1)).until(condition);

            assertSame(values, result.orThrow());
            assertEquals(3, calls[0]);
            assertEquals(List.of("map did not contain all expected " + elements + "; first missing at expected index 0",
                    "map did not contain all expected " + elements + "; first missing at expected index 1"), mismatches(result));
        }
    }

    private static List<String> mismatches(AwaitResult<?, ?> result) {
        var mismatches = new ArrayList<String>();
        for (var group : result.attempts()) {
            if (group.first().result() instanceof AttemptStatus.Unsatisfied status) {
                mismatches.add(status.mismatch());
            }
        }
        return mismatches;
    }

    private static final class CountedCheck {
        int calls;

        boolean test(Boolean value) {
            calls++;
            return value;
        }
    }
}

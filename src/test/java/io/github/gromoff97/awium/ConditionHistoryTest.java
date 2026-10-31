package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.CollectionConditions.all;
import static io.github.gromoff97.awium.CollectionConditions.any;
import static io.github.gromoff97.awium.CollectionConditions.containsAll;
import static io.github.gromoff97.awium.CollectionConditions.doesNotContainAny;
import static io.github.gromoff97.awium.CollectionConditions.hasNoDuplicates;
import static io.github.gromoff97.awium.CollectionConditions.sorted;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Condition.Typed;
import static io.github.gromoff97.awium.Conditions.exactlyInstanceOf;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static io.github.gromoff97.awium.MapConditions.containsAllEntries;
import static io.github.gromoff97.awium.MapConditions.containsAllKeys;
import static io.github.gromoff97.awium.MapConditions.containsAllValues;
import static io.github.gromoff97.awium.MapConditions.doesNotContainAnyEntries;
import static io.github.gromoff97.awium.MapConditions.doesNotContainAnyKeys;
import static io.github.gromoff97.awium.MapConditions.doesNotContainAnyValues;
import static io.github.gromoff97.awium.MapConditions.noValue;
import static io.github.gromoff97.awium.ProbeContainers.membershipCollection;
import static io.github.gromoff97.awium.result.Attempt.Unsatisfied;
import static java.lang.Integer.compare;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ConditionHistoryTest {

    private final FakeTime time = new FakeTime(0);
    private int equalityChecks;

    @ParameterizedTest
    @MethodSource("typeChecks")
    void typeChecksRetainActualTypesEvenWhenValuesHaveTheSameText(Typed<Object, String> condition) {
        var values = asList(null, null, new StringBuilder("pending"), new StringBuilder("pending"),
                new StringBuffer("pending"), "ready").iterator();
        var result = tryAwait(values::next).usingTime(time, time).every(ofNanos(1)).until(condition);

        String value = result.orThrow();
        assertEquals("ready", value);
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(2L, 3L, 4L, 5L, 6L);
        assertEquals(List.of("value was null", "value type was java.lang.StringBuilder",
                "value type was java.lang.StringBuilder", "value type was java.lang.StringBuffer"), mismatches(result));
    }

    private static List<Named<Typed<Object, String>>> typeChecks() {
        return List.of(named("instanceOf", instanceOf(String.class)), named("exactlyInstanceOf", exactlyInstanceOf(String.class)));
    }

    @Test
    void sortingRetainsTheFirstOutOfOrderPairWithoutRepeatingComparisons() {
        var values = new ArrayList<>(List.of(3, 1, 2));
        var observations = List.<Supplier<ArrayList<Integer>>>of(() -> null, () -> values,
                change(values, () -> { values.set(0, 1); values.set(1, 3); }),
                change(values, () -> { values.set(1, 2); values.set(2, 3); })).iterator();
        int[] comparisons = {0};
        var result = tryAwait(() -> observations.next().get()).usingTime(time, time).every(ofNanos(1)).until(sorted((left, right) -> {
            comparisons[0]++;
            return compare(left, right);
        }));

        assertSame(values, result.orThrow());
        assertFalse(observations.hasNext());
        assertEquals(5, comparisons[0]);
        assertEquals(List.of("collection was null", "collection was not sorted at indices 0 and 1",
                "collection was not sorted at indices 1 and 2"), mismatches(result));
    }

    @Test
    void duplicatePositionSurvivesMutationWithoutRepeatingEqualityChecks() {
        var values = new ArrayList<>(List.of(new Element(1), new Element(1), new Element(2)));
        var observations = List.<Supplier<ArrayList<Element>>>of(() -> null, () -> values,
                change(values, () -> values.set(1, new Element(2))),
                change(values, () -> values.set(2, new Element(3)))).iterator();
        var result = tryAwait(() -> observations.next().get()).usingTime(time, time).every(ofNanos(1)).until(hasNoDuplicates);

        assertSame(values, result.orThrow());
        assertFalse(observations.hasNext());
        assertEquals(7, equalityChecks);
        assertEquals(List.of("collection was null",
                "duplicate collection element at index 1",
                "duplicate collection element at index 2"), mismatches(result));
    }

    @Test
    void allRetainsProgressThroughAMutableCollectionWithoutRecheckingItsElements() {
        var values = new ArrayList<>(List.of(false, false, false));
        var check = new CountedCheck();
        var observations = List.<Supplier<ArrayList<Boolean>>>of(() -> values,
                change(values, () -> values.set(0, true)), change(values, () -> values.set(1, true)),
                change(values, () -> values.set(2, true))).iterator();
        var result = tryAwait(() -> observations.next().get()).usingTime(time, time).every(ofNanos(1)).until(all(check::test));

        assertSame(values, result.orThrow());
        assertFalse(observations.hasNext());
        assertEquals(9, check.calls);
        assertEquals(List.of(
                "not all collection elements matched (checked 1): value did not match",
                "not all collection elements matched (checked 2): value did not match",
                "not all collection elements matched (checked 3): value did not match"), mismatches(result));
    }

    @Test
    void anyRecordsHowManyElementsWereAvailableIncludingAnEmptyObservation() {
        var values = new ArrayList<Boolean>();
        var check = new CountedCheck();
        var observations = List.<Supplier<ArrayList<Boolean>>>of(() -> values,
                change(values, () -> values.add(false)), change(values, () -> values.add(false)),
                change(values, () -> values.add(true))).iterator();
        var result = tryAwait(() -> observations.next().get()).usingTime(time, time).every(ofNanos(1)).until(any(check::test));

        assertSame(values, result.orThrow());
        assertFalse(observations.hasNext());
        assertEquals(6, check.calls);
        assertEquals(List.of("no collection elements matched (checked 0)",
                "no collection elements matched (checked 1): value did not match",
                "no collection elements matched (checked 2): value did not match"), mismatches(result));
    }

    @Test
    void mapAbsenceChecksKeepTheirFirstMatchPositionInIterationOrder() {
        var keys = List.of("first", "second", "third");
        var values = new LinkedHashMap<String, Boolean>();
        keys.forEach(key -> values.put(key, true));
        var check = new CountedCheck();
        var observations = List.<Supplier<LinkedHashMap<String, Boolean>>>of(() -> values,
                change(values, () -> values.put("first", false)), change(values, () -> values.put("second", false)),
                change(values, () -> values.put("third", false))).iterator();
        var result = tryAwait(() -> observations.next().get()).usingTime(time, time).every(ofNanos(1)).until(noValue(check::test));

        assertSame(values, result.orThrow());
        assertFalse(observations.hasNext());
        assertEquals(9, check.calls);
        assertEquals(List.of("some map values matched (checked 1)",
                "some map values matched (checked 2)", "some map values matched (checked 3)"), mismatches(result));
    }

    @Test
    void membershipRetainsTheFirstMissingExpectedPositionWithoutExtraComparisons() {
        var values = new ArrayList<Element>();
        var observations = List.<Supplier<ArrayList<Element>>>of(() -> null, () -> values,
                change(values, () -> values.add(new Element(1))), () -> values,
                change(values, () -> values.add(new Element(2))), change(values, () -> values.add(new Element(3)))).iterator();
        var result = tryAwait(() -> observations.next().get()).usingTime(time, time).every(ofNanos(1)).until(containsAll(
                List.of(new Element(1), new Element(2), new Element(3))));

        assertSame(values, result.orThrow());
        assertFalse(observations.hasNext());
        assertEquals(15, equalityChecks);
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(1L, 2L, 4L, 5L, 6L);
        assertEquals(List.of("collection was null",
                "collection did not contain all expected elements; first missing at expected index 0",
                "collection did not contain all expected elements; first missing at expected index 1",
                "collection did not contain all expected elements; first missing at expected index 2"), mismatches(result));
    }

    @ParameterizedTest(name = "{displayName}: {0}")
    @MethodSource("requiredMapParts")
    void mapMembershipRetainsMissingKeyValueAndEntryPositions(String elements, PreservingCondition<? super Map<String, String>> condition) {
        var values = new LinkedHashMap<String, String>();
        var observations = List.<Supplier<LinkedHashMap<String, String>>>of(() -> values,
                change(values, () -> values.put("first", "one")), change(values, () -> values.put("second", "two"))).iterator();
        var result = tryAwait(() -> observations.next().get()).usingTime(time, time).every(ofNanos(1)).until(condition);

        assertSame(values, result.orThrow());
        assertFalse(observations.hasNext());
        assertEquals(List.of("map did not contain all expected " + elements + "; first missing at expected index 0",
                "map did not contain all expected " + elements + "; first missing at expected index 1"), mismatches(result));
    }

    private static List<Arguments> requiredMapParts() {
        var expected = orderedEntries();
        return List.of(arguments("keys", containsAllKeys(expected.keySet())), arguments("values", containsAllValues(expected.values())),
                arguments("entries", containsAllEntries(expected)));
    }

    @Test
    void absenceRetainsTheFirstRemainingForbiddenPositionWithoutExtraTraversal() {
        var forbidden = List.of(1, 2, 3);
        var values = new ArrayList<>(forbidden);
        var actual = membershipCollection(values);
        var observations = List.<Supplier<Collection<Integer>>>of(() -> null, () -> actual,
                change(actual, values::removeFirst), () -> actual,
                change(actual, values::removeFirst), change(actual, values::removeFirst)).iterator();
        var result = tryAwait(() -> observations.next().get()).usingTime(time, time).every(ofNanos(1)).until(doesNotContainAny(forbidden));

        assertSame(actual, result.orThrow());
        assertFalse(observations.hasNext());
        verify(actual, times(11)).iterator();
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(1L, 2L, 4L, 5L, 6L);
        assertEquals(List.of("collection was null",
                "collection contained unexpected elements; first at unexpected index 0",
                "collection contained unexpected elements; first at unexpected index 1",
                "collection contained unexpected elements; first at unexpected index 2"), mismatches(result));
    }

    @ParameterizedTest(name = "{displayName}: {0}")
    @MethodSource("forbiddenMapParts")
    void mapAbsenceRetainsRemainingKeyValueAndEntryPositions(String elements, PreservingCondition<? super Map<String, String>> condition) {
        var values = orderedEntries();
        var observations = List.<Supplier<LinkedHashMap<String, String>>>of(() -> values,
                change(values, () -> values.remove("first")), change(values, () -> values.remove("second"))).iterator();
        var result = tryAwait(() -> observations.next().get()).usingTime(time, time).every(ofNanos(1)).until(condition);

        assertSame(values, result.orThrow());
        assertFalse(observations.hasNext());
        assertEquals(List.of("map contained unexpected " + elements + "; first at unexpected index 0",
                "map contained unexpected " + elements + "; first at unexpected index 1"), mismatches(result));
    }

    private static List<Arguments> forbiddenMapParts() {
        var forbidden = orderedEntries();
        return List.of(arguments("keys", doesNotContainAnyKeys(forbidden.keySet())), arguments("values", doesNotContainAnyValues(forbidden.values())),
                arguments("entries", doesNotContainAnyEntries(forbidden)));
    }

    private static LinkedHashMap<String, String> orderedEntries() {
        var entries = new LinkedHashMap<String, String>();
        entries.put("first", "one");
        entries.put("second", "two");
        return entries;
    }

    private static <ACTUAL> Supplier<ACTUAL> change(ACTUAL actual, Runnable mutation) {
        return () -> {
            mutation.run();
            return actual;
        };
    }

    private static List<String> mismatches(AwaitResult<?, ?> result) {
        return attempts(result).stream().filter(Unsatisfied.class::isInstance)
                .map(attempt -> ((Unsatisfied<?>) attempt).mismatch()).toList();
    }

    private final class Element {
        final int number;
        Element(int number) { this.number = number; }
        @Override public boolean equals(Object other) {
            equalityChecks++;
            return other instanceof Element element && number == element.number;
        }
        @Override public int hashCode() { return number; }
    }

    private static final class CountedCheck {
        int calls;

        boolean test(Boolean value) {
            calls++;
            return value;
        }
    }
}

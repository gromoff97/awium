package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.function.CheckedBiPredicate;
import io.github.gromoff97.awium.result.AwaitResult;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.Conditions.atLeast;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.greaterThan;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.allEntries;
import static io.github.gromoff97.awium.MapConditions.allKeys;
import static io.github.gromoff97.awium.MapConditions.allValues;
import static io.github.gromoff97.awium.MapConditions.anyKey;
import static io.github.gromoff97.awium.MapConditions.anyValue;
import static io.github.gromoff97.awium.MapConditions.containsAllKeys;
import static io.github.gromoff97.awium.MapConditions.containsAllValues;
import static io.github.gromoff97.awium.MapConditions.containsOnlyKeys;
import static io.github.gromoff97.awium.MapConditions.doesNotContainAnyKeys;
import static io.github.gromoff97.awium.MapConditions.doesNotContainAnyValues;
import static io.github.gromoff97.awium.MapConditions.doesNotContainKey;
import static io.github.gromoff97.awium.MapConditions.doesNotContainValue;
import static io.github.gromoff97.awium.MapConditions.entryFor;
import static io.github.gromoff97.awium.MapConditions.hasEntry;
import static io.github.gromoff97.awium.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.MapConditions.hasSizeBetween;
import static io.github.gromoff97.awium.MapConditions.noKey;
import static io.github.gromoff97.awium.MapConditions.noValue;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.ProbeContainers.EntryMap;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static java.util.AbstractMap.SimpleImmutableEntry;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Map.Entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapSelectionConditionsTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void expectedAndNarrowingConditionsReturnTheOriginalEntryAndValue() {
        final class ReceiptEntry extends SimpleImmutableEntry<String, Number> {
            ReceiptEntry(String key, Number value) { super(key, value); }
        }

        var value = new BigDecimal("42.00");
        var entry = new ReceiptEntry("receipt", value);
        var equalEntry = new ReceiptEntry("receipt", new BigDecimal("42.00"));
        var actual = new EntryMap<String, Number>(List.of(entry));
        int[] reads = {0};
        Callable<EntryMap<String, Number>> source = () -> {
            reads[0]++;
            return actual;
        };
        var waiting = await(source::call).usingTime(time, time);

        Entry<String, Number> matching = waiting.until(singleEntry(equalTo(equalEntry)));
        ReceiptEntry narrowed = waiting.until(singleEntry(instanceOf(ReceiptEntry.class)));
        Entry<String, Number> byKey = waiting.until(entryFor("receipt")
                .because("the receipt has arrived"));
        BigDecimal selected = waiting.until(valueFor("receipt", instanceOf(BigDecimal.class)));
        AwaitResult<EntryMap<String, Number>, ReceiptEntry> captured =
                diagnostic(waiting).until(singleEntry(instanceOf(ReceiptEntry.class)));

        assertSame(entry, matching);
        assertSame(entry, narrowed);
        assertSame(entry, byKey);
        assertSame(value, selected);
        assertSame(entry, captured.orThrow());
        assertEquals(5, reads[0]);

        var multiple = new EntryMap<String, Number>(List.of(entry, Map.entry("other", 1)));
        var many = await(() -> multiple).usingTime(time, time).upTo(ofNanos(1));
        assertFailure(TIMEOUT, () -> many.until(singleEntry(equalTo(equalEntry))));
        assertFailure(TIMEOUT, () -> many.until(singleEntry(instanceOf(ReceiptEntry.class))));
    }

    @Test
    void pairedEntryPredicatesRetryInsteadOfReturningANonMatchingEntry() {
        var observations = new ArrayDeque<>(List.of(Map.of("key", 1), Map.of("key", 2)));
        var checked = new ArrayList<Entry<String, Integer>>();
        CheckedBiPredicate<String, Integer> predicate = (key, value) -> {
            checked.add(Map.entry(key, value));
            return value == 2;
        };
        var waiting = await(observations::removeFirst).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
        var result = diagnostic(waiting).until(singleEntry(predicate));

        assertEquals(Map.entry("key", 2), result.orThrow());
        assertEquals(2, attempts(result).size());
        assertEquals(List.of(Map.entry("key", 1), Map.entry("key", 2)), checked);
        assertTrue(observations.isEmpty());
    }

    @Test
    void valueForInitializesItsCheckAfterFindingTheKeyAndChecksOnlyThatValue() {
        List<Map<String, Integer>> observations = asList(
                null, Map.of(), Map.of("other", 2), Map.of("key", 1, "other", 2), Map.of("key", 2, "other", 1));
        var source = observations.iterator();
        var events = new ArrayList<String>();
        var checked = new ArrayList<Integer>();
        var outcomes = List.<ConditionResult<Integer>>of(unsatisfied("not ready"), satisfied(2)).iterator();
        var check = conditionPerWait("value is ready", () -> {
            events.add("factory");
            return (Integer value) -> {
                checked.add(value);
                return outcomes.next();
            };
        });
        var result = tryAwait(() -> {
            events.add("source");
            return source.next();
        }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5))
                .until(valueFor("key", check).because("the selected value must be ready"));

        assertEquals(2, result.orThrow());
        assertThat(attempts(result)).extracting(AwaitTestAccess::observation).containsExactlyElementsOf(observations);
        assertThat(events).containsExactly("source", "source", "source", "source", "factory", "source");
        assertEquals(List.of(1, 2), checked);
        var missingGroup = attempts(result).get(1);
        assertSame(missingGroup, result.conditions().getFirst().attempts().get(2L));
        assertEquals("map value: value is ready", result.conditions().getFirst().condition().description());
        assertEquals("key", result.conditions().getFirst().condition().references().getFirst().value());
        assertEquals("the selected value must be ready", result.conditions().getFirst().condition().explanation());
    }

    @Test
    void singleEntryInitializesTheNestedCheckOnlyAfterExtractingTheSoleEntry() {
        List<Map<String, Integer>> observations = asList(
                null, Map.of(), Map.of("key", 2, "other", 1), Map.of("key", 1), Map.of("key", 2));
        var source = observations.iterator();
        var events = new ArrayList<String>();
        var checked = new ArrayList<Integer>();
        var outcomes = List.<ConditionResult<Entry<String, Integer>>>of(unsatisfied("not ready"), satisfied(Map.entry("key", 2))).iterator();
        var check = conditionPerWait("entry is ready", () -> {
            events.add("factory");
            return (Entry<? extends String, ? extends Integer> entry) -> {
                checked.add(entry.getValue());
                return outcomes.next();
            };
        });
        var result = tryAwait(() -> {
            events.add("source");
            return source.next();
        }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5))
                .until(singleEntry(check).because("one ready entry required"));

        assertEquals(Map.entry("key", 2), result.orThrow());
        assertEquals(5, attempts(result).size());
        assertThat(events).containsExactly("source", "source", "source", "source", "factory", "source");
        assertEquals(List.of(1, 2), checked);
        assertEquals("single entry: entry is ready", result.conditions().getFirst().condition().description());
        assertEquals("one ready entry required", result.conditions().getFirst().condition().explanation());
    }

    @Test
    void selectorsReturnEntriesKeysAndValuesWithConcreteTypes() {
        var actual = new LinkedHashMap<String, Integer>();
        actual.put("first", 1);
        actual.put("second", 2);
        Callable<LinkedHashMap<String, Integer>> source = () -> actual;
        var waiting = await(source::call).usingTime(time, time);

        var sole = await(() -> Map.of("second", 2)).usingTime(time, time);
        Entry<String, Integer> selected = sole.until(singleEntry(matches((Entry<String, Integer> entry) -> entry.getValue() == 2)));
        Entry<String, Integer> byKey = waiting.until(entryFor("first"));
        Integer value = waiting.until(valueFor("second"));
        Integer nested = waiting.until(valueFor("second", atLeast(2)));
        String key = sole.until(singleEntry(
                matches(entry -> entry.getKey().startsWith("s")))).getKey();

        assertEquals("second", selected.getKey());
        assertEquals("first", byKey.getKey());
        assertEquals(2, value);
        assertEquals(2, nested);
        assertEquals("second", key);
        assertEquals(2, selected.getValue());
    }

    @Test
    void nullValuesAreDistinguishedFromMissingKeys() {
        var actual = new LinkedHashMap<String[], String>();
        actual.put(new String[] {"nullable"}, null);
        var waiting = await(() -> actual).usingTime(time, time).upTo(ofNanos(1));

        String selected = waiting.until(valueFor(new String[] {"nullable"}));
        assertNull(selected);
        assertNull(diagnostic(waiting).until(valueFor(new String[] {"nullable"})).orThrow());
        assertThat(assertFailure(TIMEOUT, () -> waiting.until(valueFor(new String[] {"missing"}))))
                .hasMessageContaining("map did not contain the expected key");
        assertThat(assertFailure(TIMEOUT, () -> await(() -> (Map<String[], String>) null)
                .usingTime(time, time).upTo(ofNanos(1))
                .until(valueFor(new String[] {"missing"}))))
                .hasMessageContaining("map was null");
    }

    @Test
    void quantifiersAndBulkConditionsPreserveTheConcreteMap() {
        var actual = new LinkedHashMap<>(Map.of("a", 1, "b", 2));
        Callable<LinkedHashMap<String, Integer>> source = () -> actual;
        var waiting = await(source::call).usingTime(time, time);

        assertSame(actual, waiting.until(allEntries((key, value) -> value > 0)));
        assertSame(actual, waiting.until(hasEntry((key, value) -> key.equals("b"))));
        assertSame(actual, waiting.until(hasNoEntry((key, value) -> value < 0)));
        assertSame(actual, waiting.until(allKeys(key -> key.length() == 1)));
        assertSame(actual, waiting.until(anyKey(key -> key.equals("a"))));
        assertSame(actual, waiting.until(allValues(value -> value > 0)));
        assertSame(actual, waiting.until(anyValue(value -> value == 2)));
        assertSame(actual, waiting.until(containsAllKeys(List.of("a", "b"))));
        assertSame(actual, waiting.until(containsOnlyKeys(List.of("b", "a"))));
        assertSame(actual, waiting.until(containsAllValues(List.of(1, 2))));
        assertSame(actual, waiting.until(hasSizeBetween(1, 3)));

        assertInstanceOf(Unsatisfied.class,
                evaluate(doesNotContainKey("a"), actual));
        assertInstanceOf(Unsatisfied.class,
                evaluate(doesNotContainValue(2), actual));
    }

    @ParameterizedTest
    @MethodSource("mismatchingQuantifiersAndBulkChecks")
    void quantifiersAndBulkConditionsRejectMismatches(PreservingCondition<? super LinkedHashMap<String, Integer>> check) {
        var actual = new LinkedHashMap<>(Map.of("a", 1, "b", 2));
        assertInstanceOf(Unsatisfied.class, evaluate(check, actual));
    }

    private static List<PreservingCondition<? super LinkedHashMap<String, Integer>>> mismatchingQuantifiersAndBulkChecks() {
        return List.of(allEntries((String key, Integer value) -> value < 2),
                hasEntry((String key, Integer value) -> value > 2),
                hasNoEntry((String key, Integer value) -> value == 2),
                allKeys(key -> key.equals("a")), anyKey(key -> key.equals("missing")), noKey(key -> key.equals("a")),
                allValues(value -> value < 2), anyValue(value -> value > 2), noValue(value -> value == 2),
                containsAllKeys(List.of("a", "missing")), doesNotContainAnyKeys(List.of("a", "missing")),
                containsOnlyKeys(singletonList("a")), containsAllValues(List.of(1, 3)), doesNotContainAnyValues(List.of(2, 3)));
    }

    @ParameterizedTest
    @MethodSource("matchingAbsenceChecks")
    void absenceChecksAcceptMissingKeysAndValues(PreservingCondition<? super LinkedHashMap<String, Integer>> check) {
        var actual = new LinkedHashMap<>(Map.of("a", 1, "b", 2));
        assertInstanceOf(Satisfied.class, evaluate(check, actual));
    }

    private static List<PreservingCondition<? super LinkedHashMap<String, Integer>>> matchingAbsenceChecks() {
        return List.of(doesNotContainKey("missing"), doesNotContainValue(3));
    }

    @Test
    void selectorsCoverMissingMultipleAndWrongEntries() {
        var actual = new LinkedHashMap<>(Map.of("first", 1, "second", 2));
        var waiting = await(() -> actual).usingTime(time, time).upTo(ofNanos(1));

        var none = assertFailure(TIMEOUT,
                () -> await(() -> Map.of("first", 1)).usingTime(time, time).upTo(ofNanos(1))
                        .until(singleEntry(matches(entry -> entry.getValue() > 2))));
        var many = assertFailure(TIMEOUT,
                () -> waiting.until(singleEntry((key, value) -> value == 2)));
        assertThat(none.getMessage()).contains("value did not match");
        assertThat(many.getMessage()).contains("map size was greater than 1");
        assertFailure(TIMEOUT, () -> waiting.until(valueFor("missing")));
        assertFailure(TIMEOUT, () -> waiting.until(valueFor("first", greaterThan(1))));
        assertThrows(NullPointerException.class, () -> singleEntry(
                (PreservingCondition<Entry<?, ?>>) null));
        assertThrows(NullPointerException.class, () -> allEntries((CheckedBiPredicate<Object, Object>) null));
    }
}

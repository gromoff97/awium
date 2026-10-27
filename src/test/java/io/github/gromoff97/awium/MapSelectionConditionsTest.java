package io.github.gromoff97.awium;

import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapSelectionConditionsTest {

    @Test
    void expectedAndNarrowingConditionsReturnTheOriginalEntryAndValue() {
        record ReceiptEntry(String key, Number value) implements Map.Entry<String, Number> {
            @Override
            public String getKey() { return key; }

            @Override
            public Number getValue() { return value; }

            @Override
            public Number setValue(Number replacement) { throw new UnsupportedOperationException(); }
        }

        var value = new BigDecimal("42.00");
        var entry = new ReceiptEntry("receipt", value);
        var equalEntry = new ReceiptEntry("receipt", new BigDecimal("42.00"));
        var actual = new ProbeContainers.EntryMap<String, Number>(List.of(entry));
        int[] reads = {0};
        java.util.concurrent.Callable<ProbeContainers.EntryMap<String, Number>> source = () -> {
            reads[0]++;
            return actual;
        };
        var time = new FakeTime(0);
        var waiting = await(source::call).usingTime(time, time);

        Map.Entry<String, Number> matching = waiting.until(MapConditions.singleEntry(Conditions.equalTo(equalEntry)));
        ReceiptEntry narrowed = waiting.until(MapConditions.singleEntry(Conditions.instanceOf(ReceiptEntry.class)));
        Map.Entry<String, Number> byKey = waiting.until(MapConditions.entryFor("receipt", Conditions.equalTo(equalEntry)));
        ReceiptEntry narrowedByKey = waiting.until(MapConditions.entryFor("receipt", Conditions.instanceOf(ReceiptEntry.class))
                .because("the receipt has arrived"));
        BigDecimal selected = waiting.until(MapConditions.valueFor("receipt", Conditions.instanceOf(BigDecimal.class)));
        AwaitResult<ProbeContainers.EntryMap<String, Number>, ReceiptEntry> captured =
                waiting.tryUntil(MapConditions.singleEntry(Conditions.instanceOf(ReceiptEntry.class)));

        assertSame(entry, matching);
        assertSame(entry, narrowed);
        assertSame(entry, byKey);
        assertSame(entry, narrowedByKey);
        assertSame(value, selected);
        assertSame(entry, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());
        assertEquals(6, reads[0]);

        var multiple = new ProbeContainers.EntryMap<String, Number>(List.of(entry, Map.entry("other", 1)));
        var many = await(() -> multiple).usingTime(time, time).upTo(ofNanos(1));
        assertFailure(TIMEOUT, () -> many.until(MapConditions.singleEntry(Conditions.equalTo(equalEntry))));
        assertFailure(TIMEOUT, () -> many.until(MapConditions.singleEntry(Conditions.instanceOf(ReceiptEntry.class))));
    }

    @Test
    void entryForInitializesItsCheckAfterFindingTheKeyAndChecksOnlyThatEntry() {
        List<Map<String, Integer>> observations = java.util.Arrays.asList(
                null, Map.of(), Map.of("other", 2), Map.of("key", 1, "other", 2), Map.of("key", 2, "other", 1));
        int[] reads = {0};
        int[] factories = {0};
        var checked = new java.util.ArrayList<Map.Entry<String, Integer>>();
        var check = Conditions.conditionPerWait("entry is ready", () -> {
            factories[0]++;
            return (Map.Entry<String, Integer> entry) -> {
                checked.add(entry);
                return entry.getValue() == 2 ? ConditionResult.satisfied() : ConditionResult.unsatisfied("not ready");
            };
        });
        var time = new FakeTime(0);
        var result = await(() -> {
            if (reads[0] <= 3) assertEquals(0, factories[0]);
            return observations.get(reads[0]++);
        }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5))
                .tryUntil(MapConditions.entryFor("key", check).because("the selected entry must be ready"));

        assertEquals(Map.entry("key", 2), assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        assertEquals(5, result.totalAttempts());
        assertEquals(1, factories[0]);
        assertEquals(List.of(Map.entry("key", 1), Map.entry("key", 2)), checked);
        var missing = result.attempts().get(2);
        assertEquals(MapConditions.entryFor("key").description(), missing.condition().description());
        assertEquals("key", missing.condition().reference().value());
        assertEquals("the selected entry must be ready", missing.condition().explanation());
    }

    @Test
    void singleEntryInitializesTheNestedCheckOnlyAfterExtractingTheSoleEntry() {
        List<Map<String, Integer>> observations = java.util.Arrays.asList(
                null, Map.of(), Map.of("key", 2, "other", 1), Map.of("key", 1), Map.of("key", 2));
        int[] reads = {0};
        int[] factories = {0};
        var checked = new java.util.ArrayList<Integer>();
        var check = Conditions.conditionPerWait("entry is ready", () -> {
            factories[0]++;
            return (Map.Entry<String, Integer> entry) -> {
                checked.add(entry.getValue());
                return entry.getValue() == 2 ? ConditionResult.satisfied() : ConditionResult.unsatisfied("not ready");
            };
        });
        var time = new FakeTime(0);
        var result = await(() -> {
            if (reads[0] <= 3) assertEquals(0, factories[0]);
            return observations.get(reads[0]++);
        }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5))
                .tryUntil(MapConditions.singleEntry(check).because("one ready entry required"));

        assertEquals(Map.entry("key", 2), assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        assertEquals(5, result.totalAttempts());
        assertEquals(1, factories[0]);
        assertEquals(List.of(1, 2), checked);
        var structural = result.attempts().get(2);
        assertEquals(MapConditions.singleEntry.description(), structural.condition().description());
        assertEquals("one ready entry required", structural.condition().explanation());
    }

    @Test
    void selectorsReturnEntriesKeysAndValuesWithConcreteTypes() {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<String, Integer>();
        actual.put("first", 1);
        actual.put("second", 2);
        java.util.concurrent.Callable<LinkedHashMap<String, Integer>> source = () -> actual;
        var waiting = await(source::call).usingTime(pollingTime, pollingTime);

        var sole = await(() -> Map.of("second", 2)).usingTime(pollingTime, pollingTime);
        Map.Entry<String, Integer> selected = sole.until(MapConditions.singleEntry(matches((Map.Entry<String, Integer> entry) -> entry.getValue() == 2)));
        Map.Entry<String, Integer> byKey = waiting.until(MapConditions.entryFor("first"));
        Integer value = waiting.until(MapConditions.valueFor("second"));
        Integer nested = waiting.until(MapConditions.valueFor("second", Conditions.atLeast(2)));
        String key = sole.until(MapConditions.singleEntry(
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
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<String[], String>();
        actual.put(new String[] {"nullable"}, null);
        var waiting = await(() -> actual).usingTime(pollingTime, pollingTime).upTo(ofNanos(1));

        String selected = waiting.until(MapConditions.valueFor(new String[] {"nullable"}));
        assertNull(selected);
        assertNull(assertInstanceOf(AwaitResult.Satisfied.class,
                waiting.tryUntil(MapConditions.valueFor(new String[] {"nullable"}))).result());
        assertTrue(assertFailure(TIMEOUT, () -> waiting.until(MapConditions.valueFor(new String[] {"missing"})))
                .getMessage().contains("map did not contain the expected key"));
        assertTrue(assertFailure(TIMEOUT, () -> await(() -> (Map<String[], String>) null)
                .usingTime(pollingTime, pollingTime).upTo(ofNanos(1))
                .until(MapConditions.valueFor(new String[] {"missing"})))
                .getMessage().contains("map was null"));
    }

    @Test
    void quantifiersAndBulkConditionsPreserveTheConcreteMap() {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<>(Map.of("a", 1, "b", 2));
        java.util.concurrent.Callable<LinkedHashMap<String, Integer>> source = () -> actual;
        var waiting = await(source::call).usingTime(pollingTime, pollingTime);

        assertSame(actual, waiting.until(MapConditions.allEntries((key, value) -> value > 0)));
        assertSame(actual, waiting.until(MapConditions.hasEntry((key, value) -> key.equals("b"))));
        assertSame(actual, waiting.until(MapConditions.hasNoEntry((key, value) -> value < 0)));
        assertSame(actual, waiting.until(MapConditions.allKeys(key -> key.length() == 1)));
        assertSame(actual, waiting.until(MapConditions.anyKey(key -> key.equals("a"))));
        assertSame(actual, waiting.until(MapConditions.allValues(value -> value > 0)));
        assertSame(actual, waiting.until(MapConditions.anyValue(value -> value == 2)));
        assertSame(actual, waiting.until(MapConditions.containsAllKeys(List.of("a", "b"))));
        assertSame(actual, waiting.until(MapConditions.containsOnlyKeys(List.of("b", "a"))));
        assertSame(actual, waiting.until(MapConditions.containsAllValues(List.of(1, 2))));
        assertSame(actual, waiting.until(MapConditions.hasSizeBetween(1, 3)));

        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.doesNotContainKey("a"), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.doesNotContainValue(2), actual).getClass());
    }

    @Test
    void quantifiersAndBulkConditionsCoverTheirUnsatisfiedBranches() {
        var actual = new LinkedHashMap<>(Map.of("a", 1, "b", 2));

        assertEquals(Unsatisfied.class, evaluate(MapConditions.allEntries(
                (String key, Integer value) -> value < 2), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.hasEntry(
                (String key, Integer value) -> value > 2), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.hasNoEntry(
                (String key, Integer value) -> value == 2), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.allKeys(
                key -> key.equals("a")), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.anyKey(
                key -> key.equals("missing")), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.noKey(
                key -> key.equals("a")), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.allValues(
                value -> value < 2), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.anyValue(
                value -> value > 2), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.noValue(
                value -> value == 2), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.containsAllKeys(List.of("a", "missing")), actual).getClass());
        assertEquals(Satisfied.class,
                evaluate(MapConditions.doesNotContainKey("missing"), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                MapConditions.doesNotContainAnyKeys(List.of("a", "missing")), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.containsOnlyKeys(java.util.Collections.singletonList("a")), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.containsAllValues(List.of(1, 3)), actual).getClass());
        assertEquals(Satisfied.class,
                evaluate(MapConditions.doesNotContainValue(3), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.doesNotContainAnyValues(List.of(2, 3)), actual).getClass());
    }

    @Test
    void selectorsCoverMissingMultipleAndWrongEntries() {
        var actual = new LinkedHashMap<>(Map.of("first", 1, "second", 2));
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).upTo(ofNanos(1));

        var none = assertFailure(TIMEOUT,
                () -> await(() -> Map.of("first", 1)).usingTime(time, time).upTo(ofNanos(1))
                        .until(MapConditions.singleEntry(matches(entry -> entry.getValue() > 2))));
        var many = assertFailure(TIMEOUT,
                () -> waiting.until(MapConditions.singleEntry((key, value) -> value == 2)));
        assertTrue(none.getMessage().contains("value did not match"));
        assertTrue(many.getMessage().contains("map size was greater than 1"));
        assertFailure(TIMEOUT, () -> waiting.until(MapConditions.valueFor("missing")));
        assertFailure(TIMEOUT, () -> waiting.until(MapConditions.valueFor("first", Conditions.greaterThan(1))));
        assertThrows(NullPointerException.class, () -> MapConditions.singleEntry(
                (Condition.PreservingCondition<Map.Entry<?, ?>>) null));
        assertThrows(NullPointerException.class, () -> MapConditions.allEntries((CheckedBiPredicate<Object, Object>) null));
    }
}

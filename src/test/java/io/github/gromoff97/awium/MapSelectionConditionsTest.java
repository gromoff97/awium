package io.github.gromoff97.awium;

import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.Conditions.await;
import static io.github.gromoff97.awium.CollectionConditions.await;
import static io.github.gromoff97.awium.OptionalConditions.await;
import static io.github.gromoff97.awium.MapConditions.await;
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
        var waiting = await(source).usingTime(time, time);

        Map.Entry<String, Number> matching = waiting.until(MapConditions.singleEntry(Conditions.equalTo(equalEntry)));
        ReceiptEntry narrowed = waiting.until(MapConditions.singleEntry(Conditions.instanceOf(ReceiptEntry.class)));
        BigDecimal selected = waiting.until(MapConditions.valueFor("receipt", Conditions.instanceOf(BigDecimal.class)));
        AwaitResult<ProbeContainers.EntryMap<String, Number>, ReceiptEntry> captured =
                waiting.tryUntil(MapConditions.singleEntry(Conditions.instanceOf(ReceiptEntry.class)));

        assertSame(entry, matching);
        assertSame(entry, narrowed);
        assertSame(value, selected);
        assertSame(entry, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());
        assertEquals(4, reads[0]);
    }

    @Test
    void selectorsReturnEntriesKeysAndValuesWithConcreteTypes() {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<String, Integer>();
        actual.put("first", 1);
        actual.put("second", 2);
        java.util.concurrent.Callable<LinkedHashMap<String, Integer>> source = () -> actual;
        var waiting = await(source).usingTime(pollingTime, pollingTime);

        Map.Entry<String, Integer> selected = waiting.until(MapConditions.singleEntry(matches((Map.Entry<String, Integer> entry) -> entry.getValue() == 2)));
        Map.Entry<String, Integer> byKey = waiting.until(MapConditions.entryFor("first"));
        Integer value = waiting.until(MapConditions.valueFor("second"));
        Integer nested = waiting.until(MapConditions.valueFor("second", Conditions.atLeast(2)));
        String key = waiting.until(MapConditions.singleEntry(
                matches(entry -> entry.getKey().startsWith("f")))).getKey();

        assertEquals("second", selected.getKey());
        assertEquals("first", byKey.getKey());
        assertEquals(2, value);
        assertEquals(2, nested);
        assertEquals("first", key);
        assertEquals(2, selected.getValue());
    }

    @Test
    void nullValuesAreDistinguishedFromMissingKeys() {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<String[], String>();
        actual.put(new String[] {"nullable"}, null);
        var waiting = await(() -> actual).usingTime(pollingTime, pollingTime).upTo(ofNanos(1));

        String selected = waiting.until(MapConditions.valueFor(new String[] {"nullable"})
                .because("one array key with a null value"));
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
        var waiting = await(source).usingTime(pollingTime, pollingTime);

        assertSame(actual, waiting.until(MapConditions.allEntries((key, value) -> value > 0)));
        assertSame(actual, waiting.until(MapConditions.anyEntry((key, value) -> key.equals("b"))));
        assertSame(actual, waiting.until(MapConditions.noEntry((key, value) -> value < 0)));
        assertSame(actual, waiting.until(MapConditions.allKeys(key -> key.length() == 1)));
        assertSame(actual, waiting.until(MapConditions.anyKey(key -> key.equals("a"))));
        assertSame(actual, waiting.until(MapConditions.allValues(value -> value > 0)));
        assertSame(actual, waiting.until(MapConditions.anyValue(value -> value == 2)));
        assertSame(actual, waiting.until(MapConditions.containsAllKeys(List.of("a", "b"))));
        assertSame(actual, waiting.until(MapConditions.containsOnlyKeys(List.of("b", "a"))));
        assertSame(actual, waiting.until(MapConditions.containsAllValues(List.of(1, 2))));
        assertSame(actual, waiting.until(MapConditions.sizeBetween(1, 3)));

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
        assertEquals(Unsatisfied.class, evaluate(MapConditions.anyEntry(
                (String key, Integer value) -> value > 2), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.noEntry(
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
    void onlyReturnsTheValueOfTheSoleExpectedKey() {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<>(Map.of("only", 42));
        Integer value = await(() -> actual).usingTime(pollingTime, pollingTime).until(MapConditions.onlyValueFor("only"));

        assertEquals(42, value);
    }

    @Test
    void selectorsCoverMissingMultipleAndWrongEntries() {
        var actual = new LinkedHashMap<>(Map.of("first", 1, "second", 2));
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).upTo(ofNanos(1));

        var none = assertFailure(TIMEOUT,
                () -> waiting.until(MapConditions.singleEntry(matches(entry -> entry.getValue() > 2))));
        var many = assertFailure(TIMEOUT,
                () -> waiting.until(MapConditions.singleEntry(matches(entry -> entry.getValue() > 0))));
        assertTrue(none.getMessage().contains("value did not match"));
        assertTrue(many.getMessage().contains("more than one value matched"));
        assertFailure(TIMEOUT, () -> waiting.until(MapConditions.valueFor("missing")));
        assertFailure(TIMEOUT, () -> waiting.until(MapConditions.valueFor("first", Conditions.greaterThan(1))));
        assertFailure(TIMEOUT, () -> waiting.until(MapConditions.onlyValueFor("first")));
        assertFailure(TIMEOUT, () -> await(() -> Map.of("first", 1))
                .usingTime(time, time).upTo(ofNanos(1)).until(MapConditions.onlyValueFor("other")));
        assertFailure(TIMEOUT, () -> await(() -> (Map<String, Integer>) null)
                .usingTime(time, time).upTo(ofNanos(1)).until(MapConditions.onlyValueFor("first")));
        assertThrows(NullPointerException.class, () -> MapConditions.singleEntry(
                (Condition.PreservingCondition<Map.Entry<?, ?>>) null));
        assertThrows(NullPointerException.class, () -> MapConditions.allEntries((CheckedBiPredicate<Object, Object>) null));
    }
}

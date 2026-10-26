package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Await.MapSource;
import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.Conditions.matches;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        MapSource<ProbeContainers.EntryMap<String, Number>> source = () -> {
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
        MapSource<LinkedHashMap<String, Integer>> source = () -> actual;
        var waiting = await(source).usingTime(pollingTime, pollingTime);

        Map.Entry<String, Integer> selected = waiting.until(MapConditions.singleEntry(matches(entry -> entry.getValue() == 2)));
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

        String selected = await(() -> actual).usingTime(pollingTime, pollingTime)
                .until(MapConditions.valueFor(new String[] {"nullable"}).because("one array key with a null value"));
        assertNull(selected);
        assertEquals(Satisfied.class,
                evaluate(MapConditions.valueFor(new String[] {"nullable"}), actual).getClass());
        assertEquals("map did not contain the expected key",
                ((Unsatisfied<?>) evaluate(MapConditions.valueFor(new String[] {"missing"}), actual)).mismatch());
        assertEquals("map was null",
                ((Unsatisfied<?>) evaluate(MapConditions.valueFor(new String[] {"missing"}), null)).mismatch());
    }

    @Test
    void quantifiersAndBulkConditionsPreserveTheConcreteMap() {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<>(Map.of("a", 1, "b", 2));
        MapSource<LinkedHashMap<String, Integer>> source = () -> actual;
        var waiting = await(source).usingTime(pollingTime, pollingTime);

        assertSame(actual, waiting.until(MapConditions.allEntries((key, value) -> value > 0)));
        assertSame(actual, waiting.until(MapConditions.anyEntry((key, value) -> key.equals("b"))));
        assertSame(actual, waiting.until(MapConditions.noEntry((key, value) -> value < 0)));
        assertSame(actual, waiting.until(MapConditions.allKeys(key -> key.length() == 1)));
        assertSame(actual, waiting.until(MapConditions.anyKey(key -> key.equals("a"))));
        assertSame(actual, waiting.until(MapConditions.allValues(value -> value > 0)));
        assertSame(actual, waiting.until(MapConditions.anyValue(value -> value == 2)));
        assertSame(actual, waiting.until(MapConditions.containsKeys(List.of("a", "b"))));
        assertSame(actual, waiting.until(MapConditions.containsOnlyKeys(List.of("b", "a"))));
        assertSame(actual, waiting.until(MapConditions.containsValues(List.of(1, 2))));
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
                evaluate(MapConditions.containsKeys(List.of("a", "missing")), actual).getClass());
        assertEquals(Satisfied.class,
                evaluate(MapConditions.doesNotContainKey("missing"), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                MapConditions.doesNotContainKeys(List.of("a", "missing")), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.containsOnlyKeys(java.util.Collections.singletonList("a")), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.containsValues(List.of(1, 3)), actual).getClass());
        assertEquals(Satisfied.class,
                evaluate(MapConditions.doesNotContainValue(3), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.doesNotContainValues(List.of(2, 3)), actual).getClass());
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

        var none = evaluate(MapConditions.singleEntry(matches(entry -> entry.getValue() > 2)), actual);
        var many = evaluate(MapConditions.singleEntry(matches(entry -> entry.getValue() > 0)), actual);
        assertEquals(Unsatisfied.class, none.getClass());
        assertEquals(Unsatisfied.class, many.getClass());
        assertEquals("value did not match", ((Unsatisfied<?>) none).mismatch());
        assertEquals("more than one value matched", ((Unsatisfied<?>) many).mismatch());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.valueFor("missing"), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                MapConditions.valueFor(
                        "first", Conditions.greaterThan(1)), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                MapConditions.onlyValueFor("first"), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                MapConditions.onlyValueFor("other"),
                Map.of("first", 1)).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                MapConditions.onlyValueFor("first"), null).getClass());
        assertThrows(NullPointerException.class, () -> MapConditions.singleEntry(
                (Condition.PreservingCondition<Map.Entry<?, ?>>) null));
        assertThrows(NullPointerException.class, () -> MapConditions.allEntries(null));
    }
}

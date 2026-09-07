package io.github.gromoff97.awium;

import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import io.github.gromoff97.awium.Source.MapSource;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapSelectionConditionsTest {

    @Test
    void selectorsReturnEntriesKeysAndValuesWithConcreteTypes() {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<String, Integer>();
        actual.put("first", 1);
        actual.put("second", 2);
        MapSource<LinkedHashMap<String, Integer>> source = () -> actual;

        Map.Entry<String, Integer> selected = await(source).usingTime(pollingTime, pollingTime).until(MapConditions.singleEntry((key, value) -> value == 2));
        Map.Entry<String, Integer> byKey = await(source).usingTime(pollingTime, pollingTime).until(MapConditions.entryFor("first"));
        Integer value = await(source).usingTime(pollingTime, pollingTime).until(MapConditions.valueFor("second"));
        Integer nested = await(source).usingTime(pollingTime, pollingTime).until(MapConditions.valueFor("second", Conditions.atLeast(2)));
        String key = await(source).usingTime(pollingTime, pollingTime).until(MapConditions.singleEntry(
                (candidate, valueCandidate) -> candidate.startsWith("f"))).getKey();
        Integer singleValue = await(source).usingTime(pollingTime, pollingTime).until(MapConditions.singleEntry(
                (keyCandidate, candidate) -> candidate == 2)).getValue();

        assertEquals("second", selected.getKey());
        assertEquals("first", byKey.getKey());
        assertEquals(2, value);
        assertEquals(2, nested);
        assertEquals("first", key);
        assertEquals(2, singleValue);
    }

    @Test
    void nullValuesAreDistinguishedFromMissingKeys() throws Exception {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<String, String>();
        actual.put("nullable", null);

        assertNull(await((MapSource<LinkedHashMap<String, String>>) () -> actual).usingTime(pollingTime, pollingTime).until(MapConditions.valueFor("nullable")));
        assertEquals(Satisfied.class,
                evaluate(MapConditions.<String, String>valueFor("nullable"), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.<String, String>valueFor("missing"), actual).getClass());
    }

    @Test
    void quantifiersAndBulkConditionsPreserveTheConcreteMap() throws Exception {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<>(Map.of("a", 1, "b", 2));
        MapSource<LinkedHashMap<String, Integer>> source = () -> actual;

        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(MapConditions.allEntries((key, value) -> value > 0)));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(MapConditions.anyEntry((key, value) -> key.equals("b"))));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(MapConditions.noEntry((key, value) -> value < 0)));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(MapConditions.allKeys(key -> key.length() == 1)));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(MapConditions.anyKey(key -> key.equals("a"))));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(MapConditions.allValues(value -> value > 0)));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(MapConditions.anyValue(value -> value == 2)));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(MapConditions.containsKeys("a", "b")));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(MapConditions.containsOnlyKeys("b", "a")));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(MapConditions.containsValues(1, 2)));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(MapConditions.sizeBetween(1, 3)));

        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.doesNotContainKeys("a"), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.doesNotContainValues(2), actual).getClass());
    }

    @Test
    void quantifiersAndBulkConditionsCoverTheirUnsatisfiedBranches() throws Exception {
        var actual = new LinkedHashMap<>(Map.of("a", 1, "b", 2));

        assertEquals(Unsatisfied.class, evaluate(MapConditions.allEntries(
                (String key, Integer value) -> value < 2), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.anyEntry(
                (String key, Integer value) -> value > 2), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.noEntry(
                (String key, Integer value) -> value == 2), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.<String, Integer>allKeys(
                key -> key.equals("a")), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.<String, Integer>anyKey(
                key -> key.equals("missing")), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.<String, Integer>noKey(
                key -> key.equals("a")), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.<String, Integer>allValues(
                value -> value < 2), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.<String, Integer>anyValue(
                value -> value > 2), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(MapConditions.<String, Integer>noValue(
                value -> value == 2), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.containsKeys("a", "missing"), actual).getClass());
        assertEquals(Satisfied.class,
                evaluate(MapConditions.doesNotContainKeys("missing"), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                MapConditions.doesNotContainKeys("a", "missing"), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.containsOnlyKeys("a"), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.containsValues(1, 3), actual).getClass());
        assertEquals(Satisfied.class,
                evaluate(MapConditions.doesNotContainValues(3), actual).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.doesNotContainValues(2, 3), actual).getClass());
    }

    @Test
    void onlyReturnsTheValueOfTheSoleExpectedKey() {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<>(Map.of("only", 42));
        Integer value = await((MapSource<LinkedHashMap<String, Integer>>) () -> actual).usingTime(pollingTime, pollingTime).until(MapConditions.onlyValueFor("only"));

        assertEquals(42, value);
    }

    @Test
    void selectorsCoverMissingMultipleAndWrongEntries() throws Exception {
        var actual = new LinkedHashMap<>(Map.of("first", 1, "second", 2));

        var none = evaluate(MapConditions.<String, Integer>singleEntry((key, value) -> value > 2), actual);
        var many = evaluate(MapConditions.<String, Integer>singleEntry((key, value) -> value > 0), actual);
        assertEquals(Unsatisfied.class, none.getClass());
        assertEquals(Unsatisfied.class, many.getClass());
        assertEquals("no map entry matched", ((Unsatisfied<?>) none).mismatch());
        assertEquals("more than one map entry matched", ((Unsatisfied<?>) many).mismatch());
        assertEquals(Unsatisfied.class,
                evaluate(MapConditions.<String, Integer>valueFor("missing"), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                MapConditions.<String, Integer>valueFor(
                        "first", Conditions.greaterThan(1)), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                MapConditions.<String, Integer>onlyValueFor("first"), actual).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                MapConditions.<String, Integer>onlyValueFor("other"),
                Map.of("first", 1)).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                MapConditions.<String, Integer>onlyValueFor("first"), null).getClass());
        assertThrows(NullPointerException.class, () -> MapConditions.singleEntry((java.util.function.BiPredicate<Object, Object>) null));
        assertThrows(NullPointerException.class, () -> MapConditions.allEntries(null));
    }
}

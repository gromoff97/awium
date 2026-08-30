package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditions.Conditions;
import io.github.gromoff97.awium.conditions.MapConditions;
import io.github.gromoff97.awium.sources.Source.MapSource;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.gromoff97.awium.fluent.Await.await;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.UNSATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapSelectionConditionsTest {

    @Test
    void selectorsReturnEntriesKeysAndValuesWithConcreteTypes() {
        var actual = new LinkedHashMap<String, Integer>();
        actual.put("first", 1);
        actual.put("second", 2);
        MapSource<LinkedHashMap<String, Integer>> source = () -> actual;

        Map.Entry<String, Integer> selected = await(source).until(MapConditions.singleEntry((key, value) -> value == 2));
        Map.Entry<String, Integer> byKey = await(source).until(MapConditions.entryFor("first"));
        Integer value = await(source).until(MapConditions.valueFor("second"));
        Integer nested = await(source).until(MapConditions.valueFor("second", Conditions.atLeast(2)));
        String key = await(source).until(MapConditions.singleEntry(
                (candidate, valueCandidate) -> candidate.startsWith("f"))).getKey();
        Integer singleValue = await(source).until(MapConditions.singleEntry(
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
        var actual = new LinkedHashMap<String, String>();
        actual.put("nullable", null);

        assertNull(await((MapSource<LinkedHashMap<String, String>>) () -> actual).until(MapConditions.valueFor("nullable")));
        assertEquals(SATISFIED,
                evaluate(MapConditions.<String, String>valueFor("nullable"), actual).status());
        assertEquals(UNSATISFIED,
                evaluate(MapConditions.<String, String>valueFor("missing"), actual).status());
    }

    @Test
    void quantifiersAndBulkConditionsPreserveTheConcreteMap() throws Exception {
        var actual = new LinkedHashMap<>(Map.of("a", 1, "b", 2));
        MapSource<LinkedHashMap<String, Integer>> source = () -> actual;

        assertSame(actual, await(source).until(MapConditions.allEntries((key, value) -> value > 0)));
        assertSame(actual, await(source).until(MapConditions.anyEntry((key, value) -> key.equals("b"))));
        assertSame(actual, await(source).until(MapConditions.noEntry((key, value) -> value < 0)));
        assertSame(actual, await(source).until(MapConditions.allKeys(key -> key.length() == 1)));
        assertSame(actual, await(source).until(MapConditions.anyKey(key -> key.equals("a"))));
        assertSame(actual, await(source).until(MapConditions.allValues(value -> value > 0)));
        assertSame(actual, await(source).until(MapConditions.anyValue(value -> value == 2)));
        assertSame(actual, await(source).until(MapConditions.containsKeys("a", "b")));
        assertSame(actual, await(source).until(MapConditions.containsOnlyKeys("b", "a")));
        assertSame(actual, await(source).until(MapConditions.containsValues(1, 2)));
        assertSame(actual, await(source).until(MapConditions.sizeBetween(1, 3)));

        assertEquals(UNSATISFIED,
                evaluate(MapConditions.doesNotContainKeys("a"), actual).status());
        assertEquals(UNSATISFIED,
                evaluate(MapConditions.doesNotContainValues(2), actual).status());
    }

    @Test
    void quantifiersAndBulkConditionsCoverTheirUnsatisfiedBranches() throws Exception {
        var actual = new LinkedHashMap<>(Map.of("a", 1, "b", 2));

        assertEquals(UNSATISFIED, evaluate(MapConditions.allEntries(
                (String key, Integer value) -> value < 2), actual).status());
        assertEquals(UNSATISFIED, evaluate(MapConditions.anyEntry(
                (String key, Integer value) -> value > 2), actual).status());
        assertEquals(UNSATISFIED, evaluate(MapConditions.noEntry(
                (String key, Integer value) -> value == 2), actual).status());
        assertEquals(UNSATISFIED, evaluate(MapConditions.<String, Integer>allKeys(
                key -> key.equals("a")), actual).status());
        assertEquals(UNSATISFIED, evaluate(MapConditions.<String, Integer>anyKey(
                key -> key.equals("missing")), actual).status());
        assertEquals(UNSATISFIED, evaluate(MapConditions.<String, Integer>noKey(
                key -> key.equals("a")), actual).status());
        assertEquals(UNSATISFIED, evaluate(MapConditions.<String, Integer>allValues(
                value -> value < 2), actual).status());
        assertEquals(UNSATISFIED, evaluate(MapConditions.<String, Integer>anyValue(
                value -> value > 2), actual).status());
        assertEquals(UNSATISFIED, evaluate(MapConditions.<String, Integer>noValue(
                value -> value == 2), actual).status());
        assertEquals(UNSATISFIED,
                evaluate(MapConditions.containsKeys("a", "missing"), actual).status());
        assertEquals(SATISFIED,
                evaluate(MapConditions.doesNotContainKeys("missing"), actual).status());
        assertEquals(UNSATISFIED, evaluate(
                MapConditions.doesNotContainKeys("a", "missing"), actual).status());
        assertEquals(UNSATISFIED,
                evaluate(MapConditions.containsOnlyKeys("a"), actual).status());
        assertEquals(UNSATISFIED,
                evaluate(MapConditions.containsValues(1, 3), actual).status());
        assertEquals(SATISFIED,
                evaluate(MapConditions.doesNotContainValues(3), actual).status());
        assertEquals(UNSATISFIED,
                evaluate(MapConditions.doesNotContainValues(2, 3), actual).status());
    }

    @Test
    void onlyReturnsTheValueOfTheSoleExpectedKey() {
        var actual = new LinkedHashMap<>(Map.of("only", 42));
        Integer value = await((MapSource<LinkedHashMap<String, Integer>>) () -> actual).until(MapConditions.onlyValueFor("only"));

        assertEquals(42, value);
    }

    @Test
    void selectorsCoverMissingMultipleAndWrongEntries() throws Exception {
        var actual = new LinkedHashMap<>(Map.of("first", 1, "second", 2));

        assertEquals(UNSATISFIED, evaluate(MapConditions.<String, Integer>singleEntry(
                (key, value) -> value > 2), actual).status());
        assertEquals(UNSATISFIED, evaluate(MapConditions.<String, Integer>singleEntry(
                (key, value) -> value > 0), actual).status());
        assertEquals(UNSATISFIED,
                evaluate(MapConditions.<String, Integer>valueFor("missing"), actual).status());
        assertEquals(UNSATISFIED, evaluate(
                MapConditions.<String, Integer>valueFor(
                        "first", Conditions.greaterThan(1)), actual).status());
        assertEquals(UNSATISFIED, evaluate(
                MapConditions.<String, Integer>onlyValueFor("first"), actual).status());
        assertEquals(UNSATISFIED, evaluate(
                MapConditions.<String, Integer>onlyValueFor("other"),
                Map.of("first", 1)).status());
        assertEquals(UNSATISFIED, evaluate(
                MapConditions.<String, Integer>onlyValueFor("first"), null).status());
        assertThrows(NullPointerException.class, () -> MapConditions.singleEntry(null));
        assertThrows(NullPointerException.class, () -> MapConditions.allEntries(null));
    }
}

package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.conditions.ComparableCondition;
import io.github.gromoff97.awium.conditioning.conditions.MapCondition;
import io.github.gromoff97.awium.sources.Source.MapSource;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNSATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MapSelectionConditionsTest {

    @Test
    void selectorsReturnEntriesKeysAndValuesWithConcreteTypes() {
        var actual = new LinkedHashMap<String, Integer>();
        actual.put("first", 1);
        actual.put("second", 2);
        MapSource<LinkedHashMap<String, Integer>> source = () -> actual;

        Map.Entry<String, Integer> selected = await(source).until(MapCondition.singleEntry((key, value) -> value == 2));
        Map.Entry<String, Integer> byKey = await(source).until(MapCondition.entry("first"));
        Integer value = await(source).until(MapCondition.valueFor("second"));
        Integer nested = await(source).until(MapCondition.valueFor("second", ComparableCondition.atLeast(2)));
        String key = await(source).until(MapCondition.singleKey(candidate -> candidate.startsWith("f")));
        Integer singleValue = await(source).until(MapCondition.singleValue(candidate -> candidate == 2));

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

        assertNull(await((MapSource<LinkedHashMap<String, String>>) () -> actual).until(MapCondition.valueFor("nullable")));
        assertEquals(SATISFIED, MapCondition.<String, String>valueFor("nullable").evaluate(actual).status());
        assertEquals(UNSATISFIED, MapCondition.<String, String>valueFor("missing").evaluate(actual).status());
    }

    @Test
    void quantifiersAndBulkConditionsPreserveTheConcreteMap() throws Exception {
        var actual = new LinkedHashMap<>(Map.of("a", 1, "b", 2));
        MapSource<LinkedHashMap<String, Integer>> source = () -> actual;

        assertSame(actual, await(source).until(MapCondition.allEntries((key, value) -> value > 0)));
        assertSame(actual, await(source).until(MapCondition.anyEntry((key, value) -> key.equals("b"))));
        assertSame(actual, await(source).until(MapCondition.noEntry((key, value) -> value < 0)));
        assertSame(actual, await(source).until(MapCondition.allKeys(key -> key.length() == 1)));
        assertSame(actual, await(source).until(MapCondition.anyValue(value -> value == 2)));
        assertSame(actual, await(source).until(MapCondition.containsKeys("a", "b")));
        assertSame(actual, await(source).until(MapCondition.containsOnlyKeys("b", "a")));
        assertSame(actual, await(source).until(MapCondition.containsValues(1, 2)));
        assertSame(actual, await(source).until(MapCondition.entryCountBetween(1, 3)));

        assertEquals(UNSATISFIED, MapCondition.doesNotContainKeys("a")
                .delegate().evaluate(actual).status());
        assertEquals(UNSATISFIED, MapCondition.doesNotContainValues(2)
                .delegate().evaluate(actual).status());
    }

    @Test
    void onlyReturnsTheValueOfTheSoleExpectedKey() {
        var actual = new LinkedHashMap<>(Map.of("only", 42));
        Integer value = await((MapSource<LinkedHashMap<String, Integer>>) () -> actual).until(MapCondition.onlyValueFor("only"));

        assertEquals(42, value);
    }
}

package consumer.map;

import io.github.gromoff97.awium.Await.MapSource;
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.map.MapConditions.containsAllEntries;
import static io.github.gromoff97.awium.conditions.map.MapConditions.containsAllKeys;
import static io.github.gromoff97.awium.conditions.map.MapConditions.containsAllValues;
import static io.github.gromoff97.awium.conditions.map.MapConditions.containsKey;
import static io.github.gromoff97.awium.conditions.map.MapConditions.containsOnlyKeys;
import static io.github.gromoff97.awium.conditions.map.MapConditions.containsValue;
import static io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAnyEntries;
import static io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAnyKeys;
import static io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAnyValues;
import static io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainKey;
import static io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainValue;
import static io.github.gromoff97.awium.conditions.map.MapConditions.hasEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.map.MapConditions.allKeys;
import static io.github.gromoff97.awium.conditions.map.MapConditions.allValues;
import static io.github.gromoff97.awium.conditions.map.MapConditions.anyKey;
import static io.github.gromoff97.awium.conditions.map.MapConditions.anyValue;
import static io.github.gromoff97.awium.conditions.map.MapConditions.hasSizeBetween;

public final class ContentConsumer {
    private ContentConsumer() {}

    public static List<Map<String[], String[]>> arrayOperands(Map<String[], String[]> actual, String[] key, String[] value) {
        var keyMatch = await(() -> actual).until(containsKey(key));
        var valueMatch = await(() -> actual).until(containsValue(value));
        var noKey = await(() -> actual).until(doesNotContainKey(new String[] {"missing"}));
        var noValue = await(() -> actual).until(doesNotContainValue(new String[] {"missing"}));
        return List.of(keyMatch, valueMatch, noKey, noValue);
    }

    public static List<LinkedHashMap<String, String>> entryChecks(LinkedHashMap<String, String> actual) {
        MapSource<LinkedHashMap<String, String>> source = () -> actual;
        var waiting = await(source);
        var exact = waiting.until(hasEntry("ready", "1").because("ready entry required"));
        var nested = waiting.until(hasEntry(matches(entry -> !entry.getValue().equals("0"))));
        var direct = waiting.until(hasEntry(entry -> entry.getKey().startsWith("ready") && entry.getValue().startsWith("1")));
        var recordedDirect = tryAwait(source).until(hasEntry((Entry<String, String> entry) -> !entry.getValue().equals("0"))
                .because("positive entry required")).orThrow();
        var recordedNested = tryAwait(source).until(hasEntry(matches((Entry<String, String> entry) -> !entry.getValue().equals("0")))
                .because("positive entry required")).orThrow();
        var recordedExact = tryAwait(source).until(hasEntry("ready", "1").because("ready entry required")).orThrow();
        return List.of(exact, nested, direct, recordedDirect, recordedNested, recordedExact);
    }

    public static List<LinkedHashMap<String, String>> capturedEntries(LinkedHashMap<String, String> actual) {
        var observations = await(() -> actual).until(captured(
                hasEntry("ready", "1"), hasEntry(entry -> entry.getValue().equals("2"))));
        observations.getFirst().firstEntry().getValue().length();
        return observations;
    }

    public static List<LinkedHashMap<String, String>> absenceChecks(LinkedHashMap<String, String> actual) {
        var direct = tryAwait(() -> actual).until(hasNoEntry(entry ->
                entry.getKey().startsWith("failed") && entry.getValue().startsWith("1"))).orThrow();
        var exact = tryAwait(() -> actual).until(hasNoEntry("failed", "1")).orThrow();
        var explained = tryAwait(() -> actual).until(hasNoEntry((Entry<String, String> entry) ->
                entry.getKey().startsWith("failed") && entry.getValue().startsWith("1"))
                .because("failed entry must be absent")).orThrow();
        var observed = await(() -> actual).until(hasNoEntry(entry ->
                entry.getKey().startsWith("failed") && entry.getValue().startsWith("1")));
        return List.of(direct, exact, explained, observed);
    }

    public static List<LinkedHashMap<String, String>> emptyExpectations(LinkedHashMap<String, String> actual) {
        var conditions = List.<PreservingCondition<? super Map<String, String>>>of(
                containsAllEntries(Map.of()), doesNotContainAnyEntries(Map.of()),
                containsAllKeys(List.of()), doesNotContainAnyKeys(List.of()),
                containsAllValues(List.of()), doesNotContainAnyValues(List.of()));
        return conditions.stream().flatMap(condition -> {
            var observed = await(() -> actual).until(condition);
            var recorded = tryAwait(() -> actual).until(condition).orThrow();
            return List.of(observed, recorded).stream();
        }).toList();
    }

    public static List<LinkedHashMap<String, Integer>> quantifiersAndBulkChecks(LinkedHashMap<String, Integer> actual) {
        var waiting = await(() -> actual);
        return List.of(waiting.until(allEntries((key, value) -> value > 0)),
                waiting.until(hasEntry((key, value) -> key.equals("b"))),
                waiting.until(hasNoEntry((key, value) -> value < 0)),
                waiting.until(allKeys(key -> key.length() == 1)),
                waiting.until(anyKey(key -> key.equals("a"))),
                waiting.until(allValues(value -> value > 0)),
                waiting.until(anyValue(value -> value == 2)),
                waiting.until(containsAllKeys(List.of("a", "b"))),
                waiting.until(containsOnlyKeys(List.of("b", "a"))),
                waiting.until(containsAllValues(List.of(1, 2))),
                waiting.until(hasSizeBetween(1, 3)));
    }
}

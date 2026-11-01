package consumer.map;

import io.github.gromoff97.awium.Await.MapSource;
import io.github.gromoff97.awium.result.AwaitResult;
import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.common.Conditions.atLeast;
import static io.github.gromoff97.awium.conditions.common.Conditions.captured;
import static io.github.gromoff97.awium.conditions.common.Conditions.equalTo;
import static io.github.gromoff97.awium.conditions.common.Conditions.instanceOf;
import static io.github.gromoff97.awium.conditions.common.Conditions.matches;
import static io.github.gromoff97.awium.conditions.map.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.map.MapConditions.entryFor;
import static io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;

public final class SelectionConsumer {
    private SelectionConsumer() {}

    public record User(boolean active) {}

    public static final class ReceiptEntry extends SimpleImmutableEntry<String, Number> {
        public ReceiptEntry(String key, Number value) { super(key, value); }
    }

    public static Entry<String, Number> comparedEntry(Map<String, Number> actual, ReceiptEntry expected) {
        return await(() -> actual).until(singleEntry(equalTo(expected)));
    }

    public static ReceiptEntry narrowedEntry(Map<String, Number> actual) {
        var check = singleEntry(instanceOf(ReceiptEntry.class));
        return await(() -> actual).until(check);
    }

    public static ReceiptEntry recordedNarrowedEntry(Map<String, Number> actual) {
        return tryAwait(() -> actual).until(singleEntry(instanceOf(ReceiptEntry.class))).orThrow();
    }

    public static Entry<String, Number> explainedEntry(Map<String, Number> actual) {
        return await(() -> actual).until(entryFor("receipt").because("the receipt has arrived"));
    }

    public static BigDecimal narrowedValue(Map<String, Number> actual) {
        return await(() -> actual).until(valueFor("receipt", instanceOf(BigDecimal.class)));
    }

    public static List<Integer> selectedValues(Map<String, Integer> actual) {
        var waiting = await(() -> actual);
        return List.of(waiting.until(singleEntry(matches((Entry<String, Integer> entry) -> entry.getValue() == 2))).getValue().intValue(),
                waiting.until(entryFor("second")).getValue().intValue(),
                waiting.until(valueFor("second")).intValue(),
                waiting.until(valueFor("second", atLeast(2))).intValue());
    }

    public static String selectedKey(Map<String, Integer> actual) {
        return await(() -> actual).until(singleEntry(matches(entry -> entry.getKey().startsWith("s")))).getKey();
    }

    public static String nullableValue(Map<String[], String> actual) {
        return await(() -> actual).until(valueFor(new String[] {"nullable"}));
    }

    public static String recordedNullableValue(Map<String[], String> actual) {
        return tryAwait(() -> actual).until(valueFor(new String[] {"nullable"})).orThrow();
    }

    public static List<LinkedHashMap<String, User>> entryPredicates(LinkedHashMap<String, User> actual) {
        MapSource<LinkedHashMap<String, User>> source = () -> actual;
        var all = await(source).until(allEntries(entry -> entry.getValue().active()));
        var explained = await(source).until(allEntries((Entry<String, User> entry) -> entry.getValue().active())
                .because("all users must be active"));
        var recorded = tryAwait(source).until(allEntries(entry -> !entry.getKey().isBlank() && entry.getValue().active())).orThrow();
        return List.of(all, explained, recorded);
    }

    public static List<Entry<String, User>> singleEntryPredicates(LinkedHashMap<String, User> actual) {
        var selected = await(() -> actual).until(singleEntry(entry -> entry.getValue().active()));
        var recorded = tryAwait(() -> actual).until(singleEntry(entry -> !entry.getKey().isBlank() && entry.getValue().active())).orThrow();
        return List.of(selected, recorded);
    }

    public static List<LinkedHashMap<String, User>> capturedEntryPredicates(LinkedHashMap<String, User> actual) {
        var observations = await(() -> actual).until(captured(
                allEntries(entry -> entry.getValue().active()), allEntries(entry -> entry.getValue().active())));
        observations.getFirst().firstEntry().getValue().active();
        return observations;
    }

    public static List<LinkedHashMap<String, User>> capturedSingleEntries(LinkedHashMap<String, User> actual) {
        var observations = await(() -> actual).until(captured(
                singleEntry(entry -> entry.getValue().active()), singleEntry(entry -> entry.getValue().active())));
        observations.getFirst().firstEntry().getValue().active();
        return observations;
    }

    public static Entry<String, User> replacedValue(LinkedHashMap<String, User> actual, User replacement) {
        return await(() -> actual).until(singleEntry((Entry<String, User> entry) -> {
            entry.setValue(replacement);
            return entry.getValue().active();
        }).because("the sole user must be active"));
    }

    public static AwaitResult<LinkedHashMap<String, User>, Entry<String, User>> recordedReplacement(
            LinkedHashMap<String, User> actual, User replacement) {
        return tryAwait(() -> actual).until(singleEntry((Entry<String, User> entry) -> {
            entry.setValue(replacement);
            return entry.getValue().active();
        }).because("the sole user must be active"));
    }

    public static boolean covariantSingleEntry(Map<? extends String, ? extends User> actual) {
        return await(() -> actual).until(singleEntry((String key, User value) -> value.active())
                .because("user must be active")).getValue().active();
    }

    public static Map<? extends String, ? extends User> covariantAllEntries(Map<? extends String, ? extends User> actual) {
        return await(() -> actual).until(allEntries(entry -> entry.getValue().active()));
    }

    public static List<Integer> mapWithDifferentIterableElements() {
        var both = new MapWithUsers();
        var waiting = await(() -> both);
        return List.of(waiting.until(singleEntry).getValue().intValue(),
                waiting.until(valueFor("key")).intValue(),
                waiting.until(singleEntry((key, value) -> true)).getValue().intValue(),
                waiting.until(valueFor("key", value -> value != null)).intValue(),
                tryAwait(() -> both).until(singleEntry).orThrow().getValue().intValue());
    }

    private static final class MapWithUsers extends AbstractMap<String, Integer> implements Iterable<User> {
        @Override public Set<Entry<String, Integer>> entrySet() { return Set.of(Map.entry("key", 10)); }
        @Override public Iterator<User> iterator() { return List.of(new User(true)).iterator(); }
    }
    public static int nullableKeyLength() {
        String missing = null;
        var actual = java.util.Collections.singletonMap(missing, "ready");
        var selected = await(() -> actual).until(valueFor(missing));
        return selected.length();
    }

}

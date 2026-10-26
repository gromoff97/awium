package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.Condition.Reference;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.IntPredicate;

import static io.github.gromoff97.awium.Condition.required;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.compose;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.nonEmpty;
import static io.github.gromoff97.awium.ConditionSupport.snapshot;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.selected;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static io.github.gromoff97.awium.ConditionSupport.validateRange;
import static io.github.gromoff97.awium.ValueMatching.containsAll;
import static io.github.gromoff97.awium.ValueMatching.containsAny;
import static java.util.Objects.deepEquals;
import static io.github.gromoff97.awium.ValueMatching.exactly;
import static io.github.gromoff97.awium.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.ValueMatching.sameDistinctElements;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;

public final class MapConditions {

    public static final SelectedCondition<Map<?, ?>> singleEntry = selected("map has a single entry",
            actual -> ConditionSupport.single("map", actual == null ? null : actual.entrySet(), ConditionResult::satisfied));
    public static final PreservingCondition<Map<?, ?>> empty = preserving("map is empty", "map was not empty", Map::isEmpty);
    public static final PreservingCondition<Map<?, ?>> nonEmpty = preserving("map is not empty", "map was empty", actual -> !actual.isEmpty());

    private MapConditions() {}

    public static PreservingCondition<Map<?, ?>> size(int expected) {
        return sized(expected, actual -> actual == expected,
                "map size is " + expected);
    }

    public static PreservingCondition<Map<?, ?>> sizeIsNot(int unexpected) {
        return sized(unexpected, actual -> actual != unexpected,
                "map size is not " + unexpected);
    }

    public static PreservingCondition<Map<?, ?>> sizeGreaterThan(int lowerBound) {
        return sized(lowerBound, actual -> actual > lowerBound,
                "map size is greater than " + lowerBound);
    }

    public static PreservingCondition<Map<?, ?>> sizeAtLeast(int lowerBound) {
        return sized(lowerBound, actual -> actual >= lowerBound,
                "map size is at least " + lowerBound);
    }

    public static PreservingCondition<Map<?, ?>> sizeLessThan(int upperBound) {
        return sized(upperBound, actual -> actual < upperBound,
                "map size is less than " + upperBound);
    }

    public static PreservingCondition<Map<?, ?>> sizeAtMost(int upperBound) {
        return sized(upperBound, actual -> actual <= upperBound,
                "map size is at most " + upperBound);
    }

    public static PreservingCondition<Map<?, ?>> sizeBetween(int lowerBound, int upperBound) {
        validateRange(lowerBound, upperBound, "size");
        return sized(lowerBound, actual -> actual >= lowerBound && actual <= upperBound,
                "map size is between " + lowerBound + " and " + upperBound);
    }

    /** Selects the only entry whose key and value match, retaining the source's entry type. */
    public static <KEY, VALUE> SelectedCondition<Map<? extends KEY, ? extends VALUE>> singleEntry(
            CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return singleEntry(Conditions.matches((Entry<? extends KEY, ? extends VALUE> entry) ->
                predicate.test(entry.getKey(), entry.getValue())));
    }

    public static <KEY, VALUE> SelectedCondition<Map<? extends KEY, ? extends VALUE>> singleEntry(
            PreservingCondition<? super Entry<? extends KEY, ? extends VALUE>> nested) {
        return new SelectedCondition<>(ConditionSupport.single("map", required(nested).definition, Map::entrySet,
                () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    public static <KEY, VALUE, RESULT> Condition<Map<? extends KEY, ? extends VALUE>, RESULT> singleEntry(
            Condition<? super Entry<? extends KEY, ? extends VALUE>, RESULT> nested) {
        return ConditionSupport.single("map", required(nested), Map::entrySet,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <KEY, VALUE> SelectedCondition<Map<? super KEY, ? super VALUE>> singleEntry(
            ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return new SelectedCondition<>(ConditionSupport.single("map", required(nested).definition, Map::entrySet,
                () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> allEntries(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map entries match", "not all map entries matched",
                actual -> matchesAll(actual.entrySet(),
                        entry -> predicate.test(entry.getKey(), entry.getValue())));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> anyEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map entry matches", "no map entry matched",
                actual -> matchesAny(actual.entrySet(),
                        entry -> predicate.test(entry.getKey(), entry.getValue())));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> noEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map entry matches", "a map entry matched",
                actual -> !matchesAny(actual.entrySet(),
                        entry -> predicate.test(entry.getKey(), entry.getValue())));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> allKeys(CheckedPredicate<? super KEY> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map keys match", "not all map keys matched", actual -> matchesAll(actual.keySet(), predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> anyKey(CheckedPredicate<? super KEY> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map key matches", "no map key matched", actual -> matchesAny(actual.keySet(), predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> noKey(CheckedPredicate<? super KEY> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map key matches", "a map key matched", actual -> !matchesAny(actual.keySet(), predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> allValues(CheckedPredicate<? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map values match", "not all map values matched", actual -> matchesAll(actual.values(), predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> anyValue(CheckedPredicate<? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map value matches", "no map value matched", actual -> matchesAny(actual.values(), predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> noValue(CheckedPredicate<? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map value matches", "a map value matched", actual -> !matchesAny(actual.values(), predicate));
    }

    public static <KEY> MapValueCondition<Map<? super KEY, ?>> valueFor(KEY key) {
        return new MapValueCondition<>(ConditionSupport.condition("map contains the expected key", expectedReference(key), actual -> value(actual, key)));
    }

    /** Selects the key's value if it matches, retaining the source's value type. */
    public static <KEY, VALUE> MapValueCondition<Map<? super KEY, ? extends VALUE>> valueFor(KEY key,
            CheckedPredicate<? super VALUE> predicate) {
        return valueFor(key, Conditions.matches(predicate));
    }

    public static <KEY, VALUE, RESULT> Condition<Map<? super KEY, ? extends VALUE>, RESULT> valueFor(KEY key,
            Condition<? super VALUE, RESULT> nested) {
        return compose("map value ", required(nested),
                valueFor(key).definition, actual -> value(actual, key),
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <KEY, VALUE> MapValueCondition<Map<? super KEY, ? extends VALUE>> valueFor(KEY key,
            PreservingCondition<? super VALUE> nested) {
        return new MapValueCondition<>(compose("map value ", required(nested).definition,
                valueFor(key).definition, actual -> value(actual, key),
                () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    public static <KEY, EXPECTED> MapValueCondition<Map<? super KEY, ? super EXPECTED>> valueFor(KEY key,
            ComparisonCondition<EXPECTED> nested) {
        return new MapValueCondition<>(compose("map value ", required(nested).definition,
                valueFor(key).definition, actual -> value(actual, key),
                () -> ConditionAwaitAttempt.bind(nested.definition)));
    }

    public static <KEY> SelectedCondition<Map<? super KEY, ?>> entryFor(KEY key) {
        return new SelectedCondition<>(ConditionSupport.condition("map contains the expected key", expectedReference(key), actual -> findEntry(actual, key)));
    }

    public static <KEY> MapValueCondition<Map<? super KEY, ?>> onlyValueFor(KEY key) {
        return new MapValueCondition<>(ConditionSupport.condition("map contains only the expected key", expectedReference(key), actual ->
                ConditionSupport.single("map", actual == null ? null : actual.entrySet(), entry ->
                        deepEquals(entry.getKey(), key) ? satisfied(entry.getValue())
                                : unsatisfied("map contained a different key"))));
    }

    /** Matches one key, including a collection or array as that single key. */
    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsKey(KEY expected) {
        return containsKeys(singletonList(expected));
    }

    /** Matches when the single unexpected key is absent. */
    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainKey(KEY unexpected) {
        return doesNotContainKeys(singletonList(unexpected));
    }

    /** Matches one value, including a collection or array as that single value. */
    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsValue(VALUE expected) {
        return containsValues(singletonList(expected));
    }

    /** Matches when the single unexpected value is absent. */
    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainValue(VALUE unexpected) {
        return doesNotContainValues(singletonList(unexpected));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsKeys(Iterable<? extends KEY> expected) {
        var keys = nonEmpty(snapshot(expected, "expected keys"), "expected keys");
        return preserving("map contains all expected keys", "map did not contain all expected keys", expectedReference(keys),
                actual -> containsAll(actual.keySet(), keys, Objects::deepEquals));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainKeys(Iterable<? extends KEY> unexpected) {
        var keys = nonEmpty(snapshot(unexpected, "unexpected keys"), "unexpected keys");
        return preserving("map does not contain unexpected keys", "map contained an unexpected key", unexpectedReference(keys),
                actual -> !containsAny(actual.keySet(), keys, Objects::deepEquals));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsOnlyKeys(Iterable<? extends KEY> expected) {
        var keys = snapshot(expected, "expected keys");
        return preserving("map contains only the expected keys", "map did not contain only the expected keys", expectedReference(keys),
                actual -> sameDistinctElements(actual.keySet(), keys));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsValues(Iterable<? extends VALUE> expected) {
        var values = nonEmpty(snapshot(expected, "expected values"), "expected values");
        return preserving("map contains all expected values", "map did not contain all expected values", expectedReference(values),
                actual -> containsAll(actual.values(), values, Objects::deepEquals));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainValues(Iterable<? extends VALUE> unexpected) {
        var values = nonEmpty(snapshot(unexpected, "unexpected values"), "unexpected values");
        return preserving("map does not contain unexpected values", "map contained an unexpected value", unexpectedReference(values),
                actual -> !containsAny(actual.values(), values, Objects::deepEquals));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsEntries(KEY key, VALUE value) {
        return containsEntries(singletonMap(key, value));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainEntries(KEY key, VALUE value) {
        return doesNotContainEntries(singletonMap(key, value));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = nonEmpty(snapshotEntries(expected), "expected entries");
        return preserving("map contains all expected entries", "map did not contain all expected entries", expectedReference(entries),
                actual -> containsAll(actual.entrySet(), entries,
                        MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAllEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        var entries = nonEmpty(snapshotEntries(unexpected), "expected entries");
        return preserving("map does not contain all expected entries", "map contained all expected entries", expectedReference(entries),
                actual -> !containsAll(actual.entrySet(), entries,
                        MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAnyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = nonEmpty(snapshotEntries(expected), "expected entries");
        return preserving("map contains an expected entry", "map did not contain an expected entry", expectedReference(entries),
                actual -> containsAny(actual.entrySet(), entries, MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = nonEmpty(snapshotEntries(expected), "expected entries");
        return preserving("map does not contain an expected entry", "map contained an expected entry", expectedReference(entries),
                actual -> !containsAny(actual.entrySet(), entries, MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsExactlyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = snapshotEntries(expected);
        return preserving("map contains exactly the expected entries", "map did not contain exactly the expected entries", expectedReference(entries),
                actual -> exactContent(actual, entries));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainExactlyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = snapshotEntries(expected);
        return preserving("map does not contain exactly the expected entries", "map contained exactly the expected entries", expectedReference(entries),
                actual -> !exactContent(actual, entries));
    }

    private static <VALUE> ConditionResult<VALUE> value(Map<?, ? extends VALUE> actual, Object key) {
        return findEntry(actual, key).mapSatisfied(Entry::getValue);
    }

    private static <KEY, VALUE> ConditionResult<Entry<KEY, VALUE>> findEntry(Map<KEY, VALUE> actual, Object key) {
        if (actual != null) {
            for (Entry<KEY, VALUE> entry : actual.entrySet()) {
                if (deepEquals(entry.getKey(), key)) {
                    return satisfied(entry);
                }
            }
        }
        return unsatisfied(actual == null ? "map was null" : "map did not contain the expected key");
    }

    private static PreservingCondition<Map<?, ?>> sized(int bound, IntPredicate matches,
            String description) {
        return ConditionSupport.sized("map", "size", bound, matches, description, Map::size);
    }

    private static <ENTRIES extends Map<?, ?>> PreservingCondition<ENTRIES> preserving(String description, String mismatch,
            CheckedPredicate<? super ENTRIES> matches) {
        return preservingNonNull("map", description, mismatch, matches);
    }

    private static <ENTRIES extends Map<?, ?>> PreservingCondition<ENTRIES> preserving(String description, String mismatch,
            Reference<?> reference, CheckedPredicate<? super ENTRIES> matches) {
        return preservingNonNull("map", description, mismatch, reference, matches);
    }

    private static boolean exactContent(Map<?, ?> actual, List<? extends Entry<?, ?>> expected) throws Throwable {
        return actual.size() == expected.size()
                && exactly(actual.entrySet().iterator(), expected, MapConditions::entryMatches);
    }

    // Preserve encounter order and distinct entries without calling user equals/hashCode.
    private static List<? extends Entry<?, ?>> snapshotEntries(Map<?, ?> values) {
        return requireNonNull(values, "expected entries must not be null")
                .entrySet().stream().map(SimpleImmutableEntry::new).toList();
    }

    private static boolean entryMatches(Entry<?, ?> actual, Entry<?, ?> expected) {
        return deepEquals(actual.getKey(), expected.getKey()) && deepEquals(actual.getValue(), expected.getValue());
    }

}

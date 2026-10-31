package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.ConditionSupport.Quantifier;
import io.github.gromoff97.awium.function.CheckedBiPredicate;
import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

import static io.github.gromoff97.awium.Condition.InputCondition;
import static io.github.gromoff97.awium.Condition.Typed;
import static io.github.gromoff97.awium.ConditionSupport.compose;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.quantified;
import static io.github.gromoff97.awium.ConditionSupport.selected;
import static io.github.gromoff97.awium.ConditionSupport.single;
import static io.github.gromoff97.awium.ConditionSupport.sized;
import static io.github.gromoff97.awium.ConditionSupport.snapshot;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static io.github.gromoff97.awium.ConditionSupport.validateRange;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.ValueMatching.containingAll;
import static io.github.gromoff97.awium.ValueMatching.containingNone;
import static io.github.gromoff97.awium.ValueMatching.firstMissingExpectedIndex;
import static io.github.gromoff97.awium.ValueMatching.firstPresentExpectedIndex;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.ValueMatching.sameDistinctElements;
import static io.github.gromoff97.awium.ValueMatching.sameElementsInAnyOrder;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;

public final class MapConditions {

    public static final SelectedCondition<Map<?, ?>> singleEntry = selected("map has a single entry",
            MapConditions::selectSingleEntry);
    public static final PreservingCondition<Map<?, ?>> empty = mapCondition("map is empty", "map was not empty", Map::isEmpty);
    public static final PreservingCondition<Map<?, ?>> nonEmpty = mapCondition("map is not empty", "map was empty", actual -> !actual.isEmpty());

    private MapConditions() {}

    public static PreservingCondition<Map<?, ?>> hasSize(int expected) {
        return mapSize(expected, actual -> actual == expected, "is " + expected);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeNotEqualTo(int unexpected) {
        return mapSize(unexpected, actual -> actual != unexpected, "is not " + unexpected);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeGreaterThan(int lowerBound) {
        return mapSize(lowerBound, actual -> actual > lowerBound, "is greater than " + lowerBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeAtLeast(int lowerBound) {
        return mapSize(lowerBound, actual -> actual >= lowerBound, "is at least " + lowerBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeLessThan(int upperBound) {
        return mapSize(upperBound, actual -> actual < upperBound, "is less than " + upperBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeAtMost(int upperBound) {
        return mapSize(upperBound, actual -> actual <= upperBound, "is at most " + upperBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeBetween(int lowerBound, int upperBound) {
        validateRange(lowerBound, upperBound, "size");
        return mapSize(lowerBound, actual -> actual >= lowerBound && actual <= upperBound,
                "is between " + lowerBound + " and " + upperBound);
    }

    public static <KEY, VALUE> SelectedCondition<Map<KEY, VALUE>> singleEntry(
            CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return singleEntry(matches(predicate));
    }

    public static <KEY, VALUE> SelectedCondition<Map<? extends KEY, ? extends VALUE>> singleEntry(
            CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return new SelectedCondition<>(compose("single entry", entryCheck(predicate), singleEntry));
    }

    public static <KEY, VALUE> SelectedCondition<Map<KEY, VALUE>> singleEntry(
            PreservingCondition<? super Entry<KEY, VALUE>> nested) {
        return new SelectedCondition<>(compose("single entry", nested, singleEntry));
    }

    public static <KEY, VALUE, RESULT> Typed<Map<? extends KEY, ? extends VALUE>, RESULT> singleEntry(
            Typed<? super Entry<? extends KEY, ? extends VALUE>, RESULT> nested) {
        return compose("single entry", nested, singleEntry);
    }

    public static <KEY, VALUE> SelectedCondition<Map<? super KEY, ? super VALUE>> singleEntry(
            ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return new SelectedCondition<>(compose("single entry", nested, singleEntry));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allEntries(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return allEntries(matches(predicate));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> allEntries(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return quantified("map", "entries", Quantifier.ALL, entryCheck(predicate), Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allEntries(InputCondition<? super Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.ALL, nested, Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> allEntries(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.ALL, nested, Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasEntry(KEY key, VALUE value) {
        var expected = new SimpleImmutableEntry<>(key, value);
        return mapCondition("map contains expected entry", "map did not contain expected entry", expectedReference(expected),
                actual -> matchesAny(actual.entrySet(), entry -> entryMatches(entry, expected)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasEntry(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return hasEntry(matches(predicate));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> hasEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return quantified("map", "entries", Quantifier.ANY, entryCheck(predicate), Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasEntry(InputCondition<? super Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.ANY, nested, Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasEntry(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.ANY, nested, Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasNoEntry(KEY key, VALUE value) {
        var unexpected = new SimpleImmutableEntry<>(key, value);
        return mapCondition("map does not contain unexpected entry", "map contained unexpected entry", unexpectedReference(unexpected),
                actual -> !matchesAny(actual.entrySet(), entry -> entryMatches(entry, unexpected)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasNoEntry(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return hasNoEntry(matches(predicate));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> hasNoEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return quantified("map", "entries", Quantifier.NONE, entryCheck(predicate), Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasNoEntry(InputCondition<? super Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.NONE, nested, Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasNoEntry(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.NONE, nested, Map::entrySet);
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> allKeys(CheckedPredicate<? super KEY> predicate) {
        return allKeys(matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> allKeys(InputCondition<? super KEY> nested) {
        return quantified("map", "keys", Quantifier.ALL, nested, Map::keySet);
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> allKeys(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "keys", Quantifier.ALL, nested, Map::keySet);
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> anyKey(CheckedPredicate<? super KEY> predicate) {
        return anyKey(matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> anyKey(InputCondition<? super KEY> nested) {
        return quantified("map", "keys", Quantifier.ANY, nested, Map::keySet);
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> anyKey(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "keys", Quantifier.ANY, nested, Map::keySet);
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> noKey(CheckedPredicate<? super KEY> predicate) {
        return noKey(matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> noKey(InputCondition<? super KEY> nested) {
        return quantified("map", "keys", Quantifier.NONE, nested, Map::keySet);
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> noKey(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "keys", Quantifier.NONE, nested, Map::keySet);
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> allValues(CheckedPredicate<? super VALUE> predicate) {
        return allValues(matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> allValues(InputCondition<? super VALUE> nested) {
        return quantified("map", "values", Quantifier.ALL, nested, Map::values);
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> allValues(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "values", Quantifier.ALL, nested, Map::values);
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> anyValue(CheckedPredicate<? super VALUE> predicate) {
        return anyValue(matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> anyValue(InputCondition<? super VALUE> nested) {
        return quantified("map", "values", Quantifier.ANY, nested, Map::values);
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> anyValue(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "values", Quantifier.ANY, nested, Map::values);
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> noValue(CheckedPredicate<? super VALUE> predicate) {
        return noValue(matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> noValue(InputCondition<? super VALUE> nested) {
        return quantified("map", "values", Quantifier.NONE, nested, Map::values);
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> noValue(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "values", Quantifier.NONE, nested, Map::values);
    }

    public static <KEY> MapValueCondition<Map<? super KEY, ?>> valueFor(KEY key) {
        return new MapValueCondition<>(new Typed<>("map contains the expected key", new Reference<>("Key", key), actual -> value(actual, key)));
    }

    public static <KEY, VALUE> MapValueCondition<Map<? super KEY, ? extends VALUE>> valueFor(KEY key,
            CheckedPredicate<? super VALUE> predicate) {
        return valueFor(key, matches(predicate));
    }

    public static <KEY, VALUE, RESULT> Typed<Map<? super KEY, ? extends VALUE>, RESULT> valueFor(KEY key,
            Typed<? super VALUE, RESULT> nested) {
        return compose("map value", nested, valueFor(key));
    }

    public static <KEY, CHECKED> MapValueCondition<Map<? super KEY, ? extends CHECKED>> valueFor(KEY key,
            PreservingCondition<? super CHECKED> nested) {
        return new MapValueCondition<>(compose("map value", nested, valueFor(key)));
    }

    public static <KEY, EXPECTED> MapValueCondition<Map<? super KEY, ? super EXPECTED>> valueFor(KEY key,
            ComparisonCondition<EXPECTED> nested) {
        return new MapValueCondition<>(compose("map value", nested, valueFor(key)));
    }

    public static <KEY> SelectedCondition<Map<? super KEY, ?>> entryFor(KEY key) {
        return new SelectedCondition<>(new Typed<>("map contains the expected key", new Reference<>("Key", key), actual -> findEntry(actual, key)));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsKey(KEY expected) {
        return mapCondition("map contains expected key", "map did not contain expected key", expectedReference(expected),
                actual -> matchesAny(actual.keySet(), key -> deepEquals(key, expected)));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainKey(KEY unexpected) {
        return mapCondition("map does not contain unexpected key", "map contained unexpected key", unexpectedReference(unexpected),
                actual -> !matchesAny(actual.keySet(), key -> deepEquals(key, unexpected)));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsValue(VALUE expected) {
        return mapCondition("map contains expected value", "map did not contain expected value", expectedReference(expected),
                actual -> matchesAny(actual.values(), value -> deepEquals(value, expected)));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainValue(VALUE unexpected) {
        return mapCondition("map does not contain unexpected value", "map contained unexpected value", unexpectedReference(unexpected),
                actual -> !matchesAny(actual.values(), value -> deepEquals(value, unexpected)));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsAllKeys(Iterable<? extends KEY> expected) {
        var keys = snapshot(expected, "expected keys");
        return containingAll("map", "keys", keys, Map::keySet, Objects::deepEquals);
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainAnyKeys(Iterable<? extends KEY> unexpected) {
        var keys = snapshot(unexpected, "unexpected keys");
        return containingNone("map", "keys", keys, Map::keySet, Objects::deepEquals);
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsOnlyKeys(Iterable<? extends KEY> expected) {
        var keys = snapshot(expected, "expected keys");
        return mapCondition("map contains only the expected keys", "map did not contain only the expected keys", expectedReference(keys),
                actual -> sameDistinctElements(actual.keySet(), keys));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsAllValues(Iterable<? extends VALUE> expected) {
        var values = snapshot(expected, "expected values");
        return containingAll("map", "values", values, Map::values, Objects::deepEquals);
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainAnyValues(Iterable<? extends VALUE> unexpected) {
        var values = snapshot(unexpected, "unexpected values");
        return containingNone("map", "values", values, Map::values, Objects::deepEquals);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAllEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = snapshotEntries(expected, "expected entries");
        return containingAll("map", "entries", entries, Map::entrySet, MapConditions::entryMatches);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAllEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        var entries = snapshotEntries(unexpected, "unexpected entries");
        return mapCondition("map does not contain all unexpected entries", "map contained all unexpected entries", unexpectedReference(entries),
                actual -> firstMissingExpectedIndex(actual.entrySet(), entries, MapConditions::entryMatches) >= 0);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAnyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = snapshotEntries(expected, "expected entries");
        return mapCondition("map contains an expected entry", "map did not contain an expected entry", expectedReference(entries),
                actual -> firstPresentExpectedIndex(actual.entrySet(), entries, MapConditions::entryMatches) >= 0);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAnyEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        var entries = snapshotEntries(unexpected, "unexpected entries");
        return containingNone("map", "entries", entries, Map::entrySet, MapConditions::entryMatches);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsExactlyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = snapshotEntries(expected, "expected entries");
        return mapCondition("map contains exactly the expected entries", "map did not contain exactly the expected entries", expectedReference(entries),
                actual -> exactContent(actual, entries));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainExactlyEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        var entries = snapshotEntries(unexpected, "unexpected entries");
        return mapCondition("map does not contain exactly the unexpected entries", "map contained exactly the unexpected entries", unexpectedReference(entries),
                actual -> !exactContent(actual, entries));
    }

    private static <KEY, VALUE> PreservingCondition<Entry<? extends KEY, ? extends VALUE>> entryCheck(
            CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return matches(entry -> predicate.test(entry.getKey(), entry.getValue()));
    }

    private static <KEY, VALUE> ConditionResult<Entry<KEY, VALUE>> selectSingleEntry(Map<KEY, VALUE> actual) {
        return single("map", actual == null ? null : actual.entrySet());
    }

    private static <VALUE> ConditionResult<VALUE> value(Map<?, ? extends VALUE> actual, Object key) {
        return findEntry(actual, key).mapValue(Entry::getValue);
    }

    private static <KEY, VALUE> ConditionResult<Entry<KEY, VALUE>> findEntry(Map<KEY, VALUE> actual, Object key) {
        if (actual == null) return unsatisfied("map was null");
        for (Entry<KEY, VALUE> entry : actual.entrySet()) {
            if (deepEquals(entry.getKey(), key)) {
                return satisfied(entry);
            }
        }
        return unsatisfied("map did not contain the expected key");
    }

    private static PreservingCondition<Map<?, ?>> mapSize(int bound, IntPredicate matches,
            String relation) {
        return sized("map", "size", bound, matches, relation, Map::size);
    }

    private static <ENTRIES extends Map<?, ?>> PreservingCondition<ENTRIES> mapCondition(String description, String mismatch,
            CheckedPredicate<? super ENTRIES> matches) {
        return mapCondition(description, mismatch, null, matches);
    }

    private static <ENTRIES extends Map<?, ?>> PreservingCondition<ENTRIES> mapCondition(String description, String mismatch,
            Reference<?> reference, CheckedPredicate<? super ENTRIES> matches) {
        return preservingNonNull("map", description, mismatch, reference, matches);
    }

    private static boolean exactContent(Map<?, ?> actual, List<? extends Entry<?, ?>> expected) throws Throwable {
        return actual.size() == expected.size()
                && sameElementsInAnyOrder(actual.entrySet().iterator(), expected, MapConditions::entryMatches);
    }

    // Preserve encounter order and distinct entries without calling user equals/hashCode.
    private static List<? extends Entry<?, ?>> snapshotEntries(Map<?, ?> values, String name) {
        return requireNonNull(values, name + " must not be null")
                .entrySet().stream().map(SimpleImmutableEntry::new).toList();
    }

    private static boolean entryMatches(Entry<?, ?> actual, Entry<?, ?> expected) {
        return deepEquals(actual.getKey(), expected.getKey()) && deepEquals(actual.getValue(), expected.getValue());
    }

}

package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.ConditionSupport.Quantifier;
import io.github.gromoff97.awium.function.CheckedBiPredicate;
import io.github.gromoff97.awium.function.CheckedPredicate;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

import static io.github.gromoff97.awium.internal.AttemptResult.satisfied;
import static io.github.gromoff97.awium.internal.AttemptResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.compose;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.quantified;
import static io.github.gromoff97.awium.ConditionSupport.selected;
import static io.github.gromoff97.awium.ConditionSupport.snapshot;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static io.github.gromoff97.awium.ConditionSupport.validateRange;
import static io.github.gromoff97.awium.ValueMatching.containsAll;
import static io.github.gromoff97.awium.ValueMatching.containsAny;
import static io.github.gromoff97.awium.ValueMatching.exactly;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.ValueMatching.sameDistinctElements;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;

/**
 * Checks and selections for {@link Map} sources. Checks preserve the observed map's type and
 * instance; selections return an entry, a value, or the nested condition's result.
 * A null observed map is unsatisfied, including for absence checks.
 *
 * <p>Checks with explicit expected keys, values, or entry maps compare each key and value with
 * {@link java.util.Objects#deepEquals(Object, Object)}, with the observed operand first,
 * independently of the map's comparator or identity rules. Key lookup uses the same comparison.
 * Exact entry checks ignore encounter order.
 * Nested conditions retain their own comparison rules; equality of a whole entry follows
 * {@link Entry#equals(Object)}, which compares array keys and values by identity.
 * Selected entries are the original entry objects, with the map implementation's lifecycle.
 * Expected sets and entry maps are copied when the condition is created; their keys and values
 * remain caller-owned.
 */
public final class MapConditions {

    public static final SelectedCondition<Map<?, ?>> singleEntry = selected("map has a single entry",
            MapConditions::selectSingleEntry);
    public static final PreservingCondition<Map<?, ?>> empty = preserving("map is empty", "map was not empty", Map::isEmpty);
    public static final PreservingCondition<Map<?, ?>> nonEmpty = preserving("map is not empty", "map was empty", actual -> !actual.isEmpty());

    private MapConditions() {}

    public static PreservingCondition<Map<?, ?>> hasSize(int expected) {
        return sized(expected, actual -> actual == expected, "is " + expected);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeNotEqualTo(int unexpected) {
        return sized(unexpected, actual -> actual != unexpected, "is not " + unexpected);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeGreaterThan(int lowerBound) {
        return sized(lowerBound, actual -> actual > lowerBound, "is greater than " + lowerBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeAtLeast(int lowerBound) {
        return sized(lowerBound, actual -> actual >= lowerBound, "is at least " + lowerBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeLessThan(int upperBound) {
        return sized(upperBound, actual -> actual < upperBound, "is less than " + upperBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeAtMost(int upperBound) {
        return sized(upperBound, actual -> actual <= upperBound, "is at most " + upperBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeBetween(int lowerBound, int upperBound) {
        validateRange(lowerBound, upperBound, "size");
        return sized(lowerBound, actual -> actual >= lowerBound && actual <= upperBound,
                "is between " + lowerBound + " and " + upperBound);
    }

    /** Checks the sole entry, retaining the source's entry type. */
    public static <KEY, VALUE> SelectedCondition<Map<KEY, VALUE>> singleEntry(
            CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return singleEntry(Conditions.matches(predicate));
    }

    /** Checks the sole entry's key and value, retaining the source's entry type. */
    public static <KEY, VALUE> SelectedCondition<Map<? extends KEY, ? extends VALUE>> singleEntry(
            CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return new SelectedCondition<>(compose("single entry", entryCheck(predicate), singleEntry));
    }

    public static <KEY, VALUE> SelectedCondition<Map<KEY, VALUE>> singleEntry(
            PreservingCondition<? super Entry<KEY, VALUE>> nested) {
        return new SelectedCondition<>(compose("single entry", nested, singleEntry));
    }

    public static <KEY, VALUE, RESULT> Condition.Typed<Map<? extends KEY, ? extends VALUE>, RESULT> singleEntry(
            Condition.Typed<? super Entry<? extends KEY, ? extends VALUE>, RESULT> nested) {
        return compose("single entry", nested, singleEntry);
    }

    public static <KEY, VALUE> SelectedCondition<Map<? super KEY, ? super VALUE>> singleEntry(
            ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return new SelectedCondition<>(compose("single entry", nested, singleEntry));
    }

    /** Checks every entry, returning the original map. */
    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allEntries(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return allEntries(Conditions.matches(predicate));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> allEntries(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return quantified("map", "entries", Quantifier.ALL, entryCheck(predicate), Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allEntries(PreservingCondition<? super Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.ALL, nested, Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> allEntries(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.ALL, nested, Map::entrySet);
    }

    public static PreservingCondition<Map<?, ?>> allEntries(Condition.Typed<? super Entry<?, ?>, ?> nested) {
        return quantified("map", "entries", Quantifier.ALL, nested, Map::entrySet);
    }

    /** Requires the expected key-value pair and returns the original map. */
    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasEntry(KEY key, VALUE value) {
        var expected = new SimpleImmutableEntry<>(key, value);
        return preserving("map contains expected entry", "map did not contain expected entry", expectedReference(expected),
                actual -> matchesAny(actual.entrySet(), entry -> entryMatches(entry, expected)));
    }

    /** Applies the predicate to entries until one matches, returning the original map. */
    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasEntry(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return hasEntry(Conditions.matches(predicate));
    }

    /** Requires at least one matching key-value pair and returns the original map. */
    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> hasEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return quantified("map", "entries", Quantifier.ANY, entryCheck(predicate), Map::entrySet);
    }

    /** Applies the condition to entries until one matches, returning the original map. */
    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasEntry(PreservingCondition<? super Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.ANY, nested, Map::entrySet);
    }

    /** Requires at least one entry satisfying the comparison and returns the original map. */
    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasEntry(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.ANY, nested, Map::entrySet);
    }

    /** Requires at least one matching entry; the nested result is discarded and the original map is returned. */
    public static PreservingCondition<Map<?, ?>> hasEntry(Condition.Typed<? super Entry<?, ?>, ?> nested) {
        return quantified("map", "entries", Quantifier.ANY, nested, Map::entrySet);
    }

    /** Requires the key-value pair to be absent and returns the original map. */
    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasNoEntry(KEY key, VALUE value) {
        var unexpected = new SimpleImmutableEntry<>(key, value);
        return preserving("map does not contain unexpected entry", "map contained unexpected entry", unexpectedReference(unexpected),
                actual -> !matchesAny(actual.entrySet(), entry -> entryMatches(entry, unexpected)));
    }

    /** Requires every entry to fail the predicate and returns the original map. */
    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasNoEntry(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return hasNoEntry(Conditions.matches(predicate));
    }

    /** Requires every key-value pair to fail the predicate and returns the original map. */
    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> hasNoEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return quantified("map", "entries", Quantifier.NONE, entryCheck(predicate), Map::entrySet);
    }

    /** Requires no entry to satisfy the condition and returns the original map. */
    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasNoEntry(PreservingCondition<? super Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.NONE, nested, Map::entrySet);
    }

    /** Requires no entry to satisfy the comparison and returns the original map. */
    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasNoEntry(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return quantified("map", "entries", Quantifier.NONE, nested, Map::entrySet);
    }

    /** Requires no entry to satisfy the nested condition and returns the original map. */
    public static PreservingCondition<Map<?, ?>> hasNoEntry(Condition.Typed<? super Entry<?, ?>, ?> nested) {
        return quantified("map", "entries", Quantifier.NONE, nested, Map::entrySet);
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> allKeys(CheckedPredicate<? super KEY> predicate) {
        return allKeys(Conditions.matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> allKeys(Condition.InputCondition<? super KEY> nested) {
        return quantified("map", "keys", Quantifier.ALL, nested, Map::keySet);
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> allKeys(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "keys", Quantifier.ALL, nested, Map::keySet);
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> anyKey(CheckedPredicate<? super KEY> predicate) {
        return anyKey(Conditions.matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> anyKey(Condition.InputCondition<? super KEY> nested) {
        return quantified("map", "keys", Quantifier.ANY, nested, Map::keySet);
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> anyKey(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "keys", Quantifier.ANY, nested, Map::keySet);
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> noKey(CheckedPredicate<? super KEY> predicate) {
        return noKey(Conditions.matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> noKey(Condition.InputCondition<? super KEY> nested) {
        return quantified("map", "keys", Quantifier.NONE, nested, Map::keySet);
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> noKey(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "keys", Quantifier.NONE, nested, Map::keySet);
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> allValues(CheckedPredicate<? super VALUE> predicate) {
        return allValues(Conditions.matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> allValues(Condition.InputCondition<? super VALUE> nested) {
        return quantified("map", "values", Quantifier.ALL, nested, Map::values);
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> allValues(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "values", Quantifier.ALL, nested, Map::values);
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> anyValue(CheckedPredicate<? super VALUE> predicate) {
        return anyValue(Conditions.matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> anyValue(Condition.InputCondition<? super VALUE> nested) {
        return quantified("map", "values", Quantifier.ANY, nested, Map::values);
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> anyValue(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "values", Quantifier.ANY, nested, Map::values);
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> noValue(CheckedPredicate<? super VALUE> predicate) {
        return noValue(Conditions.matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> noValue(Condition.InputCondition<? super VALUE> nested) {
        return quantified("map", "values", Quantifier.NONE, nested, Map::values);
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> noValue(ComparisonCondition<EXPECTED> nested) {
        return quantified("map", "values", Quantifier.NONE, nested, Map::values);
    }

    public static <KEY> MapValueCondition<Map<? super KEY, ?>> valueFor(KEY key) {
        return new MapValueCondition<>(ConditionSupport.condition("map contains the expected key", expectedReference(key), actual -> value(actual, key)));
    }

    /** Selects the key's value if it matches, retaining the source's value type. */
    public static <KEY, VALUE> MapValueCondition<Map<? super KEY, ? extends VALUE>> valueFor(KEY key,
            CheckedPredicate<? super VALUE> predicate) {
        return valueFor(key, Conditions.matches(predicate));
    }

    public static <KEY, VALUE, RESULT> Condition.Typed<Map<? super KEY, ? extends VALUE>, RESULT> valueFor(KEY key,
            Condition.Typed<? super VALUE, RESULT> nested) {
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
        return new SelectedCondition<>(ConditionSupport.condition("map contains the expected key", expectedReference(key), actual -> findEntry(actual, key)));
    }

    /** Matches one key, including a collection or array as that single key. */
    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsKey(KEY expected) {
        return preserving("map contains expected key", "map did not contain expected key", expectedReference(expected),
                actual -> matchesAny(actual.keySet(), key -> deepEquals(key, expected)));
    }

    /** Matches when the single unexpected key is absent. */
    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainKey(KEY unexpected) {
        return preserving("map does not contain unexpected key", "map contained unexpected key", unexpectedReference(unexpected),
                actual -> !matchesAny(actual.keySet(), key -> deepEquals(key, unexpected)));
    }

    /** Matches one value, including a collection or array as that single value. */
    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsValue(VALUE expected) {
        return preserving("map contains expected value", "map did not contain expected value", expectedReference(expected),
                actual -> matchesAny(actual.values(), value -> deepEquals(value, expected)));
    }

    /** Matches when the single unexpected value is absent. */
    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainValue(VALUE unexpected) {
        return preserving("map does not contain unexpected value", "map contained unexpected value", unexpectedReference(unexpected),
                actual -> !matchesAny(actual.values(), value -> deepEquals(value, unexpected)));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsAllKeys(Iterable<? extends KEY> expected) {
        var keys = snapshot(expected, "expected keys");
        return preserving("map contains all expected keys", "map did not contain all expected keys", expectedReference(keys),
                actual -> containsAll(actual.keySet(), keys, Objects::deepEquals));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainAnyKeys(Iterable<? extends KEY> unexpected) {
        var keys = snapshot(unexpected, "unexpected keys");
        return preserving("map does not contain unexpected keys", "map contained an unexpected key", unexpectedReference(keys),
                actual -> !containsAny(actual.keySet(), keys, Objects::deepEquals));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsOnlyKeys(Iterable<? extends KEY> expected) {
        var keys = snapshot(expected, "expected keys");
        return preserving("map contains only the expected keys", "map did not contain only the expected keys", expectedReference(keys),
                actual -> sameDistinctElements(actual.keySet(), keys));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsAllValues(Iterable<? extends VALUE> expected) {
        var values = snapshot(expected, "expected values");
        return preserving("map contains all expected values", "map did not contain all expected values", expectedReference(values),
                actual -> containsAll(actual.values(), values, Objects::deepEquals));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainAnyValues(Iterable<? extends VALUE> unexpected) {
        var values = snapshot(unexpected, "unexpected values");
        return preserving("map does not contain unexpected values", "map contained an unexpected value", unexpectedReference(values),
                actual -> !containsAny(actual.values(), values, Objects::deepEquals));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAllEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = snapshotEntries(expected, "expected entries");
        return preserving("map contains all expected entries", "map did not contain all expected entries", expectedReference(entries),
                actual -> containsAll(actual.entrySet(), entries,
                        MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAllEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        var entries = snapshotEntries(unexpected, "unexpected entries");
        return preserving("map does not contain all unexpected entries", "map contained all unexpected entries", unexpectedReference(entries),
                actual -> !containsAll(actual.entrySet(), entries,
                        MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAnyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = snapshotEntries(expected, "expected entries");
        return preserving("map contains an expected entry", "map did not contain an expected entry", expectedReference(entries),
                actual -> containsAny(actual.entrySet(), entries, MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAnyEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        var entries = snapshotEntries(unexpected, "unexpected entries");
        return preserving("map does not contain an unexpected entry", "map contained an unexpected entry", unexpectedReference(entries),
                actual -> !containsAny(actual.entrySet(), entries, MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsExactlyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = snapshotEntries(expected, "expected entries");
        return preserving("map contains exactly the expected entries", "map did not contain exactly the expected entries", expectedReference(entries),
                actual -> exactContent(actual, entries));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainExactlyEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        var entries = snapshotEntries(unexpected, "unexpected entries");
        return preserving("map does not contain exactly the unexpected entries", "map contained exactly the unexpected entries", unexpectedReference(entries),
                actual -> !exactContent(actual, entries));
    }

    private static <KEY, VALUE> PreservingCondition<Entry<? extends KEY, ? extends VALUE>> entryCheck(
            CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return Conditions.matches(entry -> predicate.test(entry.getKey(), entry.getValue()));
    }

    private static <KEY, VALUE> AttemptResult<Entry<KEY, VALUE>> selectSingleEntry(Map<KEY, VALUE> actual) {
        return ConditionSupport.single("map", actual == null ? null : actual.entrySet());
    }

    private static <VALUE> AttemptResult<VALUE> value(Map<?, ? extends VALUE> actual, Object key) {
        return findEntry(actual, key).mapSatisfied(Entry::getValue);
    }

    private static <KEY, VALUE> AttemptResult<Entry<KEY, VALUE>> findEntry(Map<KEY, VALUE> actual, Object key) {
        if (actual == null) return unsatisfied("map was null");
        for (Entry<KEY, VALUE> entry : actual.entrySet()) {
            if (deepEquals(entry.getKey(), key)) {
                return satisfied(entry);
            }
        }
        return unsatisfied("map did not contain the expected key");
    }

    private static PreservingCondition<Map<?, ?>> sized(int bound, IntPredicate matches,
            String relation) {
        return ConditionSupport.sized("map", "size", bound, matches, relation, Map::size);
    }

    private static <ENTRIES extends Map<?, ?>> PreservingCondition<ENTRIES> preserving(String description, String mismatch,
            CheckedPredicate<? super ENTRIES> matches) {
        return preserving(description, mismatch, null, matches);
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
    private static List<? extends Entry<?, ?>> snapshotEntries(Map<?, ?> values, String name) {
        return requireNonNull(values, name + " must not be null")
                .entrySet().stream().map(SimpleImmutableEntry::new).toList();
    }

    private static boolean entryMatches(Entry<?, ?> actual, Entry<?, ?> expected) {
        return deepEquals(actual.getKey(), expected.getKey()) && deepEquals(actual.getValue(), expected.getValue());
    }

}

package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.Condition.SourceCondition;
import java.util.concurrent.Callable;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Objects;
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
import static java.util.Objects.requireNonNull;

public final class MapConditions {

    public static final SelectedCondition<Map<?, ?>, Object, Reference<?>> singleEntry = selected("map has a single entry",
            actual -> ConditionSupport.single("map", actual == null ? null : actual.entrySet(), ConditionResult::satisfied));
    public static final PreservingCondition<Map<?, ?>> empty = preserving("map is empty", "map was not empty", Map::isEmpty);
    public static final PreservingCondition<Map<?, ?>> nonEmpty = preserving("map is not empty", "map was empty", actual -> !actual.isEmpty());

    private MapConditions() {}

    /**
     * Configures a map wait, retaining its complete type, entry type, and value type.
     * @param <KEY> key type
     * @param <VALUE> value type
     * @param <ENTRIES> complete map type
     * @param source repeatedly invoked source
     * @return immutable wait configuration
     */
    public static <KEY, VALUE, ENTRIES extends Map<KEY, VALUE>> Await<ENTRIES, Map.Entry<KEY, VALUE>, VALUE> await(Callable<? extends ENTRIES> source) {
        return new Await<>(source, WaitConfiguration.DEFAULT);
    }

    /**
     * Configures a wait using a covariant map source description.
     * @param <KEY> selected key bound
     * @param <VALUE> selected value bound
     * @param <ENTRIES> complete map type
     * @param source typed source description
     * @return immutable wait configuration
     */
    public static <KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>
            Await<ENTRIES, Map.Entry<? extends KEY, ? extends VALUE>, VALUE> await(Await.MapViewSource<KEY, VALUE, ENTRIES> source) {
        return new Await<>(requireNonNull(source, "source must not be null").delegate(), WaitConfiguration.DEFAULT);
    }

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
    public static <KEY, VALUE> SelectedCondition<Map<?, ?>, Entry<? extends KEY, ? extends VALUE>, Reference<?>> singleEntry(
            CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return singleEntry(Conditions.matches((Entry<? extends KEY, ? extends VALUE> entry) ->
                predicate.test(entry.getKey(), entry.getValue())));
    }

    public static <CHECKED, REFERENCE> SelectedCondition<Map<?, ?>, CHECKED, REFERENCE> singleEntry(
            SourceCondition<CHECKED, REFERENCE, ?> nested) {
        return new SelectedCondition<>(ConditionSupport.single("map", required(nested), Map::entrySet,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested)));
    }

    public static <KEY, VALUE, RESULT> Condition.Typed<Map<? extends KEY, ? extends VALUE>, RESULT> singleEntry(
            Condition.Typed<? super Entry<? extends KEY, ? extends VALUE>, RESULT> nested) {
        return ConditionSupport.single("map", required(nested), Map::entrySet,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <KEY, VALUE> SelectedCondition<Map<? super KEY, ? super VALUE>, Object, Reference<?>> singleEntry(
            ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return new SelectedCondition<>(ConditionSupport.single("map", required(nested), Map::entrySet,
                () -> ConditionAwaitAttempt.bind(nested)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> allEntries(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        var nested = Conditions.matches((Entry<? extends KEY, ? extends VALUE> entry) ->
                predicate.test(entry.getKey(), entry.getValue()));
        return ConditionSupport.quantified("map", "all map entries match", "not all map entries matched",
                ConditionSupport.Quantifier.ALL, nested, Map::entrySet,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allEntries(PreservingCondition<? super Entry<KEY, VALUE>> nested) {
        return ConditionSupport.quantified("map", "all map entries match", "not all map entries matched",
                ConditionSupport.Quantifier.ALL, nested, Map::entrySet,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> allEntries(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return ConditionSupport.quantified("map", "all map entries match", "not all map entries matched",
                ConditionSupport.Quantifier.ALL, nested, Map::entrySet,
                () -> ConditionAwaitAttempt.<Object, Object>bind(nested));
    }

    public static <RESULT> PreservingCondition<Map<?, ?>> allEntries(Condition.Typed<? super Entry<?, ?>, RESULT> nested) {
        return ConditionSupport.quantified("map", "all map entries match", "not all map entries matched",
                ConditionSupport.Quantifier.ALL, nested, Map::entrySet,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> anyEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        var nested = Conditions.matches((Entry<? extends KEY, ? extends VALUE> entry) ->
                predicate.test(entry.getKey(), entry.getValue()));
        return ConditionSupport.quantified("map", "any map entry matches", "no map entry matched",
                ConditionSupport.Quantifier.ANY, nested, Map::entrySet,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> anyEntry(PreservingCondition<? super Entry<KEY, VALUE>> nested) {
        return ConditionSupport.quantified("map", "any map entry matches", "no map entry matched",
                ConditionSupport.Quantifier.ANY, nested, Map::entrySet,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> anyEntry(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return ConditionSupport.quantified("map", "any map entry matches", "no map entry matched",
                ConditionSupport.Quantifier.ANY, nested, Map::entrySet,
                () -> ConditionAwaitAttempt.<Object, Object>bind(nested));
    }

    public static <RESULT> PreservingCondition<Map<?, ?>> anyEntry(Condition.Typed<? super Entry<?, ?>, RESULT> nested) {
        return ConditionSupport.quantified("map", "any map entry matches", "no map entry matched",
                ConditionSupport.Quantifier.ANY, nested, Map::entrySet,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> noEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        var nested = Conditions.matches((Entry<? extends KEY, ? extends VALUE> entry) ->
                predicate.test(entry.getKey(), entry.getValue()));
        return ConditionSupport.quantified("map", "no map entry matches", "a map entry matched",
                ConditionSupport.Quantifier.NONE, nested, Map::entrySet,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> noEntry(PreservingCondition<? super Entry<KEY, VALUE>> nested) {
        return ConditionSupport.quantified("map", "no map entry matches", "a map entry matched",
                ConditionSupport.Quantifier.NONE, nested, Map::entrySet,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> noEntry(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return ConditionSupport.quantified("map", "no map entry matches", "a map entry matched",
                ConditionSupport.Quantifier.NONE, nested, Map::entrySet,
                () -> ConditionAwaitAttempt.<Object, Object>bind(nested));
    }

    public static <RESULT> PreservingCondition<Map<?, ?>> noEntry(Condition.Typed<? super Entry<?, ?>, RESULT> nested) {
        return ConditionSupport.quantified("map", "no map entry matches", "a map entry matched",
                ConditionSupport.Quantifier.NONE, nested, Map::entrySet,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> allKeys(CheckedPredicate<? super KEY> predicate) {
        return allKeys(Conditions.matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> allKeys(PreservingCondition<? super KEY> nested) {
        return ConditionSupport.quantified("map", "all map keys match", "not all map keys matched",
                ConditionSupport.Quantifier.ALL, nested, Map::keySet,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> allKeys(ComparisonCondition<EXPECTED> nested) {
        return ConditionSupport.quantified("map", "all map keys match", "not all map keys matched",
                ConditionSupport.Quantifier.ALL, nested, Map::keySet,
                () -> ConditionAwaitAttempt.<Object, Object>bind(nested));
    }

    public static <KEY, RESULT> PreservingCondition<Map<? extends KEY, ?>> allKeys(Condition.Typed<? super KEY, RESULT> nested) {
        return ConditionSupport.quantified("map", "all map keys match", "not all map keys matched",
                ConditionSupport.Quantifier.ALL, nested, Map::keySet,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> anyKey(CheckedPredicate<? super KEY> predicate) {
        return anyKey(Conditions.matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> anyKey(PreservingCondition<? super KEY> nested) {
        return ConditionSupport.quantified("map", "any map key matches", "no map key matched",
                ConditionSupport.Quantifier.ANY, nested, Map::keySet,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> anyKey(ComparisonCondition<EXPECTED> nested) {
        return ConditionSupport.quantified("map", "any map key matches", "no map key matched",
                ConditionSupport.Quantifier.ANY, nested, Map::keySet,
                () -> ConditionAwaitAttempt.<Object, Object>bind(nested));
    }

    public static <KEY, RESULT> PreservingCondition<Map<? extends KEY, ?>> anyKey(Condition.Typed<? super KEY, RESULT> nested) {
        return ConditionSupport.quantified("map", "any map key matches", "no map key matched",
                ConditionSupport.Quantifier.ANY, nested, Map::keySet,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> noKey(CheckedPredicate<? super KEY> predicate) {
        return noKey(Conditions.matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> noKey(PreservingCondition<? super KEY> nested) {
        return ConditionSupport.quantified("map", "no map key matches", "a map key matched",
                ConditionSupport.Quantifier.NONE, nested, Map::keySet,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> noKey(ComparisonCondition<EXPECTED> nested) {
        return ConditionSupport.quantified("map", "no map key matches", "a map key matched",
                ConditionSupport.Quantifier.NONE, nested, Map::keySet,
                () -> ConditionAwaitAttempt.<Object, Object>bind(nested));
    }

    public static <KEY, RESULT> PreservingCondition<Map<? extends KEY, ?>> noKey(Condition.Typed<? super KEY, RESULT> nested) {
        return ConditionSupport.quantified("map", "no map key matches", "a map key matched",
                ConditionSupport.Quantifier.NONE, nested, Map::keySet,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> allValues(CheckedPredicate<? super VALUE> predicate) {
        return allValues(Conditions.matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> allValues(PreservingCondition<? super VALUE> nested) {
        return ConditionSupport.quantified("map", "all map values match", "not all map values matched",
                ConditionSupport.Quantifier.ALL, nested, Map::values,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> allValues(ComparisonCondition<EXPECTED> nested) {
        return ConditionSupport.quantified("map", "all map values match", "not all map values matched",
                ConditionSupport.Quantifier.ALL, nested, Map::values,
                () -> ConditionAwaitAttempt.<Object, Object>bind(nested));
    }

    public static <VALUE, RESULT> PreservingCondition<Map<?, ? extends VALUE>> allValues(Condition.Typed<? super VALUE, RESULT> nested) {
        return ConditionSupport.quantified("map", "all map values match", "not all map values matched",
                ConditionSupport.Quantifier.ALL, nested, Map::values,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> anyValue(CheckedPredicate<? super VALUE> predicate) {
        return anyValue(Conditions.matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> anyValue(PreservingCondition<? super VALUE> nested) {
        return ConditionSupport.quantified("map", "any map value matches", "no map value matched",
                ConditionSupport.Quantifier.ANY, nested, Map::values,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> anyValue(ComparisonCondition<EXPECTED> nested) {
        return ConditionSupport.quantified("map", "any map value matches", "no map value matched",
                ConditionSupport.Quantifier.ANY, nested, Map::values,
                () -> ConditionAwaitAttempt.<Object, Object>bind(nested));
    }

    public static <VALUE, RESULT> PreservingCondition<Map<?, ? extends VALUE>> anyValue(Condition.Typed<? super VALUE, RESULT> nested) {
        return ConditionSupport.quantified("map", "any map value matches", "no map value matched",
                ConditionSupport.Quantifier.ANY, nested, Map::values,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> noValue(CheckedPredicate<? super VALUE> predicate) {
        return noValue(Conditions.matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> noValue(PreservingCondition<? super VALUE> nested) {
        return ConditionSupport.quantified("map", "no map value matches", "a map value matched",
                ConditionSupport.Quantifier.NONE, nested, Map::values,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> noValue(ComparisonCondition<EXPECTED> nested) {
        return ConditionSupport.quantified("map", "no map value matches", "a map value matched",
                ConditionSupport.Quantifier.NONE, nested, Map::values,
                () -> ConditionAwaitAttempt.<Object, Object>bind(nested));
    }

    public static <VALUE, RESULT> PreservingCondition<Map<?, ? extends VALUE>> noValue(Condition.Typed<? super VALUE, RESULT> nested) {
        return ConditionSupport.quantified("map", "no map value matches", "a map value matched",
                ConditionSupport.Quantifier.NONE, nested, Map::values,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <KEY> MapValueCondition<Map<? super KEY, ?>, Object, Reference<?>> valueFor(KEY key) {
        return new MapValueCondition<>(ConditionSupport.condition("map contains the expected key", expectedReference(key), actual -> value(actual, key)));
    }

    /** Selects the key's value if it matches, retaining the source's value type. */
    public static <KEY, VALUE> MapValueCondition<Map<? super KEY, ?>, VALUE, Reference<?>> valueFor(KEY key,
            CheckedPredicate<? super VALUE> predicate) {
        return valueFor(key, Conditions.matches(predicate));
    }

    public static <KEY, VALUE, RESULT> Condition.Typed<Map<? super KEY, ? extends VALUE>, RESULT> valueFor(KEY key,
            Condition.Typed<? super VALUE, RESULT> nested) {
        return compose("map value ", required(nested),
                valueFor(key), actual -> value(actual, key),
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <KEY, CHECKED, REFERENCE> MapValueCondition<Map<? super KEY, ?>, CHECKED, REFERENCE> valueFor(KEY key,
            SourceCondition<CHECKED, REFERENCE, ?> nested) {
        return new MapValueCondition<>(compose("map value ", required(nested),
                valueFor(key), actual -> value(actual, key),
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested)));
    }

    public static <KEY> SelectedCondition<Map<? super KEY, ?>, Object, Reference<?>> entryFor(KEY key) {
        return new SelectedCondition<>(ConditionSupport.condition("map contains the expected key", expectedReference(key), actual -> findEntry(actual, key)));
    }

    public static <KEY> MapValueCondition<Map<? super KEY, ?>, Object, Reference<?>> onlyValueFor(KEY key) {
        return new MapValueCondition<>(ConditionSupport.condition("map contains only the expected key", expectedReference(key), actual ->
                onlyValue(actual, key)));
    }

    public static <KEY, VALUE> MapValueCondition<Map<? super KEY, ?>, VALUE, Reference<?>> onlyValueFor(KEY key,
            CheckedPredicate<? super VALUE> predicate) {
        return onlyValueFor(key, Conditions.matches(predicate));
    }

    public static <KEY, VALUE, RESULT> Condition.Typed<Map<? super KEY, ? extends VALUE>, RESULT> onlyValueFor(KEY key,
            Condition.Typed<? super VALUE, RESULT> nested) {
        return compose("only map value ", required(nested), onlyValueFor(key), actual -> onlyValue(actual, key),
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <KEY, CHECKED, REFERENCE> MapValueCondition<Map<? super KEY, ?>, CHECKED, REFERENCE> onlyValueFor(KEY key,
            SourceCondition<CHECKED, REFERENCE, ?> nested) {
        return new MapValueCondition<>(compose("only map value ", required(nested), onlyValueFor(key),
                actual -> onlyValue(actual, key), () -> ConditionAwaitAttempt.bindSelectionCheck(nested)));
    }

    private static <KEY, VALUE> ConditionResult<VALUE> onlyValue(Map<? super KEY, ? extends VALUE> actual, KEY key) {
        return ConditionSupport.single("map", actual == null ? null : actual.entrySet(), entry ->
                deepEquals(entry.getKey(), key) ? satisfied(entry.getValue()) : unsatisfied("map contained a different key"));
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
        var keys = nonEmpty(snapshot(expected, "expected keys"), "expected keys");
        return preserving("map contains all expected keys", "map did not contain all expected keys", expectedReference(keys),
                actual -> containsAll(actual.keySet(), keys, Objects::deepEquals));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainAnyKeys(Iterable<? extends KEY> unexpected) {
        var keys = nonEmpty(snapshot(unexpected, "unexpected keys"), "unexpected keys");
        return preserving("map does not contain unexpected keys", "map contained an unexpected key", unexpectedReference(keys),
                actual -> !containsAny(actual.keySet(), keys, Objects::deepEquals));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsOnlyKeys(Iterable<? extends KEY> expected) {
        var keys = snapshot(expected, "expected keys");
        return preserving("map contains only the expected keys", "map did not contain only the expected keys", expectedReference(keys),
                actual -> sameDistinctElements(actual.keySet(), keys));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsAllValues(Iterable<? extends VALUE> expected) {
        var values = nonEmpty(snapshot(expected, "expected values"), "expected values");
        return preserving("map contains all expected values", "map did not contain all expected values", expectedReference(values),
                actual -> containsAll(actual.values(), values, Objects::deepEquals));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainAnyValues(Iterable<? extends VALUE> unexpected) {
        var values = nonEmpty(snapshot(unexpected, "unexpected values"), "unexpected values");
        return preserving("map does not contain unexpected values", "map contained an unexpected value", unexpectedReference(values),
                actual -> !containsAny(actual.values(), values, Objects::deepEquals));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsEntry(KEY key, VALUE value) {
        var expected = new SimpleImmutableEntry<>(key, value);
        return preserving("map contains expected entry", "map did not contain expected entry", expectedReference(expected),
                actual -> matchesAny(actual.entrySet(), entry -> entryMatches(entry, expected)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainEntry(KEY key, VALUE value) {
        var unexpected = new SimpleImmutableEntry<>(key, value);
        return preserving("map does not contain unexpected entry", "map contained unexpected entry", unexpectedReference(unexpected),
                actual -> !matchesAny(actual.entrySet(), entry -> entryMatches(entry, unexpected)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAllEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = nonEmpty(snapshotEntries(expected, "expected entries"), "expected entries");
        return preserving("map contains all expected entries", "map did not contain all expected entries", expectedReference(entries),
                actual -> containsAll(actual.entrySet(), entries,
                        MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAllEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        var entries = nonEmpty(snapshotEntries(unexpected, "unexpected entries"), "unexpected entries");
        return preserving("map does not contain all unexpected entries", "map contained all unexpected entries", unexpectedReference(entries),
                actual -> !containsAll(actual.entrySet(), entries,
                        MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAnyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = nonEmpty(snapshotEntries(expected, "expected entries"), "expected entries");
        return preserving("map contains an expected entry", "map did not contain an expected entry", expectedReference(entries),
                actual -> containsAny(actual.entrySet(), entries, MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAnyEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        var entries = nonEmpty(snapshotEntries(unexpected, "unexpected entries"), "unexpected entries");
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
    private static List<? extends Entry<?, ?>> snapshotEntries(Map<?, ?> values, String name) {
        return requireNonNull(values, name + " must not be null")
                .entrySet().stream().map(SimpleImmutableEntry::new).toList();
    }

    private static boolean entryMatches(Entry<?, ?> actual, Entry<?, ?> expected) {
        return deepEquals(actual.getKey(), expected.getKey()) && deepEquals(actual.getValue(), expected.getValue());
    }

}

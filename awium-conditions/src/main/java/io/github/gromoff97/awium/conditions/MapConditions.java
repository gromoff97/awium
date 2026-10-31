package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.conditions.internal.ContainsCondition;
import io.github.gromoff97.awium.conditions.internal.ContainsElementsCondition.Membership;
import io.github.gromoff97.awium.conditions.internal.ContainsElementsCondition;
import io.github.gromoff97.awium.conditions.internal.EmptyCondition;
import io.github.gromoff97.awium.conditions.internal.EntryForCondition;
import io.github.gromoff97.awium.conditions.internal.EntryMatchesCondition;
import io.github.gromoff97.awium.conditions.internal.HasSizeCondition;
import io.github.gromoff97.awium.conditions.internal.MapContentCondition;
import io.github.gromoff97.awium.conditions.internal.MapEntries;
import io.github.gromoff97.awium.conditions.internal.OnlyKeysCondition;
import io.github.gromoff97.awium.conditions.internal.QuantifiedCondition.Quantifier;
import io.github.gromoff97.awium.conditions.internal.QuantifiedCondition;
import io.github.gromoff97.awium.conditions.internal.SingleEntryCondition;
import io.github.gromoff97.awium.conditions.internal.ValueForCondition;
import io.github.gromoff97.awium.conditions.function.CheckedBiPredicate;
import io.github.gromoff97.awium.conditions.function.CheckedPredicate;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Objects;

import static io.github.gromoff97.awium.AwaitCondition.InputCondition;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.internal.MapEntries.snapshotEntries;
import static io.github.gromoff97.awium.conditions.internal.NestedCondition.compose;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.AT_LEAST;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.AT_MOST;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.BETWEEN;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.EQUAL;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.GREATER;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.LESS;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.NOT_EQUAL;

public final class MapConditions {

    public static final SelectedCondition<Map<?, ?>> singleEntry = new SingleEntryCondition();
    public static final PreservingCondition<Map<?, ?>> empty = new EmptyCondition<>("map", Map::isEmpty, true);
    public static final PreservingCondition<Map<?, ?>> nonEmpty = new EmptyCondition<>("map", Map::isEmpty, false);

    private MapConditions() {}

    public static PreservingCondition<Map<?, ?>> hasSize(int expected) {
        return new HasSizeCondition<>("map", "size", Map::size, EQUAL, expected);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeNotEqualTo(int unexpected) {
        return new HasSizeCondition<>("map", "size", Map::size, NOT_EQUAL, unexpected);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeGreaterThan(int lowerBound) {
        return new HasSizeCondition<>("map", "size", Map::size, GREATER, lowerBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeAtLeast(int lowerBound) {
        return new HasSizeCondition<>("map", "size", Map::size, AT_LEAST, lowerBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeLessThan(int upperBound) {
        return new HasSizeCondition<>("map", "size", Map::size, LESS, upperBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeAtMost(int upperBound) {
        return new HasSizeCondition<>("map", "size", Map::size, AT_MOST, upperBound);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeBetween(int lowerBound, int upperBound) {
        return new HasSizeCondition<>("map", "size", Map::size, BETWEEN, lowerBound, upperBound);
    }

    public static <KEY, VALUE> SelectedCondition<Map<KEY, VALUE>> singleEntry(
            CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return singleEntry(matches(predicate));
    }

    public static <KEY, VALUE> SelectedCondition<Map<? extends KEY, ? extends VALUE>> singleEntry(
            CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return new SelectedCondition<>(compose("single entry", new EntryMatchesCondition<>(predicate), singleEntry)) {};
    }

    public static <KEY, VALUE> SelectedCondition<Map<KEY, VALUE>> singleEntry(
            PreservingCondition<? super Entry<KEY, VALUE>> nested) {
        return new SelectedCondition<>(compose("single entry", nested, singleEntry)) {};
    }

    public static <KEY, VALUE, RESULT> Condition<Map<? extends KEY, ? extends VALUE>, RESULT> singleEntry(
            Condition<? super Entry<? extends KEY, ? extends VALUE>, RESULT> nested) {
        return compose("single entry", nested, singleEntry);
    }

    public static <KEY, VALUE> SelectedCondition<Map<? super KEY, ? super VALUE>> singleEntry(
            ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return new SelectedCondition<>(compose("single entry", nested, singleEntry)) {};
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allEntries(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return allEntries(matches(predicate));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> allEntries(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return new QuantifiedCondition<>("map", "entries", Quantifier.ALL, new EntryMatchesCondition<>(predicate), Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allEntries(InputCondition<? super Entry<KEY, VALUE>> nested) {
        return new QuantifiedCondition<>("map", "entries", Quantifier.ALL, nested, Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> allEntries(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return new QuantifiedCondition<>("map", "entries", Quantifier.ALL, nested, Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasEntry(KEY key, VALUE value) {
        return new ContainsCondition<>("map", "entry", new SimpleImmutableEntry<>(key, value), false,
                Map::entrySet, MapEntries::entryMatches);
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasEntry(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return hasEntry(matches(predicate));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> hasEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return new QuantifiedCondition<>("map", "entries", Quantifier.ANY, new EntryMatchesCondition<>(predicate), Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasEntry(InputCondition<? super Entry<KEY, VALUE>> nested) {
        return new QuantifiedCondition<>("map", "entries", Quantifier.ANY, nested, Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasEntry(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return new QuantifiedCondition<>("map", "entries", Quantifier.ANY, nested, Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasNoEntry(KEY key, VALUE value) {
        return new ContainsCondition<>("map", "entry", new SimpleImmutableEntry<>(key, value), true,
                Map::entrySet, MapEntries::entryMatches);
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasNoEntry(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return hasNoEntry(matches(predicate));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> hasNoEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return new QuantifiedCondition<>("map", "entries", Quantifier.NONE, new EntryMatchesCondition<>(predicate), Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasNoEntry(InputCondition<? super Entry<KEY, VALUE>> nested) {
        return new QuantifiedCondition<>("map", "entries", Quantifier.NONE, nested, Map::entrySet);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasNoEntry(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return new QuantifiedCondition<>("map", "entries", Quantifier.NONE, nested, Map::entrySet);
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> allKeys(CheckedPredicate<? super KEY> predicate) {
        return allKeys(matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> allKeys(InputCondition<? super KEY> nested) {
        return new QuantifiedCondition<>("map", "keys", Quantifier.ALL, nested, Map::keySet);
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> allKeys(ComparisonCondition<EXPECTED> nested) {
        return new QuantifiedCondition<>("map", "keys", Quantifier.ALL, nested, Map::keySet);
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> anyKey(CheckedPredicate<? super KEY> predicate) {
        return anyKey(matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> anyKey(InputCondition<? super KEY> nested) {
        return new QuantifiedCondition<>("map", "keys", Quantifier.ANY, nested, Map::keySet);
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> anyKey(ComparisonCondition<EXPECTED> nested) {
        return new QuantifiedCondition<>("map", "keys", Quantifier.ANY, nested, Map::keySet);
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> noKey(CheckedPredicate<? super KEY> predicate) {
        return noKey(matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> noKey(InputCondition<? super KEY> nested) {
        return new QuantifiedCondition<>("map", "keys", Quantifier.NONE, nested, Map::keySet);
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> noKey(ComparisonCondition<EXPECTED> nested) {
        return new QuantifiedCondition<>("map", "keys", Quantifier.NONE, nested, Map::keySet);
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> allValues(CheckedPredicate<? super VALUE> predicate) {
        return allValues(matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> allValues(InputCondition<? super VALUE> nested) {
        return new QuantifiedCondition<>("map", "values", Quantifier.ALL, nested, Map::values);
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> allValues(ComparisonCondition<EXPECTED> nested) {
        return new QuantifiedCondition<>("map", "values", Quantifier.ALL, nested, Map::values);
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> anyValue(CheckedPredicate<? super VALUE> predicate) {
        return anyValue(matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> anyValue(InputCondition<? super VALUE> nested) {
        return new QuantifiedCondition<>("map", "values", Quantifier.ANY, nested, Map::values);
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> anyValue(ComparisonCondition<EXPECTED> nested) {
        return new QuantifiedCondition<>("map", "values", Quantifier.ANY, nested, Map::values);
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> noValue(CheckedPredicate<? super VALUE> predicate) {
        return noValue(matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> noValue(InputCondition<? super VALUE> nested) {
        return new QuantifiedCondition<>("map", "values", Quantifier.NONE, nested, Map::values);
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> noValue(ComparisonCondition<EXPECTED> nested) {
        return new QuantifiedCondition<>("map", "values", Quantifier.NONE, nested, Map::values);
    }

    public static <KEY> MapValueCondition<Map<? super KEY, ?>> valueFor(KEY key) {
        return new ValueForCondition<>(key);
    }

    public static <KEY, VALUE> MapValueCondition<Map<? super KEY, ? extends VALUE>> valueFor(KEY key,
            CheckedPredicate<? super VALUE> predicate) {
        return valueFor(key, matches(predicate));
    }

    public static <KEY, VALUE, RESULT> Condition<Map<? super KEY, ? extends VALUE>, RESULT> valueFor(KEY key,
            Condition<? super VALUE, RESULT> nested) {
        return compose("map value", nested, valueFor(key));
    }

    public static <KEY, CHECKED> MapValueCondition<Map<? super KEY, ? extends CHECKED>> valueFor(KEY key,
            PreservingCondition<? super CHECKED> nested) {
        return new MapValueCondition<>(compose("map value", nested, valueFor(key))) {};
    }

    public static <KEY, EXPECTED> MapValueCondition<Map<? super KEY, ? super EXPECTED>> valueFor(KEY key,
            ComparisonCondition<EXPECTED> nested) {
        return new MapValueCondition<>(compose("map value", nested, valueFor(key))) {};
    }

    public static <KEY> SelectedCondition<Map<? super KEY, ?>> entryFor(KEY key) {
        return new EntryForCondition<>(key);
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsKey(KEY expected) {
        return new ContainsCondition<>("map", "key", expected, false, Map::keySet, Objects::deepEquals);
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainKey(KEY unexpected) {
        return new ContainsCondition<>("map", "key", unexpected, true, Map::keySet, Objects::deepEquals);
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsValue(VALUE expected) {
        return new ContainsCondition<>("map", "value", expected, false, Map::values, Objects::deepEquals);
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainValue(VALUE unexpected) {
        return new ContainsCondition<>("map", "value", unexpected, true, Map::values, Objects::deepEquals);
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsAllKeys(Iterable<? extends KEY> expected) {
        return new ContainsElementsCondition<>("map", "keys", expected, Membership.ALL, Map::keySet, Objects::deepEquals);
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainAnyKeys(Iterable<? extends KEY> unexpected) {
        return new ContainsElementsCondition<>("map", "keys", unexpected, Membership.NONE, Map::keySet, Objects::deepEquals);
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsOnlyKeys(Iterable<? extends KEY> expected) {
        return new OnlyKeysCondition<>(expected);
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsAllValues(Iterable<? extends VALUE> expected) {
        return new ContainsElementsCondition<>("map", "values", expected, Membership.ALL, Map::values, Objects::deepEquals);
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainAnyValues(Iterable<? extends VALUE> unexpected) {
        return new ContainsElementsCondition<>("map", "values", unexpected, Membership.NONE, Map::values, Objects::deepEquals);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAllEntries(Map<? extends KEY, ? extends VALUE> expected) {
        return new ContainsElementsCondition<>("map", "entries", snapshotEntries(expected, "expected entries"), Membership.ALL, Map::entrySet, MapEntries::entryMatches);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAllEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        return new ContainsElementsCondition<>("map", "entries", snapshotEntries(unexpected, "unexpected entries"), Membership.NOT_ALL, Map::entrySet, MapEntries::entryMatches);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAnyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        return new ContainsElementsCondition<>("map", "entries", snapshotEntries(expected, "expected entries"), Membership.ANY, Map::entrySet, MapEntries::entryMatches);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAnyEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        return new ContainsElementsCondition<>("map", "entries", snapshotEntries(unexpected, "unexpected entries"), Membership.NONE, Map::entrySet, MapEntries::entryMatches);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsExactlyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        return new MapContentCondition<>(expected, false);
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainExactlyEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        return new MapContentCondition<>(unexpected, true);
    }

    // Preserve encounter order and distinct entries without calling user equals/hashCode.

}

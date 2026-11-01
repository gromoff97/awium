package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.conditions.collection.CollectionConditions;
import io.github.gromoff97.awium.conditions.common.CommonConditions;
import io.github.gromoff97.awium.function.checked.CheckedBiPredicate;
import io.github.gromoff97.awium.function.checked.CheckedPredicate;
import java.util.Map.Entry;
import java.util.Map;

import static io.github.gromoff97.awium.AwaitCondition.InputCondition;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.matches;
import static io.github.gromoff97.awium.conditions.map.MapEntries.entryPredicate;
import static io.github.gromoff97.awium.conditions.map.MapEntries.expectedEntries;
import static io.github.gromoff97.awium.conditions.map.MapElementsCondition.Part.ENTRIES;
import static io.github.gromoff97.awium.conditions.map.MapElementsCondition.Part.ENTRY_CONTENTS;
import static io.github.gromoff97.awium.conditions.map.MapElementsCondition.Part.KEYS;
import static io.github.gromoff97.awium.conditions.map.MapElementsCondition.Part.VALUES;
import static io.github.gromoff97.awium.conditions.composition.CompositionConditions.compose;
import static io.github.gromoff97.awium.conditions.composition.CompositionConditions.selected;

public final class MapConditions {

    public static final SelectedCondition<Map<?, ?>> singleEntry = new SingleEntryCondition();
    public static final PreservingCondition<Map<?, ?>> empty = new EmptyCondition(true);
    public static final PreservingCondition<Map<?, ?>> nonEmpty = new EmptyCondition(false);

    private MapConditions() {}

    public static PreservingCondition<Map<?, ?>> hasSize(int expected) {
        return new HasSizeCondition(expected, CommonConditions::equalTo);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeNotEqualTo(int unexpected) {
        return new HasSizeCondition(unexpected, CommonConditions::notEqualTo);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeGreaterThan(int lowerBound) {
        return new HasSizeCondition(lowerBound, CommonConditions::greaterThan);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeAtLeast(int lowerBound) {
        return new HasSizeCondition(lowerBound, CommonConditions::atLeast);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeLessThan(int upperBound) {
        return new HasSizeCondition(upperBound, CommonConditions::lessThan);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeAtMost(int upperBound) {
        return new HasSizeCondition(upperBound, CommonConditions::atMost);
    }

    public static PreservingCondition<Map<?, ?>> hasSizeBetween(int lowerBound, int upperBound) {
        return new HasSizeCondition(lowerBound, upperBound);
    }

    public static <KEY, VALUE> SelectedCondition<Map<KEY, VALUE>> singleEntry(
            CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return singleEntry(matches(predicate));
    }

    public static <KEY, VALUE> SelectedCondition<Map<? extends KEY, ? extends VALUE>> singleEntry(
            CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return selected(compose("single entry", matches(entryPredicate(predicate)), singleEntry));
    }

    public static <KEY, VALUE> SelectedCondition<Map<KEY, VALUE>> singleEntry(
            PreservingCondition<? super Entry<KEY, VALUE>> nested) {
        return selected(compose("single entry", nested, singleEntry));
    }

    public static <KEY, VALUE, RESULT> Condition<Map<? extends KEY, ? extends VALUE>, RESULT> singleEntry(
            Condition<? super Entry<? extends KEY, ? extends VALUE>, RESULT> nested) {
        return compose("single entry", nested, singleEntry);
    }

    public static <KEY, VALUE> SelectedCondition<Map<? super KEY, ? super VALUE>> singleEntry(
            ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return selected(compose("single entry", nested, singleEntry));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allEntries(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return allEntries(matches(predicate));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> allEntries(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return new MapElementsCondition<>(ENTRIES, CollectionConditions.all(matches(entryPredicate(predicate))));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allEntries(InputCondition<? super Entry<KEY, VALUE>> nested) {
        return new MapElementsCondition<>(ENTRIES, CollectionConditions.all(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> allEntries(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return new MapElementsCondition<>(ENTRIES, CollectionConditions.all(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasEntry(KEY key, VALUE value) {
        return new MapElementsCondition<>(ENTRY_CONTENTS, CollectionConditions.contains(new DeepEntry(key, value)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasEntry(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return hasEntry(matches(predicate));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> hasEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return new MapElementsCondition<>(ENTRIES, CollectionConditions.any(matches(entryPredicate(predicate))));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasEntry(InputCondition<? super Entry<KEY, VALUE>> nested) {
        return new MapElementsCondition<>(ENTRIES, CollectionConditions.any(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasEntry(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return new MapElementsCondition<>(ENTRIES, CollectionConditions.any(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasNoEntry(KEY key, VALUE value) {
        return new MapElementsCondition<>(ENTRY_CONTENTS, CollectionConditions.doesNotContain(new DeepEntry(key, value)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasNoEntry(CheckedPredicate<? super Entry<KEY, VALUE>> predicate) {
        return hasNoEntry(matches(predicate));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? extends KEY, ? extends VALUE>> hasNoEntry(CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        return new MapElementsCondition<>(ENTRIES, CollectionConditions.none(matches(entryPredicate(predicate))));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> hasNoEntry(InputCondition<? super Entry<KEY, VALUE>> nested) {
        return new MapElementsCondition<>(ENTRIES, CollectionConditions.none(nested));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> hasNoEntry(ComparisonCondition<? extends Entry<KEY, VALUE>> nested) {
        return new MapElementsCondition<>(ENTRIES, CollectionConditions.none(nested));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> allKeys(CheckedPredicate<? super KEY> predicate) {
        return allKeys(matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> allKeys(InputCondition<? super KEY> nested) {
        return new MapElementsCondition<>(KEYS, CollectionConditions.all(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> allKeys(ComparisonCondition<EXPECTED> nested) {
        return new MapElementsCondition<>(KEYS, CollectionConditions.all(nested));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> anyKey(CheckedPredicate<? super KEY> predicate) {
        return anyKey(matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> anyKey(InputCondition<? super KEY> nested) {
        return new MapElementsCondition<>(KEYS, CollectionConditions.any(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> anyKey(ComparisonCondition<EXPECTED> nested) {
        return new MapElementsCondition<>(KEYS, CollectionConditions.any(nested));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> noKey(CheckedPredicate<? super KEY> predicate) {
        return noKey(matches(predicate));
    }

    public static <KEY> PreservingCondition<Map<? extends KEY, ?>> noKey(InputCondition<? super KEY> nested) {
        return new MapElementsCondition<>(KEYS, CollectionConditions.none(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<? super EXPECTED, ?>> noKey(ComparisonCondition<EXPECTED> nested) {
        return new MapElementsCondition<>(KEYS, CollectionConditions.none(nested));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> allValues(CheckedPredicate<? super VALUE> predicate) {
        return allValues(matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> allValues(InputCondition<? super VALUE> nested) {
        return new MapElementsCondition<>(VALUES, CollectionConditions.all(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> allValues(ComparisonCondition<EXPECTED> nested) {
        return new MapElementsCondition<>(VALUES, CollectionConditions.all(nested));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> anyValue(CheckedPredicate<? super VALUE> predicate) {
        return anyValue(matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> anyValue(InputCondition<? super VALUE> nested) {
        return new MapElementsCondition<>(VALUES, CollectionConditions.any(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> anyValue(ComparisonCondition<EXPECTED> nested) {
        return new MapElementsCondition<>(VALUES, CollectionConditions.any(nested));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> noValue(CheckedPredicate<? super VALUE> predicate) {
        return noValue(matches(predicate));
    }

    public static <VALUE> PreservingCondition<Map<?, ? extends VALUE>> noValue(InputCondition<? super VALUE> nested) {
        return new MapElementsCondition<>(VALUES, CollectionConditions.none(nested));
    }

    public static <EXPECTED> PreservingCondition<Map<?, ? super EXPECTED>> noValue(ComparisonCondition<EXPECTED> nested) {
        return new MapElementsCondition<>(VALUES, CollectionConditions.none(nested));
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
        return new NestedValueCondition<>(compose("map value", nested, valueFor(key)));
    }

    public static <KEY, EXPECTED> MapValueCondition<Map<? super KEY, ? super EXPECTED>> valueFor(KEY key,
            ComparisonCondition<EXPECTED> nested) {
        return new NestedValueCondition<>(compose("map value", nested, valueFor(key)));
    }

    public static <KEY> SelectedCondition<Map<? super KEY, ?>> entryFor(KEY key) {
        return new EntryForCondition<>(key);
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsKey(KEY expected) {
        return new MapElementsCondition<>(KEYS, CollectionConditions.contains(expected));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainKey(KEY unexpected) {
        return new MapElementsCondition<>(KEYS, CollectionConditions.doesNotContain(unexpected));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsValue(VALUE expected) {
        return new MapElementsCondition<>(VALUES, CollectionConditions.contains(expected));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainValue(VALUE unexpected) {
        return new MapElementsCondition<>(VALUES, CollectionConditions.doesNotContain(unexpected));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsAllKeys(Iterable<? extends KEY> expected) {
        return new MapElementsCondition<>(KEYS, CollectionConditions.containsAll(expected));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainAnyKeys(Iterable<? extends KEY> unexpected) {
        return new MapElementsCondition<>(KEYS, CollectionConditions.doesNotContainAny(unexpected));
    }

    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsOnlyKeys(Iterable<? extends KEY> expected) {
        return new MapElementsCondition<>(KEYS, CollectionConditions.containsOnly(expected));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsAllValues(Iterable<? extends VALUE> expected) {
        return new MapElementsCondition<>(VALUES, CollectionConditions.containsAll(expected));
    }

    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainAnyValues(Iterable<? extends VALUE> unexpected) {
        return new MapElementsCondition<>(VALUES, CollectionConditions.doesNotContainAny(unexpected));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAllEntries(Map<? extends KEY, ? extends VALUE> expected) {
        return new MapElementsCondition<>(ENTRY_CONTENTS, CollectionConditions.containsAll(expectedEntries(expected, "expected entries")));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAllEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        return new MapElementsCondition<>(ENTRY_CONTENTS, CollectionConditions.doesNotContainAll(expectedEntries(unexpected, "unexpected entries")));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAnyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        return new MapElementsCondition<>(ENTRY_CONTENTS, CollectionConditions.containsAny(expectedEntries(expected, "expected entries")));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAnyEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        return new MapElementsCondition<>(ENTRY_CONTENTS, CollectionConditions.doesNotContainAny(expectedEntries(unexpected, "unexpected entries")));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsExactlyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        return new MapElementsCondition<>(ENTRY_CONTENTS, CollectionConditions.containsExactlyInAnyOrder(expectedEntries(expected, "expected entries")));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainExactlyEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        return new MapElementsCondition<>(ENTRY_CONTENTS, CollectionConditions.doesNotContainExactlyInAnyOrder(expectedEntries(unexpected, "unexpected entries")));
    }
}

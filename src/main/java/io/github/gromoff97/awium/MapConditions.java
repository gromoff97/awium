package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.ConditionResult.Context;
import io.github.gromoff97.awium.ConditionResult.Reference;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.CheckedCalls.call;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.compose;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.mapValue;
import static io.github.gromoff97.awium.ConditionSupport.nonEmpty;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.selected;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static io.github.gromoff97.awium.ConditionSupport.validateRange;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.ValueMatching.containsAll;
import static io.github.gromoff97.awium.ValueMatching.equal;
import static io.github.gromoff97.awium.ValueMatching.exactly;
import static io.github.gromoff97.awium.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.ValueMatching.sameDistinctElements;
import static io.github.gromoff97.awium.ValueRendering.snapshot;
import static io.github.gromoff97.awium.ValueRendering.stringValue;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

@SuppressWarnings("varargs")
public final class MapConditions {

    public static final SelectedCondition<Map<?, ?>> singleEntry = selected("map has a single entry", actual -> {
        if (actual == null) {
            return unsatisfied("map was null");
        }
        int size = call(actual::size);
        return size == 1
                ? satisfied(firstEntry(actual))
                : unsatisfied("map size was " + size);
    });
    public static final PreservingCondition<Map<?, ?>> empty = sized(0, size -> size == 0,
            "map is empty");
    public static final PreservingCondition<Map<?, ?>> nonEmpty = sized(0, size -> size > 0,
            "map is not empty");

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

    public static PreservingCondition<Map<?, ?>> sameSizeAs(Map<?, ?> expected) {
        requireNonNull(expected, "expected map must not be null");
        return preserving("map has the same size as expected map",
                "map size did not match expected map", expectedReference(expected),
                actual -> {
                    int size = call(actual::size);
                    return size == call(expected::size);
                });
    }

    public static <KEY, VALUE> SelectedCondition<Map<? extends KEY, ? extends VALUE>> singleEntry(
            PreservingCondition<? super Entry<? extends KEY, ? extends VALUE>> nested) {
        return selected(ConditionSupport.single("map", nested, Map::entrySet,
                () -> nested.preservingSession()));
    }

    public static <KEY, VALUE, RESULT> Condition<Map<KEY, VALUE>, RESULT> singleEntry(Condition<? super Entry<KEY, VALUE>, ? extends RESULT> nested) {
        return ConditionSupport.single("map", nested, Map::entrySet,
                () -> nested.newSession());
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allEntries(BiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map entries match", "not all map entries matched",
                actual -> matchesAll(call(actual::entrySet),
                        entry -> entryMatches(entry, predicate)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> anyEntry(BiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map entry matches", "no map entry matched",
                actual -> matchesAny(call(actual::entrySet),
                        entry -> entryMatches(entry, predicate)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> noEntry(BiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map entry matches", "a map entry matched",
                actual -> !matchesAny(call(actual::entrySet),
                        entry -> entryMatches(entry, predicate)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allKeys(Predicate<? super KEY> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map keys match", "not all map keys matched", actual -> matchesAll(call(actual::keySet), predicate::test));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> anyKey(Predicate<? super KEY> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map key matches", "no map key matched", actual -> matchesAny(call(actual::keySet), predicate::test));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> noKey(Predicate<? super KEY> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map key matches", "a map key matched", actual -> !matchesAny(call(actual::keySet), predicate::test));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> allValues(Predicate<? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map values match", "not all map values matched", actual -> matchesAll(call(actual::values), predicate::test));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> anyValue(Predicate<? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map value matches", "no map value matched", actual -> matchesAny(call(actual::values), predicate::test));
    }

    public static <KEY, VALUE> PreservingCondition<Map<KEY, VALUE>> noValue(Predicate<? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map value matches", "a map value matched", actual -> !matchesAny(call(actual::values), predicate::test));
    }

    public static MapValueCondition<Map<?, ?>> valueFor(Object key) {
        return mapValue(ConditionSupport.condition("map contains the expected key", expectedReference(key), actual -> value(actual, key)));
    }

    public static <VALUE, RESULT> Condition<Map<?, ? extends VALUE>, RESULT> valueFor(Object key,
            Condition<? super VALUE, ? extends RESULT> nested) {
        return compose("map value ", nested, actual -> value(actual, key),
                () -> nested.newSession());
    }

    public static <VALUE> MapValueCondition<Map<?, ? extends VALUE>> valueFor(Object key,
            PreservingCondition<? super VALUE> nested) {
        return mapValue(compose("map value ", nested, actual -> value(actual, key),
                () -> nested.preservingSession()));
    }

    public static SelectedCondition<Map<?, ?>> entryFor(Object key) {
        return selected(ConditionSupport.condition("map contains the expected key", expectedReference(key), actual -> findEntry(actual, key)));
    }

    public static MapValueCondition<Map<?, ?>> onlyValueFor(Object key) {
        return mapValue(ConditionSupport.condition("map contains only the expected key", expectedReference(key), actual -> {
            if (actual == null) {
                return unsatisfied("map was null");
            }
            int size = call(actual::size);
            if (size != 1) {
                return unsatisfied("map size was " + size);
            }
            var entry = firstEntry(actual);
            return equal(call(entry::getKey), key)
                    ? satisfied(call(entry::getValue)) : unsatisfied("map contained a different key");
        }));
    }

    @SafeVarargs
    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsKeys(KEY... expected) {
        List<KEY> keys = snapshot(stream(nonEmpty(expected, "expected keys")));
        return preserving("map contains all expected keys", "map did not contain all expected keys", expectedReference(keys),
                actual -> containsAll(call(actual::keySet), keys, ValueMatching::equal));
    }

    @SafeVarargs
    public static <KEY> PreservingCondition<Map<? super KEY, ?>> doesNotContainKeys(KEY... unexpected) {
        List<KEY> keys = snapshot(stream(nonEmpty(unexpected, "unexpected keys")));
        return preserving("map does not contain unexpected keys", "map contained an unexpected key", unexpectedReference(keys),
                actual -> !matchesAny(call(actual::keySet), value -> matchesAny(keys, key -> equal(value, key))));
    }

    @SafeVarargs
    public static <KEY> PreservingCondition<Map<? super KEY, ?>> containsOnlyKeys(KEY... expected) {
        List<KEY> keys = snapshot(stream(requireNonNull(expected, "expected keys must not be null")));
        return preserving("map contains only the expected keys", "map did not contain only the expected keys", expectedReference(keys),
                actual -> sameDistinctElements(call(actual::keySet), keys));
    }

    @SafeVarargs
    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> containsValues(VALUE... expected) {
        List<VALUE> values = snapshot(stream(nonEmpty(expected, "expected values")));
        return preserving("map contains all expected values", "map did not contain all expected values", expectedReference(values),
                actual -> containsAll(call(actual::values), values, ValueMatching::equal));
    }

    @SafeVarargs
    public static <VALUE> PreservingCondition<Map<?, ? super VALUE>> doesNotContainValues(VALUE... unexpected) {
        List<VALUE> values = snapshot(stream(nonEmpty(unexpected, "unexpected values")));
        return preserving("map does not contain unexpected values", "map contained an unexpected value", unexpectedReference(values),
                actual -> !matchesAny(call(actual::values), value -> matchesAny(values, candidate -> equal(value, candidate))));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsEntries(KEY key, VALUE value) {
        return preserving("map contains expected entry", "map did not contain expected entry", expectedReference(new Object[]{key, value}),
                actual -> matchesAny(call(actual::entrySet), entry -> entryMatches(entry, key, value)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainEntries(KEY key, VALUE value) {
        return preserving("map does not contain expected entry", "map contained expected entry", expectedReference(new Object[]{key, value}),
                actual -> !matchesAny(call(actual::entrySet), entry -> entryMatches(entry, key, value)));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = new ExpectedEntries(nonEmpty(expected, "expected entries"));
        return preserving("map contains all expected entries", "map did not contain all expected entries", expectedReference(entries),
                actual -> containsAll(call(actual::entrySet), entries.entries(),
                        MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainAllEntries(Map<? extends KEY, ? extends VALUE> unexpected) {
        var entries = new ExpectedEntries(nonEmpty(unexpected, "expected entries"));
        return preserving("map does not contain all expected entries", "map contained all expected entries", expectedReference(entries),
                actual -> !containsAll(call(actual::entrySet), entries.entries(),
                        MapConditions::entryMatches));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsAnyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = new ExpectedEntries(nonEmpty(expected, "expected entries"));
        return preserving("map contains an expected entry", "map did not contain an expected entry", expectedReference(entries),
                actual -> matchesAny(call(actual::entrySet), value ->
                        matchesAny(entries.entries(), candidate -> entryMatches(value, candidate))));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = new ExpectedEntries(nonEmpty(expected, "expected entries"));
        return preserving("map does not contain an expected entry", "map contained an expected entry", expectedReference(entries),
                actual -> !matchesAny(call(actual::entrySet), value ->
                        matchesAny(entries.entries(), candidate -> entryMatches(value, candidate))));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> containsExactlyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = new ExpectedEntries(requireNonNull(expected, "expected entries must not be null"));
        return preserving("map contains exactly the expected entries", "map did not contain exactly the expected entries", expectedReference(entries),
                actual -> exactContent(actual, entries));
    }

    public static <KEY, VALUE> PreservingCondition<Map<? super KEY, ? super VALUE>> doesNotContainExactlyEntries(Map<? extends KEY, ? extends VALUE> expected) {
        var entries = new ExpectedEntries(requireNonNull(expected, "expected entries must not be null"));
        return preserving("map does not contain exactly the expected entries", "map contained exactly the expected entries", expectedReference(entries),
                actual -> !exactContent(actual, entries));
    }

    private static <KEY, VALUE> Entry<KEY, VALUE> firstEntry(Map<KEY, VALUE> actual) throws Exception {
        var entries = call(actual::entrySet);
        var iterator = call(entries::iterator);
        return call(iterator::next);
    }

    private static <VALUE> ConditionResult<VALUE> value(Map<?, ? extends VALUE> actual, Object key) throws Exception {
        return findEntry(actual, key).mapSatisfied(Entry::getValue);
    }

    private static <KEY, VALUE> ConditionResult<Entry<KEY, VALUE>> findEntry(Map<KEY, VALUE> actual, Object key) throws Exception {
        if (actual != null) {
            var entries = call(actual::entrySet);
            for (var iterator = call(entries::iterator); call(iterator::hasNext);) {
                Entry<KEY, VALUE> entry = call(iterator::next);
                if (equal(call(entry::getKey), key)) {
                    return satisfied(entry);
                }
            }
        }
        return ConditionResult.<Entry<KEY, VALUE>>unsatisfied(actual == null ? "map was null" : "map did not contain the expected key")
                .withContext(new Context.Expectation("map contains the expected key", expectedReference(key)));
    }

    private static PreservingCondition<Map<?, ?>> sized(int bound, IntPredicate matches,
            String description) {
        return ConditionSupport.sized("map", bound, matches, description, Map::size);
    }

    private static <ENTRIES extends Map<?, ?>> PreservingCondition<ENTRIES> preserving(String description, String mismatch,
            CheckedFunction<? super ENTRIES, Boolean> matches) {
        return preservingNonNull("map", description, mismatch, matches);
    }

    private static <ENTRIES extends Map<?, ?>> PreservingCondition<ENTRIES> preserving(String description, String mismatch,
            Reference<?> reference, CheckedFunction<? super ENTRIES, Boolean> matches) {
        return preservingNonNull("map", description, mismatch, reference, matches);
    }

    private static boolean exactContent(Map<?, ?> actual, ExpectedEntries expected) throws Exception {
        if (call(actual::size) != expected.entries().size()) {
            return false;
        }
        var entries = call(actual::entrySet);
        return exactly(call(entries::iterator), expected.entries(), MapConditions::entryMatches);
    }

    // Preserve encounter order and distinct entries without calling user equals/hashCode.
    private record ExpectedEntries(List<? extends Entry<?, ?>> entries) {
        ExpectedEntries(Map<?, ?> values) {
            this(values.entrySet().stream().map(SimpleImmutableEntry::new).toList());
        }

        @Override
        public String toString() {
            return entries.stream().map(entry -> stringValue(entry.getKey()) + "=" + stringValue(entry.getValue()))
                    .collect(joining(", ", "{", "}"));
        }
    }

    private static boolean entryMatches(Entry<?, ?> actual, Entry<?, ?> expected) throws Exception {
        return entryMatches(actual, expected.getKey(), expected.getValue());
    }

    private static <KEY, VALUE> boolean entryMatches(Entry<KEY, VALUE> entry, BiPredicate<? super KEY, ? super VALUE> predicate) throws Exception {
        KEY key = call(entry::getKey);
        VALUE value = call(entry::getValue);
        return call(() -> predicate.test(key, value));
    }

    private static boolean entryMatches(Entry<?, ?> actual, Object expectedKey, Object expectedValue) throws Exception {
        return equal(call(actual::getKey), expectedKey) && equal(call(actual::getValue), expectedValue);
    }

}

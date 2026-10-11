package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.NarrowingCondition;
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

    public static final SelectedCondition<Map<?, ?>, Map<?, ?>> singleEntry = selected("map has a single entry", actual -> {
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
        Map<?, ?> entries = requireNonNull(expected, "expected map must not be null");
        return preserving("map has the same size as expected map",
                "map size did not match expected map", expectedReference(entries),
                actual -> {
                    int size = call(actual::size);
                    return size == call(entries::size);
                });
    }

    public static <Key, Value> Condition<Map<Key, Value>, Entry<Key, Value>> singleEntry(PreservingCondition<? super Entry<Key, Value>> nested) {
        return ConditionSupport.single("map", nested, Map::entrySet,
                () -> nested.preservingSession());
    }

    public static <Key, Value, Expected extends Entry<Key, Value>> Condition<Map<Key, Value>, Entry<Key, Value>> singleEntry(ExpectedCondition<Expected> nested) {
        return ConditionSupport.single("map", nested, Map::entrySet,
                () -> nested.preservingSession());
    }

    public static <Key, Value, Result extends Entry<Key, Value>> Condition<Map<Key, Value>, Result> singleEntry(NarrowingCondition<Result> nested) {
        return ConditionSupport.single("map", nested, Map::entrySet,
                () -> nested.newSession());
    }

    public static <Key, Value, Result> Condition<Map<Key, Value>, Result> singleEntry(Condition<? super Entry<Key, Value>, ? extends Result> nested) {
        return ConditionSupport.single("map", nested, Map::entrySet,
                () -> nested.newSession());
    }

    public static <Key, Value> PreservingCondition<Map<Key, Value>> allEntries(BiPredicate<? super Key, ? super Value> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map entries match", "not all map entries matched",
                actual -> matchesAll(call(actual::entrySet),
                        entry -> entryMatches(entry, predicate)));
    }

    public static <Key, Value> PreservingCondition<Map<Key, Value>> anyEntry(BiPredicate<? super Key, ? super Value> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map entry matches", "no map entry matched",
                actual -> matchesAny(call(actual::entrySet),
                        entry -> entryMatches(entry, predicate)));
    }

    public static <Key, Value> PreservingCondition<Map<Key, Value>> noEntry(BiPredicate<? super Key, ? super Value> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map entry matches", "a map entry matched",
                actual -> !matchesAny(call(actual::entrySet),
                        entry -> entryMatches(entry, predicate)));
    }

    public static <Key, Value> PreservingCondition<Map<Key, Value>> allKeys(Predicate<? super Key> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map keys match", "not all map keys matched", actual -> matchesAll(call(actual::keySet), predicate::test));
    }

    public static <Key, Value> PreservingCondition<Map<Key, Value>> anyKey(Predicate<? super Key> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map key matches", "no map key matched", actual -> matchesAny(call(actual::keySet), predicate::test));
    }

    public static <Key, Value> PreservingCondition<Map<Key, Value>> noKey(Predicate<? super Key> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map key matches", "a map key matched", actual -> !matchesAny(call(actual::keySet), predicate::test));
    }

    public static <Key, Value> PreservingCondition<Map<Key, Value>> allValues(Predicate<? super Value> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map values match", "not all map values matched", actual -> matchesAll(call(actual::values), predicate::test));
    }

    public static <Key, Value> PreservingCondition<Map<Key, Value>> anyValue(Predicate<? super Value> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map value matches", "no map value matched", actual -> matchesAny(call(actual::values), predicate::test));
    }

    public static <Key, Value> PreservingCondition<Map<Key, Value>> noValue(Predicate<? super Value> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map value matches", "a map value matched", actual -> !matchesAny(call(actual::values), predicate::test));
    }

    public static <Key, Value> Condition<Map<Key, Value>, Value> valueFor(Key key) {
        return ConditionSupport.condition("map contains the expected key", expectedReference(key), actual -> value(actual, key));
    }

    public static <Key, Value, Result> Condition<Map<Key, Value>, Result> valueFor(Key key,
            Condition<? super Value, ? extends Result> nested) {
        return compose("map value ", nested, actual -> value(actual, key),
                () -> nested.newSession());
    }

    public static <Key, Value> Condition<Map<Key, Value>, Value> valueFor(Key key,
            PreservingCondition<? super Value> nested) {
        return compose("map value ", nested, actual -> value(actual, key),
                () -> nested.preservingSession());
    }

    public static <Key, Value, Expected extends Value> Condition<Map<Key, Value>, Value> valueFor(Key key, ExpectedCondition<Expected> nested) {
        return compose("map value ", nested, actual -> value(actual, key),
                () -> nested.preservingSession());
    }

    public static <Key, Value, Result extends Value> Condition<Map<Key, Value>, Result> valueFor(Key key, NarrowingCondition<Result> nested) {
        return compose("map value ", nested, actual -> value(actual, key),
                () -> nested.newSession());
    }

    public static <Key, Value> Condition<Map<Key, Value>, Entry<Key, Value>> entryFor(Key key) {
        return ConditionSupport.condition("map contains the expected key", expectedReference(key), actual -> findEntry(actual, key));
    }

    public static <Key, Value> Condition<Map<Key, Value>, Value> onlyValueFor(Key key) {
        return ConditionSupport.condition("map contains only the expected key", expectedReference(key), actual -> {
            if (actual == null) {
                return unsatisfied("map was null");
            }
            int size = call(actual::size);
            if (size != 1) {
                return unsatisfied("map size was " + size);
            }
            Entry<Key, Value> entry = firstEntry(actual);
            return equal(call(entry::getKey), key)
                    ? satisfied(call(entry::getValue)) : unsatisfied("map contained a different key");
        });
    }

    @SafeVarargs
    public static <Key> PreservingCondition<Map<? super Key, ?>> containsKeys(Key... expected) {
        List<Key> keys = snapshot(stream(nonEmpty(expected, "expected keys")));
        return preserving("map contains all expected keys", "map did not contain all expected keys", expectedReference(keys),
                actual -> containsAll(call(actual::keySet), keys, ValueMatching::equal));
    }

    @SafeVarargs
    public static <Key> PreservingCondition<Map<? super Key, ?>> doesNotContainKeys(Key... unexpected) {
        List<Key> keys = snapshot(stream(nonEmpty(unexpected, "unexpected keys")));
        return preserving("map does not contain unexpected keys", "map contained an unexpected key", unexpectedReference(keys),
                actual -> !matchesAny(call(actual::keySet), value -> matchesAny(keys, key -> equal(value, key))));
    }

    @SafeVarargs
    public static <Key> PreservingCondition<Map<? super Key, ?>> containsOnlyKeys(Key... expected) {
        List<Key> keys = snapshot(stream(requireNonNull(expected, "expected keys must not be null")));
        return preserving("map contains only the expected keys", "map did not contain only the expected keys", expectedReference(keys),
                actual -> sameDistinctElements(call(actual::keySet), keys));
    }

    @SafeVarargs
    public static <Value> PreservingCondition<Map<?, ? super Value>> containsValues(Value... expected) {
        List<Value> values = snapshot(stream(nonEmpty(expected, "expected values")));
        return preserving("map contains all expected values", "map did not contain all expected values", expectedReference(values),
                actual -> containsAll(call(actual::values), values, ValueMatching::equal));
    }

    @SafeVarargs
    public static <Value> PreservingCondition<Map<?, ? super Value>> doesNotContainValues(Value... unexpected) {
        List<Value> values = snapshot(stream(nonEmpty(unexpected, "unexpected values")));
        return preserving("map does not contain unexpected values", "map contained an unexpected value", unexpectedReference(values),
                actual -> !matchesAny(call(actual::values), value -> matchesAny(values, candidate -> equal(value, candidate))));
    }

    public static <Key, Value> PreservingCondition<Map<? super Key, ? super Value>> containsEntries(Key key, Value value) {
        return preserving("map contains expected entry", "map did not contain expected entry", expectedReference(new Object[]{key, value}),
                actual -> matchesAny(call(actual::entrySet), entry -> entryMatches(entry, key, value)));
    }

    public static <Key, Value> PreservingCondition<Map<? super Key, ? super Value>> doesNotContainEntries(Key key, Value value) {
        return preserving("map does not contain expected entry", "map contained expected entry", expectedReference(new Object[]{key, value}),
                actual -> !matchesAny(call(actual::entrySet), entry -> entryMatches(entry, key, value)));
    }

    public static <Key, Value> PreservingCondition<Map<? super Key, ? super Value>> containsEntries(Map<? extends Key, ? extends Value> expected) {
        var entries = new ExpectedEntries(nonEmpty(expected, "expected entries"));
        return preserving("map contains all expected entries", "map did not contain all expected entries", expectedReference(entries),
                actual -> containsAll(call(actual::entrySet), entries.entries(),
                        MapConditions::entryMatches));
    }

    public static <Key, Value> PreservingCondition<Map<? super Key, ? super Value>> doesNotContainAllEntries(Map<? extends Key, ? extends Value> unexpected) {
        var entries = new ExpectedEntries(nonEmpty(unexpected, "expected entries"));
        return preserving("map does not contain all expected entries", "map contained all expected entries", expectedReference(entries),
                actual -> !containsAll(call(actual::entrySet), entries.entries(),
                        MapConditions::entryMatches));
    }

    public static <Key, Value> PreservingCondition<Map<? super Key, ? super Value>> containsAnyEntries(Map<? extends Key, ? extends Value> expected) {
        var entries = new ExpectedEntries(nonEmpty(expected, "expected entries"));
        return preserving("map contains an expected entry", "map did not contain an expected entry", expectedReference(entries),
                actual -> matchesAny(call(actual::entrySet), value ->
                        matchesAny(entries.entries(), candidate -> entryMatches(value, candidate))));
    }

    public static <Key, Value> PreservingCondition<Map<? super Key, ? super Value>> doesNotContainEntries(Map<? extends Key, ? extends Value> expected) {
        var entries = new ExpectedEntries(nonEmpty(expected, "expected entries"));
        return preserving("map does not contain an expected entry", "map contained an expected entry", expectedReference(entries),
                actual -> !matchesAny(call(actual::entrySet), value ->
                        matchesAny(entries.entries(), candidate -> entryMatches(value, candidate))));
    }

    public static <Key, Value> PreservingCondition<Map<? super Key, ? super Value>> containsExactlyEntries(Map<? extends Key, ? extends Value> expected) {
        var entries = new ExpectedEntries(requireNonNull(expected, "expected entries must not be null"));
        return preserving("map contains exactly the expected entries", "map did not contain exactly the expected entries", expectedReference(entries),
                actual -> exactContent(actual, entries));
    }

    public static <Key, Value> PreservingCondition<Map<? super Key, ? super Value>> doesNotContainExactlyEntries(Map<? extends Key, ? extends Value> expected) {
        var entries = new ExpectedEntries(requireNonNull(expected, "expected entries must not be null"));
        return preserving("map does not contain exactly the expected entries", "map contained exactly the expected entries", expectedReference(entries),
                actual -> !exactContent(actual, entries));
    }

    private static <Key, Value> Entry<Key, Value> firstEntry(Map<Key, Value> actual) throws Exception {
        var entries = call(actual::entrySet);
        var iterator = call(entries::iterator);
        return call(iterator::next);
    }

    private static <Key, Value> ConditionResult<Value> value(Map<Key, Value> actual, Key key) throws Exception {
        return findEntry(actual, key).mapSatisfied(Entry::getValue);
    }

    private static <Key, Value> ConditionResult<Entry<Key, Value>> findEntry(Map<Key, Value> actual, Key key) throws Exception {
        if (actual != null) {
            var entries = call(actual::entrySet);
            for (var iterator = call(entries::iterator); call(iterator::hasNext);) {
                Entry<Key, Value> entry = call(iterator::next);
                if (equal(call(entry::getKey), key)) {
                    return satisfied(entry);
                }
            }
        }
        return ConditionResult.<Entry<Key, Value>>unsatisfied(actual == null ? "map was null" : "map did not contain the expected key")
                .withContext(new Context.Expectation("map contains the expected key", expectedReference(key)));
    }

    private static PreservingCondition<Map<?, ?>> sized(int bound, IntPredicate matches,
            String description) {
        return ConditionSupport.sized("map", bound, matches, description, Map::size);
    }

    private static <Entries extends Map<?, ?>> PreservingCondition<Entries> preserving(String description, String mismatch,
            CheckedFunction<? super Entries, Boolean> matches) {
        return preservingNonNull("map", description, mismatch, matches);
    }

    private static <Entries extends Map<?, ?>> PreservingCondition<Entries> preserving(String description, String mismatch,
            Reference<?> reference, CheckedFunction<? super Entries, Boolean> matches) {
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

    private static <Key, Value> boolean entryMatches(Entry<Key, Value> entry, BiPredicate<? super Key, ? super Value> predicate) throws Exception {
        Key key = call(entry::getKey);
        Value value = call(entry::getValue);
        return call(() -> predicate.test(key, value));
    }

    private static boolean entryMatches(Entry<?, ?> actual, Object expectedKey, Object expectedValue) throws Exception {
        return equal(call(actual::getKey), expectedKey) && equal(call(actual::getValue), expectedValue);
    }

}

package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.CheckedBiPredicate;
import io.github.gromoff97.awium.conditioning.CheckedPredicate;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionResults.copy;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionResults.failure;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionResults.preserve;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.containsAll;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.equal;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.exactly;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.condition;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.literalExplanation;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("varargs")
public final class MapCondition {

    public static final SingleEntry singleEntry = new SingleEntry();
    public static final PreservingCondition<Map<?, ?>> noEntries = sized(0, size -> size == 0,
            "map is empty", "map was non-empty");
    public static final PreservingCondition<Map<?, ?>> hasEntries = sized(0, size -> size > 0,
            "map is not empty", "map was empty");

    private MapCondition() {
        throw new AssertionError("Utility class");
    }

    public static PreservingCondition<Map<?, ?>> entryCount(int expected) {
        return sized(expected, actual -> actual == expected,
                "map size is " + expected, "map size was not " + expected);
    }

    public static PreservingCondition<Map<?, ?>> entryCountNot(int unexpected) {
        return sized(unexpected, actual -> actual != unexpected,
                "map size is not " + unexpected, "map size was " + unexpected);
    }

    public static PreservingCondition<Map<?, ?>> entryCountGreaterThan(int lowerBound) {
        return sized(lowerBound, actual -> actual > lowerBound,
                "map size is greater than " + lowerBound,
                "map size was not greater than " + lowerBound);
    }

    public static PreservingCondition<Map<?, ?>> entryCountAtLeast(int lowerBound) {
        return sized(lowerBound, actual -> actual >= lowerBound,
                "map size is at least " + lowerBound,
                "map size was less than " + lowerBound);
    }

    public static PreservingCondition<Map<?, ?>> entryCountLessThan(int upperBound) {
        return sized(upperBound, actual -> actual < upperBound,
                "map size is less than " + upperBound,
                "map size was not less than " + upperBound);
    }

    public static PreservingCondition<Map<?, ?>> entryCountAtMost(int upperBound) {
        return sized(upperBound, actual -> actual <= upperBound,
                "map size is at most " + upperBound,
                "map size was greater than " + upperBound);
    }

    public static PreservingCondition<Map<?, ?>> entryCountBetween(int lowerBound, int upperBound) {
        validateRange(lowerBound, upperBound);
        return sized(lowerBound, actual -> actual >= lowerBound && actual <= upperBound,
                "map size is between " + lowerBound + " and " + upperBound,
                "map size was outside " + lowerBound + ".." + upperBound);
    }

    public static PreservingCondition<Map<?, ?>> sameEntryCountAs(Map<?, ?> expected) {
        return entryCount(requireNonNull(expected, "expected map must not be null").size());
    }

    public static <K, V> Condition<Map<K, V>, Map.Entry<K, V>> singleEntry(
            CheckedBiPredicate<? super K, ? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return condition("map has a single matching entry", actual -> selectSingle(actual, predicate));
    }

    public static <K, V> Condition<Map<K, V>, K> singleKey(CheckedPredicate<? super K> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return condition("map has a single matching key", actual -> {
            Evaluation<Map.Entry<K, V>> selected = selectSingle(actual, (key, value) -> predicate.test(key));
            return selected.status() == Evaluation.Status.SATISFIED
                    ? satisfied(selected.result().getKey()) : failure(selected);
        });
    }

    public static <K, V> Condition<Map<K, V>, V> singleValue(CheckedPredicate<? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return condition("map has a single matching value", actual -> {
            Evaluation<Map.Entry<K, V>> selected = selectSingle(actual, (key, value) -> predicate.test(value));
            return selected.status() == Evaluation.Status.SATISFIED
                    ? satisfied(selected.result().getValue()) : failure(selected);
        });
    }

    public static <K, V> PreservingCondition<Map<K, V>> allEntries(
            CheckedBiPredicate<? super K, ? super V> predicate) {
        return quantify("all map entries match", "not all map entries matched", predicate, Quantifier.ALL);
    }

    public static <K, V> PreservingCondition<Map<K, V>> anyEntry(
            CheckedBiPredicate<? super K, ? super V> predicate) {
        return quantify("any map entry matches", "no map entry matched", predicate, Quantifier.ANY);
    }

    public static <K, V> PreservingCondition<Map<K, V>> noEntry(
            CheckedBiPredicate<? super K, ? super V> predicate) {
        return quantify("no map entry matches", "a map entry matched", predicate, Quantifier.NONE);
    }

    public static <K, V> PreservingCondition<Map<K, V>> allKeys(CheckedPredicate<? super K> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map keys match", "not all map keys matched", actual -> matchesAll(actual.keySet(), predicate));
    }

    public static <K, V> PreservingCondition<Map<K, V>> anyKey(CheckedPredicate<? super K> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map key matches", "no map key matched", actual -> matchesAny(actual.keySet(), predicate));
    }

    public static <K, V> PreservingCondition<Map<K, V>> noKey(CheckedPredicate<? super K> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map key matches", "a map key matched", actual -> !matchesAny(actual.keySet(), predicate));
    }

    public static <K, V> PreservingCondition<Map<K, V>> allValues(CheckedPredicate<? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map values match", "not all map values matched", actual -> matchesAll(actual.values(), predicate));
    }

    public static <K, V> PreservingCondition<Map<K, V>> anyValue(CheckedPredicate<? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map value matches", "no map value matched", actual -> matchesAny(actual.values(), predicate));
    }

    public static <K, V> PreservingCondition<Map<K, V>> noValue(CheckedPredicate<? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map value matches", "a map value matched", actual -> !matchesAny(actual.values(), predicate));
    }

    public static <K, V> Condition<Map<K, V>, V> valueFor(K key) {
        return condition("map contains the expected key", actual -> {
            Evaluation<Map.Entry<K, V>> entry = findEntry(actual, key);
            return entry.status() == Evaluation.Status.SATISFIED
                    ? satisfied(entry.result().getValue()) : failure(entry);
        });
    }

    public static <K, V, R> Condition<Map<K, V>, R> valueFor(K key,
            Condition<? super V, ? extends R> nested) {
        requireNonNull(nested, "condition must not be null");
        return condition("map value " + nested.description(), actual -> {
            Evaluation<Map.Entry<K, V>> entry = findEntry(actual, key);
            if (entry.status() != Evaluation.Status.SATISFIED) {
                return failure(entry);
            }
            return copy(nested.evaluate(entry.result().getValue()));
        });
    }

    public static <K, V> Condition<Map<K, V>, V> valueFor(K key,
            PreservingCondition<? super V> nested) {
        requireNonNull(nested, "condition must not be null");
        return MapCondition.<K, V, V>valueFor(key, preserve(nested.delegate()));
    }

    public static <K, V> Condition<Map<K, V>, Map.Entry<K, V>> entry(K key) {
        return condition("map contains the expected key", actual -> findEntry(actual, key));
    }

    public static <K, V> Condition<Map<K, V>, V> onlyValueFor(K key) {
        return condition("map contains only the expected key", actual -> {
            if (actual == null) {
                return unsatisfied("map was null");
            }
            if (actual.size() != 1) {
                return unsatisfied("map size was " + actual.size());
            }
            Map.Entry<K, V> entry = actual.entrySet().iterator().next();
            return equal(entry.getKey(), key)
                    ? satisfied(entry.getValue()) : unsatisfied("map contained a different key");
        });
    }

    public static <K> PreservingCondition<Map<? super K, ?>> containsKey(K expected) {
        return entryCondition(entry -> equal(entry.getKey(), expected), true, "key");
    }

    public static <K> PreservingCondition<Map<? super K, ?>> doesNotContainKey(K expected) {
        return entryCondition(entry -> equal(entry.getKey(), expected), false, "key");
    }

    @SafeVarargs
    public static <K> PreservingCondition<Map<? super K, ?>> containsKeys(K... expected) {
        List<K> keys = asList(nonEmpty(expected, "expected keys"));
        return preserving("map contains all expected keys", "map did not contain all expected keys",
                actual -> matchesAll(keys, key -> matchesAny(actual.keySet(), value -> equal(value, key))));
    }

    @SafeVarargs
    public static <K> PreservingCondition<Map<? super K, ?>> doesNotContainKeys(K... unexpected) {
        List<K> keys = asList(nonEmpty(unexpected, "unexpected keys"));
        return preserving("map does not contain unexpected keys", "map contained an unexpected key",
                actual -> !matchesAny(actual.keySet(), value -> matchesAny(keys, key -> equal(value, key))));
    }

    @SafeVarargs
    public static <K> PreservingCondition<Map<? super K, ?>> containsOnlyKeys(K... expected) {
        List<K> keys = asList(nonNull(expected, "expected keys"));
        return preserving("map contains only the expected keys", "map did not contain only the expected keys",
                actual -> sameDistinctElements(actual.keySet(), keys));
    }

    public static <V> PreservingCondition<Map<?, ? super V>> containsValue(V expected) {
        return entryCondition(entry -> equal(entry.getValue(), expected), true, "value");
    }

    public static <V> PreservingCondition<Map<?, ? super V>> doesNotContainValue(V expected) {
        return entryCondition(entry -> equal(entry.getValue(), expected), false, "value");
    }

    @SafeVarargs
    public static <V> PreservingCondition<Map<?, ? super V>> containsValues(V... expected) {
        List<V> values = asList(nonEmpty(expected, "expected values"));
        return preserving("map contains all expected values", "map did not contain all expected values",
                actual -> containsAll(actual.values(), new ArrayList<>(values), ValueMatching::equal));
    }

    @SafeVarargs
    public static <V> PreservingCondition<Map<?, ? super V>> doesNotContainValues(V... unexpected) {
        List<V> values = asList(nonEmpty(unexpected, "unexpected values"));
        return preserving("map does not contain unexpected values", "map contained an unexpected value",
                actual -> !matchesAny(actual.values(), value -> matchesAny(values, candidate -> equal(value, candidate))));
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsEntry(K key, V value) {
        return entryCondition(entry -> entryMatches(entry, key, value), true, "entry");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> doesNotContainEntry(K key, V value) {
        return entryCondition(entry -> entryMatches(entry, key, value), false, "entry");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsAllEntriesOf(
            Map<? extends K, ? extends V> expected) {
        return membership(expected, Quantifier.ALL, true);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> doesNotContainAllEntriesOf(
            Map<? extends K, ? extends V> unexpected) {
        return membership(unexpected, Quantifier.ALL, false);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsAnyEntriesOf(
            Map<? extends K, ? extends V> expected) {
        return membership(expected, Quantifier.ANY, true);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsNoEntriesOf(
            Map<? extends K, ? extends V> expected) {
        return membership(expected, Quantifier.ANY, false);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsExactlyEntriesOf(
            Map<? extends K, ? extends V> expected) {
        Map<? extends K, ? extends V> entries = nonNull(expected, "expected entries");
        return preserving("map contains exactly the expected entries", "map did not contain exactly the expected entries",
                actual -> exactContent(actual, entries));
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> doesNotContainExactlyEntriesOf(
            Map<? extends K, ? extends V> expected) {
        Map<? extends K, ? extends V> entries = nonNull(expected, "expected entries");
        return preserving("map does not contain exactly the expected entries", "map contained exactly the expected entries",
                actual -> !exactContent(actual, entries));
    }

    private static <K, V> Evaluation<Map.Entry<K, V>> selectSingle(Map<K, V> actual,
            CheckedBiPredicate<? super K, ? super V> predicate) throws Exception {
        if (actual == null) {
            return unsatisfied("map was null");
        }
        Map.Entry<K, V> selected = null;
        for (Map.Entry<K, V> entry : actual.entrySet()) {
            if (!predicate.test(entry.getKey(), entry.getValue())) {
                continue;
            }
            if (selected != null) {
                return unsatisfied("more than one map entry matched");
            }
            selected = entry;
        }
        return selected == null ? unsatisfied("no map entry matched") : satisfied(selected);
    }

    private static <K, V> Evaluation<Map.Entry<K, V>> findEntry(Map<K, V> actual, K key) {
        if (actual == null) {
            return unsatisfied("map was null");
        }
        for (Map.Entry<K, V> entry : actual.entrySet()) {
            if (equal(entry.getKey(), key)) {
                return satisfied(entry);
            }
        }
        return unsatisfied("map did not contain the expected key");
    }

    private static <K, V> PreservingCondition<Map<K, V>> quantify(String description, String mismatch,
            CheckedBiPredicate<? super K, ? super V> predicate, Quantifier quantifier) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving(description, mismatch, actual -> switch (quantifier) {
                case ALL -> matchesAll(actual.entrySet(), entry -> predicate.test(entry.getKey(), entry.getValue()));
                case ANY -> matchesAny(actual.entrySet(), entry -> predicate.test(entry.getKey(), entry.getValue()));
                case NONE -> !matchesAny(actual.entrySet(), entry -> predicate.test(entry.getKey(), entry.getValue()));
            });
    }

    private static <M extends Map<?, ?>> PreservingCondition<M> entryCondition(
            CheckedPredicate<Map.Entry<?, ?>> matches, boolean positive, String expected) {
        return preserving("map " + (positive ? "contains " : "does not contain ") + "expected " + expected,
                "map " + (positive ? "did not contain" : "contained") + " expected " + expected,
                actual -> matchesAny(actual.entrySet(), matches) == positive);
    }

    private static <K, V> PreservingCondition<Map<? super K, ? super V>> membership(
            Map<? extends K, ? extends V> expected, Quantifier quantifier, boolean positive) {
        Map<? extends K, ? extends V> entries = nonEmpty(expected, "expected entries");
        String target = quantifier == Quantifier.ALL ? "all expected entries" : "an expected entry";
        return preserving("map " + (positive ? "contains " : "does not contain ") + target,
                "map " + (positive ? "did not contain " : "contained ") + target,
                actual -> {
                    boolean matched = quantifier == Quantifier.ALL
                            ? containsAll(actual.entrySet(), new ArrayList<>(entries.entrySet()), MapCondition::entryMatches)
                            : matchesAny(actual.entrySet(), value -> matchesAny(entries.entrySet(), candidate -> entryMatches(value, candidate)));
                    return matched == positive;
                });
    }

    private static PreservingCondition<Map<?, ?>> sized(int bound, java.util.function.IntPredicate matches,
            String description, String mismatch) {
        if (bound < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        return new PreservingCondition<>(condition(description, actual -> {
            if (actual == null) {
                return unsatisfied("map was null");
            }
            int size = actual.size();
            return matches.test(size) ? satisfied(actual) : unsatisfied("map size was " + size);
        }));
    }

    private static <M extends Map<?, ?>> PreservingCondition<M> preserving(String description, String mismatch,
            CheckedPredicate<? super M> matches) {
        return new PreservingCondition<>(condition(description, actual -> {
            if (actual == null) {
                return unsatisfied("map was null");
            }
            return matches.test(actual) ? satisfied(actual) : unsatisfied(mismatch);
        }));
    }

    private static boolean exactContent(Map<?, ?> actual, Map<?, ?> expected) throws Exception {
        return actual.size() == expected.size()
                && exactly(actual.entrySet().iterator(), new ArrayList<>(expected.entrySet()), MapCondition::entryMatches);
    }

    private static boolean sameDistinctElements(Collection<?> actual, Collection<?> expected) throws Exception {
        return matchesAll(actual, value -> matchesAny(expected, candidate -> equal(value, candidate)))
                && matchesAll(expected, value -> matchesAny(actual, candidate -> equal(value, candidate)));
    }

    private static boolean entryMatches(Map.Entry<?, ?> actual, Map.Entry<?, ?> expected) {
        return entryMatches(actual, expected.getKey(), expected.getValue());
    }

    private static boolean entryMatches(Map.Entry<?, ?> actual, Object expectedKey, Object expectedValue) {
        return equal(actual.getKey(), expectedKey) && equal(actual.getValue(), expectedValue);
    }

    private static void validateRange(int lowerBound, int upperBound) {
        if (lowerBound < 0 || upperBound < lowerBound) {
            throw new IllegalArgumentException("size range must be non-negative and ordered");
        }
    }

    private static <T> T nonNull(T value, String name) {
        return requireNonNull(value, name + " must not be null");
    }

    private static <E> E[] nonEmpty(E[] values, String name) {
        nonNull(values, name);
        if (values.length == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    private static <K, V> Map<? extends K, ? extends V> nonEmpty(Map<? extends K, ? extends V> values, String name) {
        nonNull(values, name);
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    public static final class SingleEntry {

        private SingleEntry() {
        }

        public Evaluation<Map<?, ?>> evaluate(Map<?, ?> actual) {
            if (actual == null) {
                return unsatisfied("map was null");
            }
            return actual.size() == 1 ? satisfied(actual) : unsatisfied("map size was " + actual.size());
        }

        public String description() {
            return "map has a single entry";
        }

        public Explained because(String explanation) {
            return new Explained(this, explanation);
        }

        public Explained because(String format, Object... arguments) {
            return new Explained(this, formattedExplanation(format, arguments));
        }

        public record Explained(SingleEntry delegate, String explanation) {

            public Explained {
                requireNonNull(delegate, "condition must not be null");
                explanation = literalExplanation(explanation);
            }
        }
    }

    private enum Quantifier { ALL, ANY, NONE }
}

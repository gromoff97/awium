package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
import io.github.gromoff97.awium.fluent.Condition.PreservingCondition;
import io.github.gromoff97.awium.fluent.Condition.PreservingStage;
import io.github.gromoff97.awium.fluent.Condition.ExpectedStage;
import io.github.gromoff97.awium.fluent.Condition.NarrowingStage;
import io.github.gromoff97.awium.fluent.ConditionStage.ResultStage;
import io.github.gromoff97.awium.fluent.Condition.SelectedCondition;
import io.github.gromoff97.awium.sources.Source.MapSource;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.engine.ConditionAssessment.plain;
import static io.github.gromoff97.awium.fluent.ConditionSupport.nonEmpty;
import static io.github.gromoff97.awium.fluent.ConditionSupport.preserve;
import static io.github.gromoff97.awium.fluent.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.fluent.ConditionSupport.validateRange;
import static io.github.gromoff97.awium.fluent.ValueMatching.containsAll;
import static io.github.gromoff97.awium.fluent.ValueMatching.equal;
import static io.github.gromoff97.awium.fluent.ValueMatching.exactly;
import static io.github.gromoff97.awium.fluent.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.fluent.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.fluent.ValueMatching.sameDistinctElements;
import static io.github.gromoff97.awium.fluent.Conditions.condition;
import static io.github.gromoff97.awium.fluent.ConditionRuntime.selected;
import static io.github.gromoff97.awium.fluent.ConditionRuntime.assessedCondition;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("varargs")
public final class MapConditions {

    public static final SelectedCondition<Map<?, ?>, MapSource<?>> singleEntry = selected("map has a single entry", actual -> {
        if (actual == null) {
            return unsatisfied("map was null");
        }
        return actual.size() == 1
                ? satisfied(actual.entrySet().iterator().next())
                : unsatisfied("map size was " + actual.size());
    });
    public static final PreservingCondition<Map<?, ?>> empty = sized(0, size -> size == 0,
            "map is empty");
    public static final PreservingCondition<Map<?, ?>> nonEmpty = sized(0, size -> size > 0,
            "map is not empty");

    private MapConditions() {
        throw new AssertionError("Utility class");
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

    public static PreservingCondition<Map<?, ?>> sameSizeAs(Map<?, ?> expected) {
        return size(requireNonNull(expected, "expected map must not be null").size());
    }

    public static <K, V> Condition<Map<K, V>, Map.Entry<K, V>> singleEntry(BiPredicate<? super K, ? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        Predicate<Map.Entry<K, V>> matches = entry -> predicate.test(entry.getKey(), entry.getValue());
        return condition("map has a single matching entry", actual -> selectSingle(actual, matches));
    }

    public static <K, V> PreservingCondition<Map<K, V>> allEntries(BiPredicate<? super K, ? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map entries match", "not all map entries matched",
                actual -> matchesAll(actual.entrySet(),
                        entry -> predicate.test(entry.getKey(), entry.getValue())));
    }

    public static <K, V> PreservingCondition<Map<K, V>> anyEntry(BiPredicate<? super K, ? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map entry matches", "no map entry matched",
                actual -> matchesAny(actual.entrySet(),
                        entry -> predicate.test(entry.getKey(), entry.getValue())));
    }

    public static <K, V> PreservingCondition<Map<K, V>> noEntry(BiPredicate<? super K, ? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map entry matches", "a map entry matched",
                actual -> !matchesAny(actual.entrySet(),
                        entry -> predicate.test(entry.getKey(), entry.getValue())));
    }

    public static <K, V> PreservingCondition<Map<K, V>> allKeys(Predicate<? super K> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map keys match", "not all map keys matched", actual -> matchesAll(actual.keySet(), predicate));
    }

    public static <K, V> PreservingCondition<Map<K, V>> anyKey(Predicate<? super K> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map key matches", "no map key matched", actual -> matchesAny(actual.keySet(), predicate));
    }

    public static <K, V> PreservingCondition<Map<K, V>> noKey(Predicate<? super K> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map key matches", "a map key matched", actual -> !matchesAny(actual.keySet(), predicate));
    }

    public static <K, V> PreservingCondition<Map<K, V>> allValues(Predicate<? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("all map values match", "not all map values matched", actual -> matchesAll(actual.values(), predicate));
    }

    public static <K, V> PreservingCondition<Map<K, V>> anyValue(Predicate<? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("any map value matches", "no map value matched", actual -> matchesAny(actual.values(), predicate));
    }

    public static <K, V> PreservingCondition<Map<K, V>> noValue(Predicate<? super V> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("no map value matches", "a map value matched", actual -> !matchesAny(actual.values(), predicate));
    }

    public static <K, V> Condition<Map<K, V>, V> valueFor(K key) {
        return condition("map contains the expected key", actual -> findEntry(actual, key)
                .continueIfSatisfied(entry -> satisfied(entry.getValue())));
    }

    public static <K, V, R> Condition<Map<K, V>, R> valueFor(K key,
            ResultStage<? super V, ? extends R> nested) {
        return assessedCondition("map value " + ConditionRuntime.description(nested), () -> {
            var nestedEvaluator = ConditionRuntime.<V, R>evaluator(nested);
            return actual -> plain(findEntry(actual, key)).flatMap(entry -> nestedEvaluator.apply(entry.getValue()));
        });
    }

    public static <K, V> Condition<Map<K, V>, V> valueFor(K key,
            PreservingStage<? super V> nested) {
        return MapConditions.<K, V, V>valueFor(key, preserve(nested));
    }

    public static <K, V, T extends V> Condition<Map<K, V>, V> valueFor(K key, ExpectedStage<T> nested) {
        return assessedCondition("map value " + ConditionRuntime.description(nested), () -> {
            var nestedEvaluator = ConditionRuntime.<V>expectedEvaluator(nested);
            return actual -> plain(findEntry(actual, key)).flatMap(entry -> nestedEvaluator.apply(entry.getValue()));
        });
    }

    public static <K, V, R extends V> Condition<Map<K, V>, R> valueFor(K key, NarrowingStage<R> nested) {
        return assessedCondition("map value " + ConditionRuntime.description(nested), () -> {
            var nestedEvaluator = ConditionRuntime.<V, R>narrowingEvaluator(nested);
            return actual -> plain(findEntry(actual, key)).flatMap(entry -> nestedEvaluator.apply(entry.getValue()));
        });
    }

    public static <K, V> Condition<Map<K, V>, Map.Entry<K, V>> entryFor(K key) {
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
        return preserving("map contains expected key", "map did not contain expected key",
                actual -> matchesAny(actual.keySet(), key -> equal(key, expected)));
    }

    public static <K> PreservingCondition<Map<? super K, ?>> doesNotContainKey(K expected) {
        return preserving("map does not contain expected key", "map contained expected key",
                actual -> !matchesAny(actual.keySet(), key -> equal(key, expected)));
    }

    @SafeVarargs
    public static <K> PreservingCondition<Map<? super K, ?>> containsKeys(K... expected) {
        List<K> keys = asList(nonEmpty(expected, "expected keys"));
        return preserving("map contains all expected keys", "map did not contain all expected keys",
                actual -> containsAll(actual.keySet(), keys, ValueMatching::equal));
    }

    @SafeVarargs
    public static <K> PreservingCondition<Map<? super K, ?>> doesNotContainKeys(K... unexpected) {
        List<K> keys = asList(nonEmpty(unexpected, "unexpected keys"));
        return preserving("map does not contain unexpected keys", "map contained an unexpected key",
                actual -> !matchesAny(actual.keySet(), value -> matchesAny(keys, key -> equal(value, key))));
    }

    @SafeVarargs
    public static <K> PreservingCondition<Map<? super K, ?>> containsOnlyKeys(K... expected) {
        List<K> keys = asList(requireNonNull(expected, "expected keys must not be null"));
        return preserving("map contains only the expected keys", "map did not contain only the expected keys",
                actual -> sameDistinctElements(actual.keySet(), keys));
    }

    public static <V> PreservingCondition<Map<?, ? super V>> containsValue(V expected) {
        return preserving("map contains expected value", "map did not contain expected value",
                actual -> matchesAny(actual.values(), value -> equal(value, expected)));
    }

    public static <V> PreservingCondition<Map<?, ? super V>> doesNotContainValue(V expected) {
        return preserving("map does not contain expected value", "map contained expected value",
                actual -> !matchesAny(actual.values(), value -> equal(value, expected)));
    }

    @SafeVarargs
    public static <V> PreservingCondition<Map<?, ? super V>> containsValues(V... expected) {
        List<V> values = asList(nonEmpty(expected, "expected values"));
        return preserving("map contains all expected values", "map did not contain all expected values",
                actual -> containsAll(actual.values(), values, ValueMatching::equal));
    }

    @SafeVarargs
    public static <V> PreservingCondition<Map<?, ? super V>> doesNotContainValues(V... unexpected) {
        List<V> values = asList(nonEmpty(unexpected, "unexpected values"));
        return preserving("map does not contain unexpected values", "map contained an unexpected value",
                actual -> !matchesAny(actual.values(), value -> matchesAny(values, candidate -> equal(value, candidate))));
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsEntry(K key, V value) {
        return preserving("map contains expected entry", "map did not contain expected entry",
                actual -> matchesAny(actual.entrySet(), entry -> entryMatches(entry, key, value)));
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> doesNotContainEntry(K key, V value) {
        return preserving("map does not contain expected entry", "map contained expected entry",
                actual -> !matchesAny(actual.entrySet(), entry -> entryMatches(entry, key, value)));
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsAllEntriesOf(Map<? extends K, ? extends V> expected) {
        Map<? extends K, ? extends V> entries = nonEmpty(expected, "expected entries");
        return preserving("map contains all expected entries", "map did not contain all expected entries",
                actual -> containsAll(actual.entrySet(), entries.entrySet(),
                        MapConditions::entryMatches));
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> doesNotContainAllEntriesOf(Map<? extends K, ? extends V> unexpected) {
        Map<? extends K, ? extends V> entries = nonEmpty(unexpected, "expected entries");
        return preserving("map does not contain all expected entries", "map contained all expected entries",
                actual -> !containsAll(actual.entrySet(), entries.entrySet(),
                        MapConditions::entryMatches));
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsAnyEntriesOf(Map<? extends K, ? extends V> expected) {
        Map<? extends K, ? extends V> entries = nonEmpty(expected, "expected entries");
        return preserving("map contains an expected entry", "map did not contain an expected entry",
                actual -> matchesAny(actual.entrySet(), value ->
                        matchesAny(entries.entrySet(), candidate -> entryMatches(value, candidate))));
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsNoEntriesOf(Map<? extends K, ? extends V> expected) {
        Map<? extends K, ? extends V> entries = nonEmpty(expected, "expected entries");
        return preserving("map does not contain an expected entry", "map contained an expected entry",
                actual -> !matchesAny(actual.entrySet(), value ->
                        matchesAny(entries.entrySet(), candidate -> entryMatches(value, candidate))));
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsExactlyEntriesOf(Map<? extends K, ? extends V> expected) {
        Map<? extends K, ? extends V> entries = requireNonNull(expected, "expected entries must not be null");
        return preserving("map contains exactly the expected entries", "map did not contain exactly the expected entries",
                actual -> exactContent(actual, entries));
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> doesNotContainExactlyEntriesOf(Map<? extends K, ? extends V> expected) {
        Map<? extends K, ? extends V> entries = requireNonNull(expected, "expected entries must not be null");
        return preserving("map does not contain exactly the expected entries", "map contained exactly the expected entries",
                actual -> !exactContent(actual, entries));
    }

    private static <K, V> ConditionEvaluation<Map.Entry<K, V>> selectSingle(Map<K, V> actual,
            Predicate<? super Map.Entry<K, V>> predicate) {
        if (actual == null) {
            return unsatisfied("map was null");
        }
        return ConditionSupport.selectSingle(actual.entrySet(), predicate,
                "no map entry matched", "more than one map entry matched");
    }

    private static <K, V> ConditionEvaluation<Map.Entry<K, V>> findEntry(Map<K, V> actual, K key) {
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

    private static PreservingCondition<Map<?, ?>> sized(int bound, java.util.function.IntPredicate matches,
            String description) {
        return ConditionSupport.sized("map", bound, matches, description, Map::size);
    }

    private static <M extends Map<?, ?>> PreservingCondition<M> preserving(String description, String mismatch,
            Predicate<? super M> matches) {
        return preservingNonNull("map", description, mismatch, matches);
    }

    private static boolean exactContent(Map<?, ?> actual, Map<?, ?> expected) {
        return actual.size() == expected.size()
                && exactly(actual.entrySet().iterator(), expected.entrySet(), MapConditions::entryMatches);
    }

    private static boolean entryMatches(Map.Entry<?, ?> actual, Map.Entry<?, ?> expected) {
        return entryMatches(actual, expected.getKey(), expected.getValue());
    }

    private static boolean entryMatches(Map.Entry<?, ?> actual, Object expectedKey, Object expectedValue) {
        return equal(actual.getKey(), expectedKey) && equal(actual.getValue(), expectedValue);
    }

}

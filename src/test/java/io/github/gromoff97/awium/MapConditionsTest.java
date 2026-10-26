package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class MapConditionsTest {

    @Test
    void bulkKeyAndValueFactoriesRequireANonemptyCollection() {
        List<Function<Collection<String>, PreservingCondition<?>>> factories = List.of(
                MapConditions::containsAllKeys, MapConditions::doesNotContainAnyKeys,
                MapConditions::containsAllValues, MapConditions::doesNotContainAnyValues);
        for (var factory : factories) {
            assertThrows(NullPointerException.class, () -> factory.apply(null));
            assertThrows(IllegalArgumentException.class, () -> factory.apply(List.of()));
        }
        assertThrows(NullPointerException.class, () -> containsOnlyKeys(null));
    }

    @Test
    void arrayKeysAndValuesRemainSingleObjectsInFluentChecks() {
        var time = new FakeTime(0);
        String[] key = {"a", "b"};
        String[] value = {"x", "y"};
        var actual = Map.of(key, value);

        Map<String[], String[]> keyMatch = am(() -> actual).usingTime(time, time)
                .until(containsKey(new String[] {"a", "b"}).because("one array key"));
        Map<String[], String[]> valueMatch = am(() -> actual).usingTime(time, time)
                .until(containsValue(new String[] {"x", "y"}).because("one array value"));
        assertSame(actual, keyMatch);
        assertSame(actual, valueMatch);
        assertEquals(2, keyMatch.keySet().iterator().next().length);
        assertEquals(2, valueMatch.get(key).length);
        assertSame(actual, am(() -> actual).usingTime(time, time)
                .until(doesNotContainKey(new String[] {"missing"}).because("the key must be absent")));
        assertSame(actual, am(() -> actual).usingTime(time, time)
                .until(doesNotContainValue(new String[] {"missing"}).because("the value must be absent")));
    }

    @Test
    void singleMapOperandsCanBeEmptyOrNull() {
        var time = new FakeTime(0);
        String[] missing = null;
        var actual = new LinkedHashMap<String[], String[]>();
        actual.put(null, new String[0]);
        actual.put(new String[0], null);

        assertSame(actual, am(() -> actual).usingTime(time, time)
                .until(containsKey(missing).because("one null key")));
        assertSame(actual, am(() -> actual).usingTime(time, time)
                .until(containsValue(missing).because("one null value")));
        assertSame(actual, am(() -> actual).usingTime(time, time)
                .until(containsKey(new String[0]).because("one empty array key")));
        assertSame(actual, am(() -> actual).usingTime(time, time)
                .until(containsValue(new String[0]).because("one empty array value")));
    }

    @Test
    void exactComparisonRejectsDifferentSizesWithoutTraversingEntries() {
        for (var expected : List.of(Map.<String, String>of(), Map.of("a", "1", "b", "2"))) {
            for (boolean negative : List.of(false, true)) {
                var actual = new ProbeContainers.ProbeMap<String, String>();
                var condition = negative ? doesNotContainExactlyEntries(expected) : containsExactlyEntries(expected);
                var evaluation = evaluate(condition, actual);
                assertEquals(negative ? Satisfied.class : Unsatisfied.class, evaluation.getClass());
                if (negative) assertSame(actual, result(evaluation));
                assertEquals(1, actual.sizeCalls);
            }
        }
    }

    @Test
    void exactComparisonRejectsChangesBetweenSizeAndEntryTraversal() {
        for (boolean shrink : List.of(false, true)) {
            for (boolean negative : List.of(false, true)) {
                var actual = new LinkedHashMap<>(Map.of("a", "1", "b", "2")) {
                    private boolean changed;

                    @Override
                    public Set<Map.Entry<String, String>> entrySet() {
                        if (!changed) {
                            changed = true;
                            if (shrink) remove("b");
                            else put("c", "3");
                        }
                        return super.entrySet();
                    }
                };
                var expected = Map.of("a", "1", "b", "2");
                var condition = negative ? doesNotContainExactlyEntries(expected) : containsExactlyEntries(expected);
                var evaluation = evaluate(condition, actual);
                assertEquals(negative ? Satisfied.class : Unsatisfied.class, evaluation.getClass());
                if (negative) assertSame(actual, result(evaluation));
            }
        }
    }

    @Test
    void positiveAndNegativeMapConditionsAreComplements() {
        for (Pair pair : pairs()) {
            assertPair(pair, pair.matchingActual(), true);
            assertPair(pair, pair.mismatchingActual(), false);
        }
    }

    @Test
    void onlyKeysRejectMissingAndExtraKeysButIgnoreExpectedDuplicates() {
        var condition = containsOnlyKeys(List.of("a", "b", "a"));
        assertStatus(condition, Map.of(), Unsatisfied.class);
        assertStatus(condition, map("a", "1"), Unsatisfied.class);
        assertStatus(condition, map("b", "2"), Unsatisfied.class);
        assertStatus(condition, map("a", "1", "b", "2", "c", "3"), Unsatisfied.class);
        assertStatus(condition, map("b", "2", "a", "1"), Satisfied.class);
        assertStatus(containsOnlyKeys(List.of()), Map.of(), Satisfied.class);
        assertStatus(containsOnlyKeys(List.of()), map("a", "1"), Unsatisfied.class);
    }

    @Test
    void mapEqualityIsActualFirstArrayAwareAndKeyFirst() {
        Directional actualKey = new Directional(true);
        Directional expectedKey = new Directional(false);
        var actual = entryMap(entry(actualKey, "value"));
        assertStatus(containsKey(expectedKey), actual, Satisfied.class);
        assertEquals(1, actualKey.equalsCalls);

        var arrayActual = entryMap(entry(new int[] {1}, new int[] {2}));
        assertStatus(containsKey(new int[] {1}), arrayActual, Satisfied.class);
        assertStatus(containsValue(new int[] {2}), arrayActual, Satisfied.class);
        assertStatus(containsEntry(new int[] {1}, new int[] {2}),
                arrayActual, Satisfied.class);

        var objectArrayActual = entryMap(entry(new String[] {"key"}, new String[] {"value"}));
        assertStatus(MapConditions.containsKey(new String[] {"key"}),
                objectArrayActual, Satisfied.class);
        assertStatus(MapConditions.containsValue(new String[] {"value"}),
                objectArrayActual, Satisfied.class);

        var valueFailure = new IllegalStateException("value equals");
        var keyMismatch = entryMap(entry("actual",
                new ThrowingEquals(valueFailure)));
        assertStatus(containsEntry("expected", new ThrowingEquals(null)),
                keyMismatch, Unsatisfied.class);

        Directional onlyActual = new Directional(true);
        Directional onlyExpected = new Directional(false);
        assertStatus(containsOnlyKeys(java.util.Collections.singletonList(onlyExpected)),
                entryMap(entry(onlyActual, "value")), Satisfied.class);
        assertEquals(2, onlyActual.equalsCalls);
        assertEquals(0, onlyExpected.equalsCalls);
    }

    @Test
    void nullKeysAndValuesUseScanSemantics() {
        var actual = entryMap(entry(null, "value"), entry("key", null));
        assertStatus(containsKey((String) null), actual, Satisfied.class);
        assertStatus(containsValue((String) null), actual, Satisfied.class);
        assertStatus(containsEntry(null, "value"), actual, Satisfied.class);
        assertStatus(containsEntry("key", null), actual, Satisfied.class);
    }

    @Test
    void negativeMapConditionsDoNotHideTraversalOrEqualityFailures() {
        var actualCause = new IllegalStateException("actual entries");
        var expectedCause = new IllegalStateException("expected entries");
        var equalityCause = new IllegalStateException("equals failed");
        var brokenActual = entryMap(entry("a", "1"));
        brokenActual.entrySetFailure = actualCause;
        var brokenExpected = entryMap(entry("a", "1"));
        brokenExpected.entrySetFailure = expectedCause;
        var brokenEquality = entryMap(entry(
                new ThrowingEquals(equalityCause), "1"));

        assertSame(actualCause, assertFailure(CONDITION_FAILED,
                () -> awaitMap(brokenActual, doesNotContainKey("a"))).getCause());
        assertSame(expectedCause, assertThrows(IllegalStateException.class,
                () -> doesNotContainAnyEntries(brokenExpected)));
        assertSame(equalityCause, assertFailure(CONDITION_FAILED,
                () -> awaitMap(brokenEquality,
                        doesNotContainKey(new ThrowingEquals(null)))).getCause());
    }

    @Test
    void nullActualShortCircuitsAndAggregateFactoriesValidateInputs() {
        ConditionResult<?> evaluation = evaluate(
                containsAllEntries(map("a", "1")), null);
        assertEquals(Unsatisfied.class, evaluation.getClass());
        assertFalse(mismatch(evaluation).isBlank());

        assertValidation(NullPointerException.class,
                () -> containsAllEntries(null));
        assertValidation(NullPointerException.class,
                () -> containsExactlyEntries(null));
        assertValidation(IllegalArgumentException.class,
                () -> containsAllEntries(Map.of()));
    }

    @Test
    void terminalDiagnosticsDoNotTraverseTheActualAgain() {
        var actual = entryMap(entry("a", "1"));
        FakeTime time = new FakeTime(0);

        assertFailure(TIMEOUT,
                () -> am(() -> {
                    time.advanceNanos(2);
                    return actual;
                }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                        .until(doesNotContainKey("a").because("business reason")));

        assertEquals(1, actual.entrySetCalls);
    }

    @Test
    void mapFactoriesPreserveConcreteResultTypes() {
        var pollingTime = new FakeTime(0);
        var actual = map("a", "1");
        java.util.concurrent.Callable<LinkedHashMap<String, String>> source = () -> actual;

        LinkedHashMap<String, String> result = am(source).usingTime(pollingTime, pollingTime).until(containsEntry("a", "1"));

        assertSame(actual, result);
    }

    private static void assertPair(Pair pair,
            LinkedHashMap<String, String> actual, boolean positiveSatisfied) {
        ConditionResult<?> positive = evaluate(pair.positive(), actual);
        ConditionResult<?> negative = evaluate(pair.negative(), actual);
        assertAll(pair.name(),
                () -> assertSame(actual, result(positiveSatisfied ? positive : negative)),
                () -> assertFalse(mismatch(positiveSatisfied ? negative : positive).isBlank()));
    }

    private static <ACTUAL extends Map<?, ?>> void assertStatus(
            PreservingCondition<? super ACTUAL> condition, ACTUAL actual,
            Class<?> status) {
        assertEquals(status, evaluate(condition, actual).getClass());
    }

    private static <K, V> ProbeContainers.EntryMap<K, V> awaitMap(
            ProbeContainers.EntryMap<K, V> actual,
            PreservingCondition<? super ProbeContainers.EntryMap<K, V>>
                    condition) {
        var pollingTime = new FakeTime(0);
        return am(() -> actual).usingTime(pollingTime, pollingTime).until(condition);
    }

    private static void assertValidation(Class<? extends Throwable> type,
            org.junit.jupiter.api.function.Executable action) {
        assertFalse(assertThrows(type, action).getMessage().isBlank());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static <K, V> ProbeContainers.EntryMap<K, V> entryMap(
            ProbeContainers.ProbeEntry<K, V>... entries) {
        return new ProbeContainers.EntryMap<>(List.of(entries));
    }

    private static <K, V> ProbeContainers.ProbeEntry<K, V> entry(K key,
            V value) {
        return new ProbeContainers.ProbeEntry<>(key, value);
    }

    private static LinkedHashMap<String, String> map(String... entries) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put(entries[index], entries[index + 1]);
        }
        return map;
    }

    private static List<Pair> pairs() {
        return List.of(
                new Pair("key", containsKey("b"), doesNotContainKey("b"),
                        map("a", "1", "b", "2"), map("a", "1", "c", "2")),
                new Pair("value", containsValue("2"), doesNotContainValue("2"),
                        map("a", "1", "b", "2"), map("a", "1", "b", "3")),
                new Pair("entry", containsEntry("b", "2"),
                        doesNotContainEntry("b", "2"),
                        map("a", "1", "b", "2"), map("a", "1", "b", "3")),
                new Pair("all", containsAllEntries(map("a", "1", "b", "2")),
                        doesNotContainAllEntries(map("a", "1", "b", "2")),
                        map("a", "1", "b", "2", "c", "3"),
                        map("a", "1", "b", "3")),
                new Pair("any", containsAnyEntries(map("x", "9", "b", "2")),
                        doesNotContainAnyEntries(map("x", "9", "b", "2")),
                        map("a", "1", "b", "2"),
                        map("a", "1", "c", "3")),
                new Pair("empty exact", containsExactlyEntries(Map.of()),
                        doesNotContainExactlyEntries(Map.of()), map(), map("a", "1")),
                new Pair("exact",
                        containsExactlyEntries(map("a", "1", "b", "2")),
                        doesNotContainExactlyEntries(
                                map("a", "1", "b", "2")),
                        map("b", "2", "a", "1"),
                        map("a", "1", "b", "3")));
    }

    private record Pair(String name,
            PreservingCondition<? super Map<String, String>> positive,
            PreservingCondition<? super Map<String, String>> negative,
            LinkedHashMap<String, String> matchingActual,
            LinkedHashMap<String, String> mismatchingActual) {}
}

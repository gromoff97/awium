package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import io.github.gromoff97.awium.Source.MapSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.AwaitTestAccess.timedMapAwait;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.WaitConfiguration.DEFAULT;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class MapConditionsTest {

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
    void exactAndAggregateConditionsKeepSetLikeEntrySemantics() {
        assertStatus(containsEntries(map("a", "1")),
                map("a", "1"), Satisfied.class);
        assertStatus(containsExactlyEntries(map("a", "1", "b", "2")),
                map("b", "2", "a", "1"), Satisfied.class);
        assertStatus(containsExactlyEntries(Map.of()), Map.of(), Satisfied.class);
        assertStatus(containsExactlyEntries(map("a", "1")),
                map("a", "1", "b", "2"), Unsatisfied.class);
    }

    @Test
    void onlyKeysRejectMissingAndExtraKeysButIgnoreExpectedDuplicates() {
        var condition = containsOnlyKeys("a", "b", "a");
        assertStatus(condition, Map.of(), Unsatisfied.class);
        assertStatus(condition, map("a", "1"), Unsatisfied.class);
        assertStatus(condition, map("b", "2"), Unsatisfied.class);
        assertStatus(condition, map("a", "1", "b", "2", "c", "3"), Unsatisfied.class);
        assertStatus(condition, map("b", "2", "a", "1"), Satisfied.class);
        assertStatus(containsOnlyKeys(), Map.of(), Satisfied.class);
        assertStatus(containsOnlyKeys(), map("a", "1"), Unsatisfied.class);
    }

    @Test
    void mapEqualityIsActualFirstArrayAwareAndKeyFirst() {
        Directional actualKey = new Directional(true);
        Directional expectedKey = new Directional(false);
        var actual = entryMap(entry(actualKey, "value"));
        assertStatus(containsKeys(expectedKey), actual, Satisfied.class);
        assertEquals(1, actualKey.equalsCalls);

        var arrayActual = entryMap(entry(new int[] {1}, new int[] {2}));
        assertStatus(containsKeys(new int[] {1}), arrayActual, Satisfied.class);
        assertStatus(containsValues(new int[] {2}), arrayActual, Satisfied.class);
        assertStatus(containsEntries(new int[] {1}, new int[] {2}),
                arrayActual, Satisfied.class);

        var objectArrayActual = entryMap(entry(new String[] {"key"}, new String[] {"value"}));
        assertStatus(MapConditions.<String[]>containsKeys(new String[] {"key"}),
                objectArrayActual, Satisfied.class);
        assertStatus(MapConditions.<String[]>containsValues(new String[] {"value"}),
                objectArrayActual, Satisfied.class);

        var valueFailure = new IllegalStateException("value equals");
        var keyMismatch = entryMap(entry("actual",
                new ThrowingEquals(valueFailure)));
        assertStatus(containsEntries("expected", new ThrowingEquals(null)),
                keyMismatch, Unsatisfied.class);

        Directional onlyActual = new Directional(true);
        Directional onlyExpected = new Directional(false);
        assertStatus(containsOnlyKeys(onlyExpected),
                entryMap(entry(onlyActual, "value")), Satisfied.class);
        assertEquals(2, onlyActual.equalsCalls);
        assertEquals(0, onlyExpected.equalsCalls);
    }

    @Test
    void nullKeysAndValuesUseScanSemantics() {
        var actual = entryMap(entry(null, "value"), entry("key", null));
        assertStatus(containsKeys((String) null), actual, Satisfied.class);
        assertStatus(containsValues((String) null), actual, Satisfied.class);
        assertStatus(containsEntries(null, "value"), actual, Satisfied.class);
        assertStatus(containsEntries("key", null), actual, Satisfied.class);
    }

    @Test
    void mapConditionsSnapshotExpectedValuesAtConstruction() {
        Map<String, String> expected = new LinkedHashMap<>(map("a", "before"));
        PreservingCondition<? super Map<String, String>> membership =
                containsEntries(expected);
        PreservingCondition<? super Map<String, String>> exact =
                containsExactlyEntries(expected);
        expected.put("a", "after");

        assertStatus(membership, map("a", "before", "b", "other"), Satisfied.class);
        assertStatus(exact, map("a", "before"), Satisfied.class);
        assertStatus(membership, map("a", "after"), Unsatisfied.class);
        assertStatus(exact, map("a", "after"), Unsatisfied.class);
    }

    @Test
    void clearingExpectedMapDoesNotChangeContainment() {
        var expected = new LinkedHashMap<>(map("expected", "value"));
        var positive = containsEntries(expected);
        var negative = doesNotContainAllEntries(expected);
        expected.clear();

        for (Map<String, String> actual : List.of(Map.<String, String>of(), map("actual", "value"))) {
            assertStatus(positive, actual, Unsatisfied.class);
            assertStatus(negative, actual, Satisfied.class);
        }
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
                () -> awaitMap(brokenActual, doesNotContainKeys("a"))).getCause());
        assertSame(expectedCause, assertThrows(IllegalStateException.class,
                () -> doesNotContainEntries(brokenExpected)));
        assertSame(equalityCause, assertFailure(CONDITION_FAILED,
                () -> awaitMap(brokenEquality,
                        doesNotContainKeys(new ThrowingEquals(null)))).getCause());
    }

    @Test
    void nullActualShortCircuitsAndAggregateFactoriesValidateInputs() {
        ConditionResult<?> evaluation = evaluate(
                containsEntries(map("a", "1")), null);
        assertEquals(Unsatisfied.class, evaluation.getClass());
        assertFalse(mismatch(evaluation).isBlank());

        assertValidation(NullPointerException.class,
                () -> containsEntries(null));
        assertValidation(NullPointerException.class,
                () -> containsExactlyEntries(null));
        assertValidation(IllegalArgumentException.class,
                () -> containsEntries(Map.of()));
    }

    @Test
    void terminalDiagnosticsDoNotTraverseTheActualAgain() {
        var actual = entryMap(entry("a", "1"));
        FakeTime time = new FakeTime(0);

        assertFailure(TIMEOUT,
                () -> timedMapAwait((Source<ProbeContainers.EntryMap<
                                String, String>>) () -> {
                            time.advanceNanos(2);
                            return actual;
                        }, DEFAULT.withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(doesNotContainKeys("a").because("business reason")));

        assertEquals(1, actual.entrySetCalls);
    }

    @Test
    void mapFactoriesPreserveConcreteResultTypes() {
        var pollingTime = new FakeTime(0);
        var actual = map("a", "1");
        MapSource<LinkedHashMap<String, String>> source = () -> actual;

        LinkedHashMap<String, String> result = await(source).usingTime(pollingTime, pollingTime).until(containsEntries("a", "1"));

        assertSame(actual, result);
    }

    private static void assertPair(Pair pair,
            LinkedHashMap<String, String> actual, boolean positiveSatisfied) {
        ConditionResult<?> positive = evaluate(pair.positive(), actual);
        ConditionResult<?> negative = evaluate(pair.negative(), actual);
        assertEquals(positiveSatisfied ? Satisfied.class : Unsatisfied.class,
                positive.getClass(), pair.name());
        assertNotEquals(positive.getClass(), negative.getClass(), pair.name());
        assertSame(actual, result(positiveSatisfied ? positive : negative));
        assertFalse(mismatch(positiveSatisfied ? negative : positive).isBlank());
    }

    private static <K, V, M extends Map<K, V>> void assertStatus(
            PreservingCondition<? super M> condition, M actual,
            Class<?> status) {
        assertFalse(description(condition).isBlank());
        assertEquals(status, evaluate(condition, actual).getClass());
    }

    private static <K, V> ProbeContainers.EntryMap<K, V> awaitMap(
            ProbeContainers.EntryMap<K, V> actual,
            PreservingCondition<? super ProbeContainers.EntryMap<K, V>>
                    condition) {
        var pollingTime = new FakeTime(0);
        return await((MapSource<ProbeContainers.EntryMap<K, V>>) () -> actual).usingTime(pollingTime, pollingTime).until(condition);
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
                new Pair("key", containsKeys("b"), doesNotContainKeys("b"),
                        map("a", "1", "b", "2"), map("a", "1", "c", "2")),
                new Pair("value", containsValues("2"), doesNotContainValues("2"),
                        map("a", "1", "b", "2"), map("a", "1", "b", "3")),
                new Pair("entry", containsEntries("b", "2"),
                        doesNotContainEntries("b", "2"),
                        map("a", "1", "b", "2"), map("a", "1", "b", "3")),
                new Pair("all", containsEntries(map("a", "1", "b", "2")),
                        doesNotContainAllEntries(map("a", "1", "b", "2")),
                        map("a", "1", "b", "2", "c", "3"),
                        map("a", "1", "b", "3")),
                new Pair("any", containsAnyEntries(map("x", "9", "b", "2")),
                        doesNotContainEntries(map("x", "9", "b", "2")),
                        map("a", "1", "b", "2"),
                        map("a", "1", "c", "3")),
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

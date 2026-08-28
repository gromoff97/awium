package io.github.gromoff97.awium;

import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
import io.github.gromoff97.awium.fluent.Condition.PreservingCondition;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.MapSource;

import static io.github.gromoff97.awium.fluent.Await.await;
import static io.github.gromoff97.awium.fluent.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.fluent.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.fluent.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.fluent.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.fluent.AwaitTestAccess.timedMapAwait;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.Status.*;
import static io.github.gromoff97.awium.fluent.MapConditions.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapConditionsTest {

    @Test
    void positiveAndNegativeMapConditionsAreComplements() throws Exception {
        for (Pair pair : pairs()) {
            assertPair(pair, pair.matchingActual(), true);
            assertPair(pair, pair.mismatchingActual(), false);
        }
    }

    @Test
    void exactAndAggregateConditionsKeepSetLikeEntrySemantics()
            throws Exception {
        assertStatus(containsAllEntriesOf(map("a", "1")),
                map("a", "1"), SATISFIED);
        assertStatus(containsExactlyEntriesOf(map("a", "1", "b", "2")),
                map("b", "2", "a", "1"), SATISFIED);
        assertStatus(containsExactlyEntriesOf(Map.of()), Map.of(), SATISFIED);
        assertStatus(containsExactlyEntriesOf(map("a", "1")),
                map("a", "1", "b", "2"), UNSATISFIED);
    }

    @Test
    void mapEqualityIsActualFirstArrayAwareAndKeyFirst() throws Exception {
        Directional actualKey = new Directional(true);
        Directional expectedKey = new Directional(false);
        var actual = entryMap(entry(actualKey, "value"));
        assertStatus(containsKey(expectedKey), actual, SATISFIED);
        assertEquals(1, actualKey.equalsCalls);

        var arrayActual = entryMap(entry(new int[] {1}, new int[] {2}));
        assertStatus(containsEntry(new int[] {1}, new int[] {2}),
                arrayActual, SATISFIED);

        var valueFailure = new IllegalStateException("value equals");
        var keyMismatch = entryMap(entry("actual",
                new ThrowingEquals(valueFailure)));
        assertStatus(containsEntry("expected", new ThrowingEquals(null)),
                keyMismatch, UNSATISFIED);

        Directional onlyActual = new Directional(true);
        Directional onlyExpected = new Directional(false);
        assertStatus(containsOnlyKeys(onlyExpected),
                entryMap(entry(onlyActual, "value")), SATISFIED);
        assertEquals(2, onlyActual.equalsCalls);
        assertEquals(0, onlyExpected.equalsCalls);
    }

    @Test
    void nullKeysAndValuesUseScanSemantics() throws Exception {
        var actual = entryMap(entry(null, "value"), entry("key", null));
        assertStatus(containsKey(null), actual, SATISFIED);
        assertStatus(containsValue(null), actual, SATISFIED);
        assertStatus(containsEntry(null, "value"), actual, SATISFIED);
        assertStatus(containsEntry("key", null), actual, SATISFIED);
    }

    @Test
    void mapConditionsObserveUserOwnedExpectedValuesAtEvaluationTime()
            throws Exception {
        Map<String, String> expected = new LinkedHashMap<>(map("a", "before"));
        PreservingCondition<? super Map<String, String>> membership =
                containsAllEntriesOf(expected);
        PreservingCondition<? super Map<String, String>> exact =
                containsExactlyEntriesOf(expected);
        expected.put("a", "after");

        assertStatus(membership, map("a", "after", "b", "other"), SATISFIED);
        assertStatus(exact, map("a", "after"), SATISFIED);
    }

    @Test
    void clearedLiveExpectedMapUsesEmptyContainmentSemantics()
            throws Exception {
        var expected = new LinkedHashMap<>(map("expected", "value"));
        var positive = containsAllEntriesOf(expected);
        var negative = doesNotContainAllEntriesOf(expected);
        expected.clear();

        for (Map<String, String> actual : List.of(Map.<String, String>of(), map("actual", "value"))) {
            assertStatus(positive, actual, SATISFIED);
            assertStatus(negative, actual, UNSATISFIED);
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

        assertSame(actualCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> awaitMap(brokenActual, doesNotContainKey("a"))).getCause());
        assertSame(expectedCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> awaitMap(entryMap(entry("a", "1")),
                        containsNoEntriesOf(brokenExpected))).getCause());
        assertSame(equalityCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> awaitMap(brokenEquality,
                        doesNotContainKey(new ThrowingEquals(null)))).getCause());
    }

    @Test
    void nullActualShortCircuitsAndAggregateFactoriesValidateInputs()
            throws Exception {
        ConditionEvaluation<?> evaluation = evaluate(
                containsAllEntriesOf(map("a", "1")), null);
        assertEquals(UNSATISFIED, evaluation.status());
        assertFalse(mismatch(evaluation).isBlank());

        assertValidation(NullPointerException.class,
                () -> containsAllEntriesOf(null));
        assertValidation(NullPointerException.class,
                () -> containsExactlyEntriesOf(null));
        assertValidation(IllegalArgumentException.class,
                () -> containsAllEntriesOf(Map.of()));
    }

    @Test
    void terminalDiagnosticsDoNotTraverseTheActualAgain() {
        var actual = entryMap(entry("a", "1"));
        FakeTime time = new FakeTime(0);

        assertThrows(AwaitTimeoutException.class,
                () -> timedMapAwait((Source<ProbeContainers.EntryMap<
                                String, String>>) () -> {
                            time.advanceNanos(2);
                            return actual;
                        }, defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(doesNotContainKey("a").because("business reason")));

        assertEquals(1, actual.entrySetCalls);
    }

    @Test
    void mapFactoriesPreserveConcreteResultTypes() {
        var actual = map("a", "1");
        MapSource<LinkedHashMap<String, String>> source = () -> actual;

        LinkedHashMap<String, String> result = await(source).until(containsEntry("a", "1"));

        assertSame(actual, result);
    }

    private static void assertPair(Pair pair,
            LinkedHashMap<String, String> actual, boolean positiveSatisfied)
            throws Exception {
        ConditionEvaluation<?> positive = evaluate(pair.positive(), actual);
        ConditionEvaluation<?> negative = evaluate(pair.negative(), actual);
        assertEquals(positiveSatisfied ? SATISFIED : UNSATISFIED,
                positive.status(), pair.name());
        assertNotEquals(positive.status(), negative.status(), pair.name());
        assertSame(actual, result(positiveSatisfied ? positive : negative));
        assertFalse(mismatch(positiveSatisfied ? negative : positive).isBlank());
    }

    private static <K, V, M extends Map<K, V>> void assertStatus(
            PreservingCondition<? super M> condition, M actual,
            ConditionEvaluation.Status status) throws Exception {
        assertFalse(description(condition).isBlank());
        assertEquals(status, evaluate(condition, actual).status());
    }

    private static <K, V> ProbeContainers.EntryMap<K, V> awaitMap(
            ProbeContainers.EntryMap<K, V> actual,
            PreservingCondition<? super ProbeContainers.EntryMap<K, V>>
                    condition) {
        return await((MapSource<ProbeContainers.EntryMap<K, V>>) () -> actual).until(condition);
    }

    private static void assertValidation(Class<? extends Throwable> type,
            org.junit.jupiter.api.function.Executable action) {
        assertTrue(!assertThrows(type, action).getMessage().isBlank());
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
                new Pair("all", containsAllEntriesOf(map("a", "1", "b", "2")),
                        doesNotContainAllEntriesOf(map("a", "1", "b", "2")),
                        map("a", "1", "b", "2", "c", "3"),
                        map("a", "1", "b", "3")),
                new Pair("any", containsAnyEntriesOf(map("x", "9", "b", "2")),
                        containsNoEntriesOf(map("x", "9", "b", "2")),
                        map("a", "1", "b", "2"),
                        map("a", "1", "c", "3")),
                new Pair("exact",
                        containsExactlyEntriesOf(map("a", "1", "b", "2")),
                        doesNotContainExactlyEntriesOf(
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

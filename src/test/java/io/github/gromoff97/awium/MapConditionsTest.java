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

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class MapConditionsTest {

    @Test
    void bulkKeyAndValueFactoriesRejectNullInputs() {
        List<Function<Collection<String>, PreservingCondition<?>>> factories = List.of(
                MapConditions::containsAllKeys, MapConditions::doesNotContainAnyKeys,
                MapConditions::containsAllValues, MapConditions::doesNotContainAnyValues);
        for (var factory : factories) {
            assertThrows(NullPointerException.class, () -> factory.apply(null));
        }
        assertThrows(NullPointerException.class, () -> containsOnlyKeys(null));
    }

    @Test
    void arrayKeysAndValuesRemainSingleObjectsInFluentChecks() {
        var time = new FakeTime(0);
        String[] key = {"a", "b"};
        String[] value = {"x", "y"};
        var actual = Map.of(key, value);

        Map<String[], String[]> keyMatch = await(() -> actual).usingTime(time, time)
                .until(containsKey(new String[] {"a", "b"}));
        Map<String[], String[]> valueMatch = await(() -> actual).usingTime(time, time)
                .until(containsValue(new String[] {"x", "y"}));
        assertSame(actual, keyMatch);
        assertSame(actual, valueMatch);
        assertEquals(2, keyMatch.keySet().iterator().next().length);
        assertEquals(2, valueMatch.get(key).length);
        assertSame(actual, await(() -> actual).usingTime(time, time)
                .until(doesNotContainKey(new String[] {"missing"})));
        assertSame(actual, await(() -> actual).usingTime(time, time)
                .until(doesNotContainValue(new String[] {"missing"})));
    }

    @Test
    void singleMapOperandsCanBeEmptyOrNull() {
        var time = new FakeTime(0);
        String[] missing = null;
        var actual = new LinkedHashMap<String[], String[]>();
        actual.put(null, new String[0]);
        actual.put(new String[0], null);

        assertSame(actual, await(() -> actual).usingTime(time, time)
                .until(containsKey(missing)));
        assertSame(actual, await(() -> actual).usingTime(time, time)
                .until(containsValue(missing)));
        assertSame(actual, await(() -> actual).usingTime(time, time)
                .until(containsKey(new String[0])));
        assertSame(actual, await(() -> actual).usingTime(time, time)
                .until(containsValue(new String[0])));
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
        assertStatus(hasEntry(new int[] {1}, new int[] {2}),
                arrayActual, Satisfied.class);

        var objectArrayActual = entryMap(entry(new String[] {"key"}, new String[] {"value"}));
        assertStatus(MapConditions.containsKey(new String[] {"key"}),
                objectArrayActual, Satisfied.class);
        assertStatus(MapConditions.containsValue(new String[] {"value"}),
                objectArrayActual, Satisfied.class);

        var valueFailure = new IllegalStateException("value equals");
        var keyMismatch = entryMap(entry("actual",
                new ThrowingEquals(valueFailure)));
        assertStatus(hasEntry("expected", new ThrowingEquals(null)),
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
        assertStatus(hasEntry(null, "value"), actual, Satisfied.class);
        assertStatus(hasEntry("key", null), actual, Satisfied.class);
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
                () -> awaitEntryMap(brokenActual, doesNotContainKey("a"))).getCause());
        assertSame(expectedCause, assertThrows(IllegalStateException.class,
                () -> doesNotContainAnyEntries(brokenExpected)));
        assertSame(equalityCause, assertFailure(CONDITION_FAILED,
                () -> awaitEntryMap(brokenEquality,
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
        assertValidation(NullPointerException.class,
                () -> containsAnyEntries(null));
        assertValidation(NullPointerException.class,
                () -> doesNotContainAllEntries(null));
        assertValidation(NullPointerException.class,
                () -> doesNotContainAnyEntries(null));
    }

    @Test
    void emptyExpectedSetsFollowTheSameRulesForEntriesKeysAndValues() {
        List<PreservingCondition<? super Map<String, String>>> satisfied = List.of(
                containsAllEntries(Map.of()), doesNotContainAnyEntries(Map.of()),
                containsAllKeys(List.of()), doesNotContainAnyKeys(List.of()),
                containsAllValues(List.of()), doesNotContainAnyValues(List.of()));
        List<PreservingCondition<? super Map<String, String>>> unsatisfied = List.of(
                containsAnyEntries(Map.of()), doesNotContainAllEntries(Map.of()));
        for (var checks : List.of(satisfied, unsatisfied)) {
            for (var check : checks) assertInstanceOf(Unsatisfied.class, evaluate(check, null));
        }

        for (var entries : List.of(Map.<String, String>of(), Map.of("key", "value"))) {
            var actual = new LinkedHashMap<>(entries);
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
            for (var check : satisfied) {
                LinkedHashMap<String, String> selected = waiting.until(check);
                assertSame(actual, selected);
                assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, waiting.tryUntil(check)).result());
            }
            for (var check : unsatisfied) {
                var result = waiting.tryUntil(check);
                assertEquals(TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, result).failure().reason());
                assertEquals(List.of(), result.attempts().getLast().condition().reference().value());
            }
            assertEquals(actual.isEmpty() ? Satisfied.class : Unsatisfied.class,
                    evaluate(containsExactlyEntries(Map.<String, String>of()), actual).getClass());
        }
    }

    @Test
    void terminalDiagnosticsDoNotTraverseTheActualAgain() {
        var actual = entryMap(entry("a", "1"));
        FakeTime time = new FakeTime(0);

        assertFailure(TIMEOUT,
                () -> await(() -> {
                    time.advanceNanos(2);
                    return actual;
                }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                        .until(doesNotContainKey("a")));

        assertEquals(1, actual.entrySetCalls);
    }

    @Test
    void entryChecksPreserveConcreteResultTypesInBothTerminalsAndSequences() {
        var pollingTime = new FakeTime(0);
        var actual = map("pending", "0", "ready", "1", "alsoReady", "2");
        Await.MapSource<LinkedHashMap<String, String>> source = () -> actual;
        var waiting = await(source).usingTime(pollingTime, pollingTime).every(ofNanos(1));

        LinkedHashMap<String, String> exact = waiting.until(hasEntry("ready", "1").because("ready entry required"));
        LinkedHashMap<String, String> checked = waiting.until(hasEntry(matches(entry -> !entry.getValue().equals("0"))));
        LinkedHashMap<String, String> direct = waiting.until(
                hasEntry(entry -> entry.getKey().startsWith("ready") && entry.getValue().startsWith("1")));
        AwaitResult<LinkedHashMap<String, String>, LinkedHashMap<String, String>> directResult = waiting.tryUntil(
                hasEntry((Map.Entry<String, String> entry) -> !entry.getValue().equals("0"))
                        .because("positive entry required"));
        AwaitResult<LinkedHashMap<String, String>, LinkedHashMap<String, String>> checkedResult = waiting.tryUntil(
                hasEntry(matches((Map.Entry<String, String> entry) -> !entry.getValue().equals("0")))
                        .because("positive entry required"));
        AwaitResult<LinkedHashMap<String, String>, LinkedHashMap<String, String>> exactResult = waiting.tryUntil(
                hasEntry("ready", "1").because("ready entry required"));
        List<LinkedHashMap<String, String>> stages = waiting.until(
                hasEntry("ready", "1"), hasEntry(entry -> entry.getValue().equals("2")));

        assertSame(actual, exact);
        assertSame(actual, checked);
        assertSame(actual, direct);
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, directResult).result());
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, checkedResult).result());
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, exactResult).result());
        assertEquals(2, stages.size());
        stages.forEach(stage -> assertSame(actual, stage));
    }

    @Test
    void entryChecksReportMissingAndMismatchingEntriesWithReasons() {
        List<PreservingCondition<? super Map<String, String>>> checks = List.of(
                hasEntry("ready", "1").because("ready entry required"),
                hasEntry((Map.Entry<String, String> entry) ->
                        entry.getKey().equals("ready") && entry.getValue().equals("1"))
                        .because("ready entry required"),
                hasEntry(matches((Map.Entry<String, String> entry) ->
                        entry.getKey().equals("ready") && entry.getValue().equals("1")))
                        .because("ready entry required"));
        for (var actual : List.of(Map.<String, String>of(), Map.of("other", "1"), Map.of("ready", "0"))) {
            Await.MapSource<Map<String, String>> source = () -> actual;
            for (var check : checks) {
                var time = new FakeTime(0);
                var result = await(source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).tryUntil(check);
                var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();

                assertEquals(TIMEOUT, failure.reason());
                assertTrue(failure.message().contains("Because: ready entry required"), failure.message());
            }
        }
    }

    @Test
    void entryAbsenceChecksPreserveMapsAndRejectAnyMatchingEntry() {
        for (var actual : List.of(map(), map("ready", "1"), map("failed", "0"), map("ready", "1", "failed", "1"))) {
            Await.MapSource<LinkedHashMap<String, String>> source = () -> actual;
            var time = new FakeTime(0);
            var waiting = await(source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));
            AwaitResult<LinkedHashMap<String, String>, LinkedHashMap<String, String>> direct = waiting.tryUntil(
                    hasNoEntry(entry -> entry.getKey().startsWith("failed") && entry.getValue().startsWith("1")));
            var exact = waiting.tryUntil(hasNoEntry("failed", "1"));
            var explained = waiting.tryUntil(hasNoEntry((Map.Entry<String, String> entry) ->
                    entry.getKey().startsWith("failed") && entry.getValue().startsWith("1"))
                    .because("failed entry must be absent"));

            for (var result : List.of(direct, exact, explained)) {
                if ("1".equals(actual.get("failed"))) {
                    assertEquals(TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, result).failure().reason());
                } else {
                    assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
                }
            }
            if (!"1".equals(actual.get("failed"))) {
                LinkedHashMap<String, String> observed = waiting.until(hasNoEntry(entry ->
                        entry.getKey().startsWith("failed") && entry.getValue().startsWith("1")));
                assertSame(actual, observed);
            } else {
                var failure = assertInstanceOf(AwaitResult.Failed.class, explained).failure();
                assertTrue(failure.message().contains("Because: failed entry must be absent"), failure.message());
            }
        }
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

    private static <K, V> ProbeContainers.EntryMap<K, V> awaitEntryMap(
            ProbeContainers.EntryMap<K, V> actual,
            PreservingCondition<? super ProbeContainers.EntryMap<K, V>>
                    condition) {
        var pollingTime = new FakeTime(0);
        return await(() -> actual).usingTime(pollingTime, pollingTime).until(condition);
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
                new Pair("entry", hasEntry("b", "2"),
                        hasNoEntry("b", "2"),
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

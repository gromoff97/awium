package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.internal.AttemptResult.Satisfied;
import io.github.gromoff97.awium.internal.AttemptResult.Unsatisfied;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import static org.mockito.Mockito.verify;

class MapConditionsTest {

    private final FakeTime time = new FakeTime(0);

    private static final List<PreservingCondition<? super Map<String, String>>> EMPTY_EXPECTATION_MATCHES = List.of(
            containsAllEntries(Map.of()), doesNotContainAnyEntries(Map.of()),
            containsAllKeys(List.of()), doesNotContainAnyKeys(List.of()),
            containsAllValues(List.of()), doesNotContainAnyValues(List.of()));

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
                var actual = ProbeContainers.<String, String>sizeOnlyMap();
                var condition = negative ? doesNotContainExactlyEntries(expected) : containsExactlyEntries(expected);
                var evaluation = evaluate(condition, actual);
                assertEquals(negative ? Satisfied.class : Unsatisfied.class, evaluation.getClass());
                if (negative) assertSame(actual, result(evaluation));
                verify(actual).size();
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

    @ParameterizedTest
    @MethodSource("pairs")
    void positiveAndNegativeMapConditionsAreComplements(
            PreservingCondition<? super Map<String, String>> positive,
            PreservingCondition<? super Map<String, String>> negative,
            LinkedHashMap<String, String> matching, LinkedHashMap<String, String> mismatching) {
        assertSame(matching, result(evaluate(positive, matching)));
        assertThat(mismatch(evaluate(negative, matching))).isNotBlank();
        assertSame(mismatching, result(evaluate(negative, mismatching)));
        assertThat(mismatch(evaluate(positive, mismatching))).isNotBlank();
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
        AttemptResult<?> evaluation = evaluate(
                containsAllEntries(map("a", "1")), null);
        assertInstanceOf(Unsatisfied.class, evaluation);
        assertThat(mismatch(evaluation)).isNotBlank();

        assertThat(assertThrows(NullPointerException.class, () -> containsAllEntries(null)).getMessage()).isNotBlank();
        assertThat(assertThrows(NullPointerException.class, () -> containsExactlyEntries(null)).getMessage()).isNotBlank();
        assertThat(assertThrows(NullPointerException.class, () -> containsAnyEntries(null)).getMessage()).isNotBlank();
        assertThat(assertThrows(NullPointerException.class, () -> doesNotContainAllEntries(null)).getMessage()).isNotBlank();
        assertThat(assertThrows(NullPointerException.class, () -> doesNotContainAnyEntries(null)).getMessage()).isNotBlank();
    }

    @Test
    void emptyExpectedMembershipDoesNotTraverseTheObservedMap() {
        var actual = new ProbeContainers.EntryMap<>(List.of(Map.entry("key", "value")));
        actual.iteratorFailure = new IllegalStateException("unnecessary traversal");
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        for (var condition : EMPTY_EXPECTATION_MATCHES) assertSame(actual, waiting.until(condition));
        for (var result : List.of(diagnostic(waiting).until(containsAnyEntries(Map.of())),
                diagnostic(waiting).until(doesNotContainAllEntries(Map.of())))) {
            assertEquals(TIMEOUT, failure(result).reason());
        }
    }

    @Test
    void emptyExpectedSetsFollowTheSameRulesForEntriesKeysAndValues() {
        List<PreservingCondition<? super Map<String, String>>> unsatisfied = List.of(
                containsAnyEntries(Map.of()), doesNotContainAllEntries(Map.of()));
        for (var checks : List.of(EMPTY_EXPECTATION_MATCHES, unsatisfied)) {
            for (var check : checks) assertInstanceOf(Unsatisfied.class, evaluate(check, null));
        }

        for (var entries : List.of(Map.<String, String>of(), Map.of("key", "value"))) {
            var actual = new LinkedHashMap<>(entries);
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
            for (var check : EMPTY_EXPECTATION_MATCHES) {
                LinkedHashMap<String, String> selected = waiting.until(check);
                assertSame(actual, selected);
                assertSame(actual, diagnostic(waiting).until(check).orThrow());
            }
            for (var check : unsatisfied) {
                var result = diagnostic(waiting).until(check);
                assertEquals(TIMEOUT, failure(result).reason());
                assertEquals(List.of(), result.conditions().getLast().condition().references().getFirst().value());
            }
            assertEquals(actual.isEmpty() ? Satisfied.class : Unsatisfied.class,
                    evaluate(containsExactlyEntries(Map.<String, String>of()), actual).getClass());
        }
    }

    @Test
    void terminalDiagnosticsDoNotTraverseTheActualAgain() {
        var actual = entryMap(entry("a", "1"));

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
        var actual = map("pending", "0", "ready", "1", "alsoReady", "2");
        Await.MapSource<LinkedHashMap<String, String>> source = () -> actual;
        var waiting = await(source).usingTime(time, time).every(ofNanos(1));

        LinkedHashMap<String, String> exact = waiting.until(hasEntry("ready", "1").because("ready entry required"));
        LinkedHashMap<String, String> checked = waiting.until(hasEntry(matches(entry -> !entry.getValue().equals("0"))));
        LinkedHashMap<String, String> direct = waiting.until(
                hasEntry(entry -> entry.getKey().startsWith("ready") && entry.getValue().startsWith("1")));
        AwaitResult<LinkedHashMap<String, String>, LinkedHashMap<String, String>> directResult = diagnostic(waiting).until(
                hasEntry((Map.Entry<String, String> entry) -> !entry.getValue().equals("0"))
                        .because("positive entry required"));
        AwaitResult<LinkedHashMap<String, String>, LinkedHashMap<String, String>> checkedResult = diagnostic(waiting).until(
                hasEntry(matches((Map.Entry<String, String> entry) -> !entry.getValue().equals("0")))
                        .because("positive entry required"));
        AwaitResult<LinkedHashMap<String, String>, LinkedHashMap<String, String>> exactResult = diagnostic(waiting).until(
                hasEntry("ready", "1").because("ready entry required"));
        List<LinkedHashMap<String, String>> stages = waiting.until(captured(
                hasEntry("ready", "1"), hasEntry(entry -> entry.getValue().equals("2"))));

        assertSame(actual, exact);
        assertSame(actual, checked);
        assertSame(actual, direct);
        assertSame(actual, directResult.orThrow());
        assertSame(actual, checkedResult.orThrow());
        assertSame(actual, exactResult.orThrow());
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
                var result = tryAwait(source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(check);
                var failure = failure(result);

                assertEquals(TIMEOUT, failure.reason());
                assertThat(failure.message()).contains("Because: ready entry required");
            }
        }
    }

    @Test
    void entryAbsenceChecksPreserveMapsAndRejectAnyMatchingEntry() {
        for (var actual : List.of(map(), map("ready", "1"), map("failed", "0"), map("ready", "1", "failed", "1"))) {
            Await.MapSource<LinkedHashMap<String, String>> source = () -> actual;
            var time = new FakeTime(0);
            var waiting = await(source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));
            AwaitResult<LinkedHashMap<String, String>, LinkedHashMap<String, String>> direct = diagnostic(waiting).until(
                    hasNoEntry(entry -> entry.getKey().startsWith("failed") && entry.getValue().startsWith("1")));
            var exact = diagnostic(waiting).until(hasNoEntry("failed", "1"));
            var explained = diagnostic(waiting).until(hasNoEntry((Map.Entry<String, String> entry) ->
                    entry.getKey().startsWith("failed") && entry.getValue().startsWith("1"))
                    .because("failed entry must be absent"));

            for (var result : List.of(direct, exact, explained)) {
                if ("1".equals(actual.get("failed"))) {
                    assertEquals(TIMEOUT, failure(result).reason());
                } else {
                    assertSame(actual, result.orThrow());
                }
            }
            if (!"1".equals(actual.get("failed"))) {
                LinkedHashMap<String, String> observed = waiting.until(hasNoEntry(entry ->
                        entry.getKey().startsWith("failed") && entry.getValue().startsWith("1")));
                assertSame(actual, observed);
            } else {
                assertThat(failure(explained).message()).contains("Because: failed entry must be absent");
            }
        }
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

    private static List<Arguments> pairs() {
        return List.of(
                argumentSet("key", containsKey("b"), doesNotContainKey("b"),
                        map("a", "1", "b", "2"), map("a", "1", "c", "2")),
                argumentSet("value", containsValue("2"), doesNotContainValue("2"),
                        map("a", "1", "b", "2"), map("a", "1", "b", "3")),
                argumentSet("entry", hasEntry("b", "2"),
                        hasNoEntry("b", "2"),
                        map("a", "1", "b", "2"), map("a", "1", "b", "3")),
                argumentSet("all", containsAllEntries(map("a", "1", "b", "2")),
                        doesNotContainAllEntries(map("a", "1", "b", "2")),
                        map("a", "1", "b", "2", "c", "3"),
                        map("a", "1", "b", "3")),
                argumentSet("all first missing", containsAllEntries(map("a", "1", "b", "2")),
                        doesNotContainAllEntries(map("a", "1", "b", "2")),
                        map("a", "1", "b", "2"), map("a", "9", "b", "2")),
                argumentSet("any first", containsAnyEntries(map("b", "2", "x", "9")),
                        doesNotContainAnyEntries(map("b", "2", "x", "9")),
                        map("a", "1", "b", "2"), map("a", "1", "c", "3")),
                argumentSet("any", containsAnyEntries(map("x", "9", "b", "2")),
                        doesNotContainAnyEntries(map("x", "9", "b", "2")),
                        map("a", "1", "b", "2"),
                        map("a", "1", "c", "3")),
                argumentSet("empty exact", containsExactlyEntries(Map.of()),
                        doesNotContainExactlyEntries(Map.of()), map(), map("a", "1")),
                argumentSet("exact",
                        containsExactlyEntries(map("a", "1", "b", "2")),
                        doesNotContainExactlyEntries(
                                map("a", "1", "b", "2")),
                        map("b", "2", "a", "1"),
                        map("a", "1", "b", "3")));
    }

}

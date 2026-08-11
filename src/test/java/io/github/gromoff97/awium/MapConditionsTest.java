package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.CompilationSupport.compiles;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ExpectedValue;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.preserving;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.await.StructuralAwait;
import io.github.gromoff97.awium.await.stages.StructuralAwaitStage;
import io.github.gromoff97.awium.sources.MapSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class MapConditionsTest {

    private static final List<Pair> PAIRS = List.of(
            new Pair("containsKey", containsKey("b"),
                    doesNotContainKey("b"),
                    map("a", "1", "b", "2"),
                    map("a", "1", "c", "2")),
            new Pair("containsValue", containsValue("2"),
                    doesNotContainValue("2"),
                    map("a", "1", "b", "2"),
                    map("a", "1", "b", "3")),
            new Pair("containsEntry", containsEntry("b", "2"),
                    doesNotContainEntry("b", "2"),
                    map("a", "1", "b", "2"),
                    map("a", "1", "b", "3")),
            new Pair("containsAllEntriesOf",
                    containsAllEntriesOf(
                            map("a", "1", "b", "2")),
                    doesNotContainAllEntriesOf(
                            map("a", "1", "b", "2")),
                    map("a", "1", "b", "2", "c", "3"),
                    map("a", "1", "b", "3")),
            new Pair("containsAnyEntriesOf",
                    containsAnyEntriesOf(
                            map("x", "9", "b", "2")),
                    containsNoEntriesOf(
                            map("x", "9", "b", "2")),
                    map("a", "1", "b", "2"),
                    map("a", "1", "c", "3")),
            new Pair("containsExactlyEntriesOf",
                    containsExactlyEntriesOf(
                            map("a", "1", "b", "2")),
                    doesNotContainExactlyEntriesOf(
                            map("a", "1", "b", "2")),
                    map("b", "2", "a", "1"),
                    map("a", "1", "b", "3")));
    private static final MapAccess NONE =
            new MapAccess(0, 0, 0, 0, 0, 0, 0, 0, 0);

    @TempDir
    Path temporaryDirectory;

    @Test
    void completeRawAndExplainedMapTableIsComplementary() throws Exception {
        for (Pair pair : PAIRS) {
            for (boolean explained : new boolean[] {false, true}) {
                assertPair(pair, pair.matchingActual(), true, explained);
                assertPair(pair, pair.mismatchingActual(), false, explained);
            }
        }
    }

    @Test
    void exactEntriesRejectIterationShorterThanReportedSize()
            throws Exception {
        Map<String, String> actual = new HashMap<>() {
            @Override
            public int size() {
                return 1;
            }
        };

        assertStatus(containsExactlyEntriesOf(Map.of("a", "1")), actual,
                Evaluation.Status.UNSATISFIED);
        assertStatus(doesNotContainExactlyEntriesOf(Map.of("a", "1")),
                actual, Evaluation.Status.SATISFIED);
    }

    @Test
    void nullActualIsUnsatisfiedForEverySignWithoutExpectedAccess()
            throws Exception {
        var expected = entryMap(entry("a", "1"));
        List<PreservingCondition<? super ProbeContainers.EntryMap<String,
                String>>> conditions = List.of(
                        containsKey("a"),
                        doesNotContainKey("a"),
                        containsValue("1"),
                        doesNotContainValue("1"),
                        containsEntry("a", "1"),
                        doesNotContainEntry("a", "1"),
                        containsAllEntriesOf(expected),
                        doesNotContainAllEntriesOf(expected),
                        containsAnyEntriesOf(expected),
                        containsNoEntriesOf(expected),
                        containsExactlyEntriesOf(expected),
                        doesNotContainExactlyEntriesOf(expected));

        for (PreservingCondition<? super ProbeContainers.EntryMap<String,
                String>> condition : conditions) {
            Evaluation<?> evaluation = RuntimeCondition.<
                    ProbeContainers.EntryMap<String, String>>preserving(
                            condition).evaluate(null);
            assertEquals(Evaluation.Status.UNSATISFIED, evaluation.status());
            assertEquals("map was null", evaluation.mismatch());
        }
        assertEquals(0, expected.sizeCalls);
        assertEquals(0, expected.entrySetCalls);
    }

    @Test
    void membershipPairsUseIdenticalCallsAtEveryStoppingBoundary()
            throws Exception {
        assertMembershipAccess(MembershipCase.KEY_MATCH,
                Evaluation.Status.SATISFIED, Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 2, 2, 2, 0, 2), NONE));
        assertMembershipAccess(MembershipCase.VALUE_MATCH,
                Evaluation.Status.SATISFIED, Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 2, 2, 0, 2, 2), NONE));
        assertMembershipAccess(MembershipCase.ENTRY_MATCH,
                Evaluation.Status.SATISFIED, Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 2, 2, 2, 1, 3), NONE));
        assertMembershipAccess(MembershipCase.ANY_MATCH,
                Evaluation.Status.SATISFIED, Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 2, 2, 4, 1, 5),
                        new MapAccess(0, 1, 1, 1, 3, 2, 4, 1, 0)));
        assertMembershipAccess(MembershipCase.ALL_FOUND,
                Evaluation.Status.SATISFIED, Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 2, 2, 3, 2, 5),
                        new MapAccess(0, 1, 1, 1, 3, 2, 3, 2, 0)));
        assertMembershipAccess(MembershipCase.KEY_EXHAUSTED,
                Evaluation.Status.UNSATISFIED, Evaluation.Status.SATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 4, 3, 3, 0, 3), NONE));
        assertMembershipAccess(MembershipCase.VALUE_EXHAUSTED,
                Evaluation.Status.UNSATISFIED, Evaluation.Status.SATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 4, 3, 0, 3, 3), NONE));
        assertMembershipAccess(MembershipCase.ENTRY_EXHAUSTED,
                Evaluation.Status.UNSATISFIED, Evaluation.Status.SATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 4, 3, 3, 1, 4), NONE));
        assertMembershipAccess(MembershipCase.ANY_EXHAUSTED,
                Evaluation.Status.UNSATISFIED, Evaluation.Status.SATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 4, 3, 6, 0, 6),
                        new MapAccess(0, 1, 1, 1, 3, 2, 6, 0, 0)));
        assertMembershipAccess(MembershipCase.ALL_EXHAUSTED,
                Evaluation.Status.UNSATISFIED, Evaluation.Status.SATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 4, 3, 4, 1, 5),
                        new MapAccess(0, 1, 1, 1, 3, 2, 4, 1, 0)));
    }

    @Test
    void allMembershipIsSetLikeForRepeatedExpectedPositions()
            throws Exception {
        var expected = entryMap(entry("a", "1"), entry("a", "1"));
        var actual = entryMap(entry("a", "1"));

        Evaluation<?> evaluation = evaluate(
                containsAllEntriesOf(expected), actual, false);
        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
        assertSame(actual, evaluation.result());
        assertNull(evaluation.mismatch());
        assertEquals(1, actual.nextCalls);
    }

    @Test
    void exactPairsUseIdenticalCallsAtEveryCardinalityBoundary()
            throws Exception {
        assertExactAccess(ExactCase.DIFFERENT_SIZE,
                Evaluation.Status.UNSATISFIED, Evaluation.Status.SATISFIED,
                new Access(new MapAccess(1, 0, 0, 0, 0, 0, 0, 0, 0),
                        new MapAccess(1, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertExactAccess(ExactCase.EMPTY,
                Evaluation.Status.SATISFIED, Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(1, 0, 0, 0, 0, 0, 0, 0, 0),
                        new MapAccess(1, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertExactAccess(ExactCase.MATCH,
                Evaluation.Status.SATISFIED, Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(1, 0, 1, 1, 3, 2, 2, 2, 4),
                        new MapAccess(1, 0, 1, 1, 3, 2, 2, 2, 0)));
        assertExactAccess(ExactCase.CONTENT_MISMATCH,
                Evaluation.Status.UNSATISFIED, Evaluation.Status.SATISFIED,
                new Access(new MapAccess(1, 0, 1, 1, 2, 2, 2, 2, 4),
                        new MapAccess(1, 0, 1, 1, 3, 2, 2, 2, 0)));
    }

    @Test
    void exactMatchingIsGreedyInActualAndExpectedEncounterOrder()
            throws Exception {
        GreedyValue first = new GreedyValue(Set.of("x", "y"));
        GreedyValue second = new GreedyValue(Set.of("x"));
        ExpectedValue x = new ExpectedValue("x");
        ExpectedValue y = new ExpectedValue("y");
        var actual = entryMap(
                entry((Object) first, "v"), entry((Object) second, "v"));

        assertStatus(containsExactlyEntriesOf(
                        entryMap(entry((Object) x, "v"),
                                entry((Object) y, "v"))),
                actual, Evaluation.Status.UNSATISFIED);
        assertEquals(2, first.equalsCalls + second.equalsCalls);
        assertEquals(0, x.equalsCalls + y.equalsCalls);

        first.equalsCalls = 0;
        second.equalsCalls = 0;
        assertStatus(containsExactlyEntriesOf(
                        entryMap(entry((Object) y, "v"),
                                entry((Object) x, "v"))),
                actual, Evaluation.Status.SATISFIED);
        assertEquals(2, first.equalsCalls + second.equalsCalls);
        assertEquals(0, x.equalsCalls + y.equalsCalls);
    }

    @Test
    void keyValueAndEntryEqualityIsActualFirstArrayAwareAndKeyFirst()
            throws Exception {
        Directional actualKey = new Directional(true);
        Directional expectedKey = new Directional(false);
        Directional actualValue = new Directional(true);
        Directional expectedValue = new Directional(false);
        var actual = entryMap(entry(actualKey, actualValue));

        assertStatus(containsKey(expectedKey), actual,
                Evaluation.Status.SATISFIED);
        assertStatus(containsValue(expectedValue), actual,
                Evaluation.Status.SATISFIED);
        assertStatus(containsEntry(expectedKey, expectedValue),
                actual, Evaluation.Status.SATISFIED);
        assertEquals(2, actualKey.equalsCalls);
        assertEquals(2, actualValue.equalsCalls);
        assertEquals(0, expectedKey.equalsCalls);
        assertEquals(0, expectedValue.equalsCalls);

        var mismatched = entry("actual", "value");
        mismatched.valueFailure = new IllegalStateException(
                "value must not be read");
        assertStatus(containsEntry("expected", "value"),
                entryMap(mismatched), Evaluation.Status.UNSATISFIED);
        assertEquals(0, mismatched.valueCalls);

        Map<Object, Object> arrays = new LinkedHashMap<>();
        arrays.put(new int[] {1, 2}, new Object[] {new int[] {3, 4}});
        assertStatus(containsEntry(new int[] {1, 2},
                        new Object[] {new int[] {3, 4}}),
                arrays, Evaluation.Status.SATISFIED);
    }

    @Test
    void nullKeysValuesAndLookupRejectingMapsUseScanSemantics()
            throws Exception {
        HashMap<String, String> nullable = new HashMap<>();
        nullable.put(null, null);
        nullable.put("present", null);
        assertStatus(containsKey((String) null), nullable,
                Evaluation.Status.SATISFIED);
        assertStatus(containsValue((String) null), nullable,
                Evaluation.Status.SATISFIED);
        assertStatus(containsEntry("present", null), nullable,
                Evaluation.Status.SATISFIED);
        assertStatus(containsEntry("absent", null), nullable,
                Evaluation.Status.UNSATISFIED);

        TreeMap<String, String> tree = new TreeMap<>();
        tree.put("a", "1");
        assertStatus(containsKey((String) null), tree,
                Evaluation.Status.UNSATISFIED);
        assertStatus(containsEntry(null, null), tree,
                Evaluation.Status.UNSATISFIED);

        var rejecting = entryMap(entry("a", "1"));
        assertStatus(containsEntry("a", "1"), rejecting,
                Evaluation.Status.SATISFIED);
    }

    @Test
    void exactUsesOneToOneArrayEntryMatching() throws Exception {
        Map<Object, String> actual = new LinkedHashMap<>();
        actual.put(new int[] {1}, "a");
        actual.put(new int[] {1}, "b");
        Map<Object, String> equal = new LinkedHashMap<>();
        equal.put(new int[] {1}, "b");
        equal.put(new int[] {1}, "a");
        Map<Object, String> unequal = new LinkedHashMap<>();
        unequal.put(new int[] {1}, "a");
        unequal.put(new int[] {1}, "a");

        assertStatus(containsExactlyEntriesOf(equal), actual,
                Evaluation.Status.SATISFIED);
        assertStatus(containsExactlyEntriesOf(unequal), actual,
                Evaluation.Status.UNSATISFIED);
    }

    @Test
    void aggregateFactoriesRetainExpectedProbeWithoutConstructionCopy()
            throws Exception {
        MapRun membership = membershipRun(MembershipCase.ANY_MATCH, true);
        assertEquals(new MapAccess(0, 1, 0, 0, 0, 0, 0, 0, 0),
                expectedAccess(membership));
        membership.evaluate();
        assertEquals(new MapAccess(0, 1, 1, 1, 3, 2, 4, 1, 0),
                expectedAccess(membership));

        MapRun exact = exactRun(ExactCase.MATCH, true);
        assertEquals(NONE, expectedAccess(exact));
        exact.evaluate();
        assertEquals(new MapAccess(1, 0, 1, 1, 3, 2, 2, 2, 0),
                expectedAccess(exact));
    }

    @Test
    void aggregateFactoriesApplyExactValidationAndAccessContracts()
            throws Exception {
        List<Function<Map<String, String>, ?>> membershipFactories = List.of(
                ConditionProvider::containsAllEntriesOf,
                ConditionProvider::doesNotContainAllEntriesOf,
                ConditionProvider::containsAnyEntriesOf,
                ConditionProvider::containsNoEntriesOf);
        for (Function<Map<String, String>, ?> factory : membershipFactories) {
            var expected = entryMap(entry("a", "1"));
            factory.apply(expected);
            assertEquals(1, expected.isEmptyCalls);
            assertEquals(0, expected.sizeCalls);
            assertEquals(0, expected.entrySetCalls);
        }

        List<Executable> nullFactories = List.of(
                () -> containsAllEntriesOf(null),
                () -> doesNotContainAllEntriesOf(null),
                () -> containsAnyEntriesOf(null),
                () -> containsNoEntriesOf(null),
                () -> containsExactlyEntriesOf(null),
                () -> doesNotContainExactlyEntriesOf(null));
        nullFactories.forEach(factory -> assertEquals(
                "expected entries must not be null",
                assertThrows(NullPointerException.class, factory).getMessage()));

        for (Function<Map<String, String>, ?> factory : membershipFactories) {
            assertEquals("expected entries must not be empty",
                    assertThrows(IllegalArgumentException.class,
                            () -> factory.apply(Map.of())).getMessage());
            var expected = entryMap(entry("a", "1"));
            RuntimeException cause = new IllegalStateException("isEmpty");
            expected.isEmptyFailure = cause;
            assertSame(cause, assertThrows(RuntimeException.class,
                    () -> factory.apply(expected)));
        }

        var emptyActual = MapConditionsTest.<String, String>entryMap();
        assertStatus(containsExactlyEntriesOf(Map.of()),
                emptyActual, Evaluation.Status.SATISFIED);
        assertStatus(doesNotContainExactlyEntriesOf(Map.of()),
                emptyActual, Evaluation.Status.UNSATISFIED);
    }

    @Test
    void membershipFailuresArePairedAtActualAndExpectedBoundaries() {
        for (FailurePoint point : FailurePoint.contentBoundaries()) {
            assertPairedFailure(false, point);
        }
    }

    @Test
    void exactFailuresArePairedAtActualAndExpectedBoundaries() {
        for (FailurePoint point : FailurePoint.exactBoundaries()) {
            assertPairedFailure(true, point);
        }
    }

    @Test
    void diagnosticsDoNotTraverseMapAgain() {
        var actual = entryMap(entry("a", "1"));

        assertThrows(AwaitTimeoutException.class,
                () -> timed(actual).until(
                        containsKey("missing")
                                .because("required")));

        assertEquals(1, actual.entrySetCalls);
        assertEquals(1, actual.iteratorCalls);
        assertEquals(1, actual.nextCalls);
    }

    @Test
    void genericFactoriesPreserveBoundsAndConcreteMapResults()
            throws IOException {
        assertTrue(compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.Awium.await;
                import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
                import io.github.gromoff97.awium.sources.MapSource;
                import io.github.gromoff97.awium.conditioning.conditions.*;
                import java.util.*;

                final class Contract {
                    void check(Map<Integer, String> expected,
                            MapSource<HashMap<Number, CharSequence>> source) {
                        PreservingCondition<Map<? super Integer, ? super String>>
                                typed = containsAllEntriesOf(expected);
                        HashMap<Number, CharSequence> result = await(source)
                                .until(containsExactlyEntriesOf(expected));
                        containsKey(1);
                        containsValue("one");
                        containsEntry(1, "one");
                    }
                }
                """));
        assertFalse(compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.Awium.await;
                import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
                import io.github.gromoff97.awium.sources.MapSource;
                import io.github.gromoff97.awium.conditioning.conditions.*;
                import java.util.*;

                final class Contract {
                    void check(Map<Object, Object> broad,
                            MapSource<HashMap<String, String>> source) {
                        await(source).until(containsAllEntriesOf(broad));
                    }
                }
                """));
    }

    private static void assertPair(Pair pair,
            LinkedHashMap<String, String> actual, boolean positiveSatisfied,
            boolean explained) throws Exception {
        Evaluation<?> positive = evaluate(pair.positive(), actual, explained);
        Evaluation<?> negative = evaluate(pair.negative(), actual, explained);
        assertEquals(positiveSatisfied ? Evaluation.Status.SATISFIED
                        : Evaluation.Status.UNSATISFIED,
                positive.status(), pair.name());
        assertNotEquals(positive.status(), negative.status(), pair.name());
        if (positive.status() == Evaluation.Status.SATISFIED) {
            assertSame(actual, positive.result());
        }
    }

    private static <K, V, M extends Map<K, V>> Evaluation<?> evaluate(
            PreservingCondition<? super M> condition, M actual,
            boolean explained) throws Exception {
        RuntimeCondition<M, M> runtime = explained
                ? preserving(condition.because("reason"))
                : preserving(condition);
        assertEquals(explained ? "reason" : null, runtime.explanation());
        assertFalse(runtime.description().get().isBlank());
        return runtime.evaluate(actual);
    }

    private static <K, V, M extends Map<K, V>> void assertStatus(
            PreservingCondition<? super M> condition, M actual,
            Evaluation.Status expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual, false).status());
    }

    private static void assertMembershipAccess(MembershipCase testCase,
            Evaluation.Status positiveStatus,
            Evaluation.Status negativeStatus, Access expected)
            throws Exception {
        assertPairedAccess(membershipRun(testCase, true),
                membershipRun(testCase, false), positiveStatus, negativeStatus,
                expected, testCase.name());
    }

    private static void assertExactAccess(ExactCase testCase,
            Evaluation.Status positiveStatus,
            Evaluation.Status negativeStatus, Access expected)
            throws Exception {
        assertPairedAccess(exactRun(testCase, true),
                exactRun(testCase, false), positiveStatus, negativeStatus,
                expected, testCase.name());
    }

    private static void assertPairedAccess(MapRun positive, MapRun negative,
            Evaluation.Status positiveStatus,
            Evaluation.Status negativeStatus, Access expected, String name)
            throws Exception {
        assertEquals(positiveStatus, positive.evaluate().status(), name);
        assertEquals(negativeStatus, negative.evaluate().status(), name);
        assertEquals(expected, snapshot(positive), name);
        assertEquals(expected, snapshot(negative), name);
    }

    private static void assertPairedFailure(boolean exact, FailurePoint point) {
        FailureRun positive = failureRun(exact, true, point);
        FailureRun negative = failureRun(exact, false, point);

        assertFailFast(positive);
        assertFailFast(negative);
        assertEquals(snapshot(positive.run()), snapshot(negative.run()),
                point.name());
    }

    private static void assertFailFast(FailureRun failureRun) {
        MapRun run = failureRun.run();
        AwaitConditionEvaluationException failure = assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await((MapSource<
                        ProbeContainers.EntryMap<Object, Object>>)
                                run::actual)
                        .until(run.condition()));
        assertSame(failureRun.cause(), failure.getCause());
    }

    private static MapRun membershipRun(MembershipCase testCase,
            boolean positive) {
        return switch (testCase) {
            case KEY_MATCH -> singularRun(positive, Singular.KEY, "b", "2");
            case VALUE_MATCH -> singularRun(positive, Singular.VALUE, "b", "2");
            case ENTRY_MATCH -> singularRun(positive, Singular.ENTRY, "b", "2");
            case KEY_EXHAUSTED ->
                    singularRun(positive, Singular.KEY, "x", "2");
            case VALUE_EXHAUSTED ->
                    singularRun(positive, Singular.VALUE, "b", "9");
            case ENTRY_EXHAUSTED ->
                    singularRun(positive, Singular.ENTRY, "b", "9");
            case ANY_MATCH -> aggregateRun(positive, false,
                    List.of(probe("x", "9"), probe("b", "2")));
            case ANY_EXHAUSTED -> aggregateRun(positive, false,
                    List.of(probe("x", "9"), probe("y", "8")));
            case ALL_FOUND -> aggregateRun(positive, true,
                    List.of(probe("a", "1"), probe("b", "2")));
            case ALL_EXHAUSTED -> aggregateRun(positive, true,
                    List.of(probe("a", "1"), probe("x", "9")));
        };
    }

    private static MapRun singularRun(boolean positive, Singular singular,
            String expectedKeyLabel, String expectedValueLabel) {
        List<EntryProbe> actualEntries = List.of(
                probe("a", "1"), probe("b", "2"), probe("c", "3"));
        var actual = entryMap(actualEntries);
        CountingValue expectedKey = new CountingValue(expectedKeyLabel);
        CountingValue expectedValue = new CountingValue(expectedValueLabel);
        PreservingCondition<? super Map<Object, Object>> condition = switch (singular) {
            case KEY -> positive
                    ? containsKey(expectedKey)
                    : doesNotContainKey(expectedKey);
            case VALUE -> positive
                    ? containsValue(expectedValue)
                    : doesNotContainValue(expectedValue);
            case ENTRY -> positive
                    ? containsEntry(expectedKey, expectedValue)
                    : doesNotContainEntry(
                            expectedKey, expectedValue);
        };
        return new MapRun(actual, null, condition, actualEntries, List.of(),
                List.of(expectedKey, expectedValue));
    }

    private static MapRun aggregateRun(boolean positive, boolean all,
            List<EntryProbe> expectedEntries) {
        List<EntryProbe> actualEntries = List.of(
                probe("a", "1"), probe("b", "2"), probe("c", "3"));
        var actual = entryMap(actualEntries);
        var expected = entryMap(expectedEntries);
        PreservingCondition<? super Map<Object, Object>> condition = all
                ? positive
                        ? containsAllEntriesOf(expected)
                        : doesNotContainAllEntriesOf(expected)
                : positive
                        ? containsAnyEntriesOf(expected)
                        : containsNoEntriesOf(expected);
        return new MapRun(actual, expected, condition, actualEntries,
                expectedEntries, List.of());
    }

    private static MapRun exactRun(ExactCase testCase, boolean positive) {
        List<EntryProbe> actualEntries = switch (testCase) {
            case EMPTY -> List.of();
            case DIFFERENT_SIZE -> List.of(probe("a", "1"));
            case MATCH -> List.of(probe("a", "1"), probe("b", "2"));
            case CONTENT_MISMATCH ->
                    List.of(probe("a", "1"), probe("b", "3"));
        };
        List<EntryProbe> expectedEntries = switch (testCase) {
            case EMPTY -> List.of();
            default -> List.of(probe("a", "1"), probe("b", "2"));
        };
        var actual = entryMap(actualEntries);
        var expected = entryMap(expectedEntries);
        PreservingCondition<? super Map<Object, Object>> condition = positive
                ? containsExactlyEntriesOf(expected)
                : doesNotContainExactlyEntriesOf(expected);
        return new MapRun(actual, expected, condition, actualEntries,
                expectedEntries, List.of());
    }

    private static FailureRun failureRun(boolean exact, boolean positive,
            FailurePoint point) {
        EntryProbe actualEntry = probe("a", "1");
        EntryProbe expectedEntry = probe("a", "1");
        var actual = entryMap(List.of(actualEntry));
        var expected = entryMap(List.of(expectedEntry));
        RuntimeException cause = point == FailurePoint.EXPECTED_NEXT
                ? new ConcurrentModificationException("expected next")
                : new IllegalStateException(point.name());

        switch (point) {
            case ACTUAL_SIZE -> actual.sizeFailure = cause;
            case EXPECTED_SIZE -> expected.sizeFailure = cause;
            case ACTUAL_ENTRY_SET -> actual.entrySetFailure = cause;
            case EXPECTED_ENTRY_SET -> expected.entrySetFailure = cause;
            case ACTUAL_ITERATOR -> actual.iteratorFailure = cause;
            case EXPECTED_ITERATOR -> expected.iteratorFailure = cause;
            case ACTUAL_NEXT -> {
                actual.failingNext = 1;
                actual.nextFailure = cause;
            }
            case EXPECTED_NEXT -> {
                expected.failingNext = 1;
                expected.nextFailure = cause;
            }
            case ACTUAL_KEY -> actualEntry.entry().keyFailure = cause;
            case EXPECTED_KEY -> expectedEntry.entry().keyFailure = cause;
            case ACTUAL_VALUE -> actualEntry.entry().valueFailure = cause;
            case EXPECTED_VALUE -> expectedEntry.entry().valueFailure = cause;
            case KEY_EQUALITY -> actualEntry.key().failure = cause;
            case VALUE_EQUALITY -> actualEntry.value().failure = cause;
        }

        PreservingCondition<? super Map<Object, Object>> condition = exact
                ? positive
                        ? containsExactlyEntriesOf(expected)
                        : doesNotContainExactlyEntriesOf(
                                expected)
                : positive
                        ? containsAnyEntriesOf(expected)
                        : containsNoEntriesOf(expected);
        return new FailureRun(new MapRun(actual, expected, condition,
                List.of(actualEntry), List.of(expectedEntry), List.of()), cause);
    }

    private static Access snapshot(MapRun run) {
        return new Access(mapAccess(run.actual(), run.actualEntries(), List.of()),
                expectedAccess(run));
    }

    private static MapAccess expectedAccess(MapRun run) {
        return mapAccess(run.expected(), run.expectedEntries(),
                run.extraExpectedOperands());
    }

    private static MapAccess mapAccess(
            ProbeContainers.EntryMap<Object, Object> map,
            List<EntryProbe> entries, List<CountingValue> extraOperands) {
        int keyCalls = 0;
        int valueCalls = 0;
        int equalityCalls = 0;
        for (EntryProbe entry : entries) {
            keyCalls += entry.entry().keyCalls;
            valueCalls += entry.entry().valueCalls;
            equalityCalls += entry.key().equalsCalls
                    + entry.value().equalsCalls;
        }
        for (CountingValue operand : extraOperands) {
            equalityCalls += operand.equalsCalls;
        }
        return new MapAccess(map == null ? 0 : map.sizeCalls,
                map == null ? 0 : map.isEmptyCalls,
                map == null ? 0 : map.entrySetCalls,
                map == null ? 0 : map.iteratorCalls,
                map == null ? 0 : map.hasNextCalls,
                map == null ? 0 : map.nextCalls,
                keyCalls, valueCalls, equalityCalls);
    }

    private static StructuralAwait<
            ProbeContainers.EntryMap<String, String>> timed(
                    ProbeContainers.EntryMap<String, String> actual) {
        FakeTime time = new FakeTime(0);
        return new StructuralAwaitStage<>(
                (MapSource<ProbeContainers.EntryMap<String, String>>) () -> {
                    time.advanceNanos(2);
                    return actual;
                }, Map::size,
                defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)), time, time);
    }

    private static ProbeContainers.EntryMap<Object, Object> entryMap(
            List<EntryProbe> entries) {
        return new ProbeContainers.EntryMap<>(
                entries.stream()
                        .<Map.Entry<Object, Object>>map(EntryProbe::entry)
                        .toList());
    }

    private static EntryProbe probe(String key, String value) {
        CountingValue keyProbe = new CountingValue(key);
        CountingValue valueProbe = new CountingValue(value);
        return new EntryProbe(entry(keyProbe, valueProbe), keyProbe,
                valueProbe);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static <K, V> ProbeContainers.EntryMap<K, V> entryMap(
            Map.Entry<K, V>... entries) {
        return new ProbeContainers.EntryMap<>(
                Arrays.asList(entries));
    }

    private static <K, V> ProbeContainers.ProbeEntry<K, V> entry(
            K key, V value) {
        return new ProbeContainers.ProbeEntry<>(key, value);
    }

    private static LinkedHashMap<String, String> map(String... entries) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put(entries[index], entries[index + 1]);
        }
        return map;
    }

    private record Pair(String name,
            PreservingCondition<? super LinkedHashMap<String, String>> positive,
            PreservingCondition<? super LinkedHashMap<String, String>> negative,
            LinkedHashMap<String, String> matchingActual,
            LinkedHashMap<String, String> mismatchingActual) {
    }

    private record MapRun(
            ProbeContainers.EntryMap<Object, Object> actual,
            ProbeContainers.EntryMap<Object, Object> expected,
            PreservingCondition<? super Map<Object, Object>> condition,
            List<EntryProbe> actualEntries,
            List<EntryProbe> expectedEntries,
            List<CountingValue> extraExpectedOperands) {

        private Evaluation<?> evaluate() throws Exception {
            return MapConditionsTest.evaluate(condition, actual,
                    false);
        }
    }

    private record FailureRun(MapRun run, RuntimeException cause) {
    }

    private record EntryProbe(
            ProbeContainers.ProbeEntry<Object, Object> entry,
            CountingValue key,
            CountingValue value) {
    }

    private record Access(MapAccess actual, MapAccess expected) {
    }

    private record MapAccess(int size, int isEmpty, int entrySet, int iterator,
            int hasNext, int next, int key, int value, int equality) {
    }

    private enum Singular {
        KEY, VALUE, ENTRY
    }

    private enum MembershipCase {
        KEY_MATCH,
        VALUE_MATCH,
        ENTRY_MATCH,
        ANY_MATCH,
        ALL_FOUND,
        KEY_EXHAUSTED,
        VALUE_EXHAUSTED,
        ENTRY_EXHAUSTED,
        ANY_EXHAUSTED,
        ALL_EXHAUSTED
    }

    private enum ExactCase {
        DIFFERENT_SIZE, EMPTY, MATCH, CONTENT_MISMATCH
    }

    private enum FailurePoint {
        ACTUAL_SIZE,
        EXPECTED_SIZE,
        ACTUAL_ENTRY_SET,
        EXPECTED_ENTRY_SET,
        ACTUAL_ITERATOR,
        EXPECTED_ITERATOR,
        ACTUAL_NEXT,
        EXPECTED_NEXT,
        ACTUAL_KEY,
        EXPECTED_KEY,
        ACTUAL_VALUE,
        EXPECTED_VALUE,
        KEY_EQUALITY,
        VALUE_EQUALITY;

        private static List<FailurePoint> contentBoundaries() {
            List<FailurePoint> boundaries = exactBoundaries();
            return boundaries.subList(ACTUAL_ENTRY_SET.ordinal(),
                    boundaries.size());
        }

        private static List<FailurePoint> exactBoundaries() {
            return List.of(values());
        }
    }

    private static final class GreedyValue {
        private final Set<String> matches;
        private int equalsCalls;

        private GreedyValue(Set<String> matches) {
            this.matches = matches;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof ExpectedValue expected
                    && matches.contains(expected.value);
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }

    private static final class CountingValue {
        private final String value;
        private RuntimeException failure;
        private int equalsCalls;

        private CountingValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            if (failure != null) {
                throw failure;
            }
            return other instanceof CountingValue expected
                    && value.equals(expected.value);
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }
}

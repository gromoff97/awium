package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.CompilationSupport.compiles;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ExpectedValue;
import static io.github.gromoff97.awium.ProbeContainers.GreedyValue;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.preserving;
import static io.github.gromoff97.awium.conditioning.providers.MapConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.MapConditionProvider;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.await.stages.StructuralAwaitStage;
import io.github.gromoff97.awium.sources.MapSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapConditionsTest {

    private static final List<Pair> PAIRS = List.of(
            new Pair("containsKey", "key", containsKey("b"),
                    doesNotContainKey("b"),
                    map("a", "1", "b", "2"),
                    map("a", "1", "c", "2")),
            new Pair("containsValue", "value", containsValue("2"),
                    doesNotContainValue("2"),
                    map("a", "1", "b", "2"),
                    map("a", "1", "b", "3")),
            new Pair("containsEntry", "entry", containsEntry("b", "2"),
                    doesNotContainEntry("b", "2"),
                    map("a", "1", "b", "2"),
                    map("a", "1", "b", "3")),
            new Pair("containsAllEntriesOf", null,
                    containsAllEntriesOf(
                            map("a", "1", "b", "2")),
                    doesNotContainAllEntriesOf(
                            map("a", "1", "b", "2")),
                    map("a", "1", "b", "2", "c", "3"),
                    map("a", "1", "b", "3")),
            new Pair("containsAnyEntriesOf", null,
                    containsAnyEntriesOf(
                            map("x", "9", "b", "2")),
                    containsNoEntriesOf(
                            map("x", "9", "b", "2")),
                    map("a", "1", "b", "2"),
                    map("a", "1", "c", "3")),
            new Pair("containsExactlyEntriesOf", null,
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
    void completeRawMapTableIsComplementary() throws Exception {
        for (Pair pair : PAIRS) {
            assertPair(pair, pair.matchingActual(), true);
            assertPair(pair, pair.mismatchingActual(), false);
        }
    }

    @Test
    void exactEntriesRejectTwoSidedIterationLengthMismatch()
            throws Exception {
        for (var entries : List.of(Map.<String, String>of(),
                map("a", "1", "b", "2"))) {
            Map<String, String> actual = reportedMap(entries, 1);
            Map<String, String> expected = reportedMap(entries, 1);
            assertStatus(containsExactlyEntriesOf(expected), actual,
                    Evaluation.Status.UNSATISFIED);
        }
    }

    @Test
    void nullActualIsUnsatisfiedWithoutExpectedMapAccess()
            throws Exception {
        var expected = entryMap(entry("a", "1"));
        Evaluation<?> evaluation = RuntimeCondition.<
                ProbeContainers.EntryMap<String, String>>preserving(
                        containsAllEntriesOf(expected)).evaluate(null);
        assertEquals(Evaluation.Status.UNSATISFIED, evaluation.status());
        assertEquals("map was null", evaluation.mismatch());
        assertEquals(0, expected.sizeCalls);
        assertEquals(0, expected.entrySetCalls);
    }

    @Test
    void membershipUsesExpectedCallsAtEveryStoppingBoundary()
            throws Exception {
        assertMembershipAccess(MembershipCase.KEY_MATCH,
                Evaluation.Status.SATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 2, 2, 2, 0, 2), NONE));
        assertMembershipAccess(MembershipCase.VALUE_MATCH,
                Evaluation.Status.SATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 2, 2, 0, 2, 2), NONE));
        assertMembershipAccess(MembershipCase.ENTRY_MATCH,
                Evaluation.Status.SATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 2, 2, 2, 1, 3), NONE));
        assertMembershipAccess(MembershipCase.ANY_MATCH,
                Evaluation.Status.SATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 2, 2, 4, 1, 5),
                        new MapAccess(0, 1, 1, 1, 3, 2, 4, 1, 0)));
        assertMembershipAccess(MembershipCase.ALL_FOUND,
                Evaluation.Status.SATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 2, 2, 3, 2, 5),
                        new MapAccess(0, 1, 1, 1, 3, 2, 3, 2, 0)));
        assertMembershipAccess(MembershipCase.KEY_EXHAUSTED,
                Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 4, 3, 3, 0, 3), NONE));
        assertMembershipAccess(MembershipCase.VALUE_EXHAUSTED,
                Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 4, 3, 0, 3, 3), NONE));
        assertMembershipAccess(MembershipCase.ENTRY_EXHAUSTED,
                Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 4, 3, 3, 1, 4), NONE));
        assertMembershipAccess(MembershipCase.ANY_EXHAUSTED,
                Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 4, 3, 6, 0, 6),
                        new MapAccess(0, 1, 1, 1, 3, 2, 6, 0, 0)));
        assertMembershipAccess(MembershipCase.ALL_EXHAUSTED,
                Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(0, 0, 1, 1, 4, 3, 4, 1, 5),
                        new MapAccess(0, 1, 1, 1, 3, 2, 4, 1, 0)));
    }

    @Test
    void allMembershipIsSetLikeForRepeatedExpectedPositions()
            throws Exception {
        var expected = entryMap(entry("a", "1"), entry("a", "1"));
        var actual = entryMap(entry("a", "1"));

        Evaluation<?> evaluation = evaluate(
                containsAllEntriesOf(expected), actual);
        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
        assertEquals(1, actual.nextCalls);
    }

    @Test
    void exactUsesExpectedCallsAtEveryCardinalityBoundary()
            throws Exception {
        assertExactAccess(ExactCase.DIFFERENT_SIZE,
                Evaluation.Status.UNSATISFIED,
                new Access(new MapAccess(1, 0, 0, 0, 0, 0, 0, 0, 0),
                        new MapAccess(1, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertExactAccess(ExactCase.EMPTY,
                Evaluation.Status.SATISFIED,
                new Access(new MapAccess(1, 0, 0, 0, 0, 0, 0, 0, 0),
                        new MapAccess(1, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertExactAccess(ExactCase.MATCH,
                Evaluation.Status.SATISFIED,
                new Access(new MapAccess(1, 0, 1, 1, 3, 2, 2, 2, 4),
                        new MapAccess(1, 0, 1, 1, 3, 2, 2, 2, 0)));
        assertExactAccess(ExactCase.CONTENT_MISMATCH,
                Evaluation.Status.UNSATISFIED,
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

        first.equalsCalls = 0;
        second.equalsCalls = 0;
        assertStatus(containsExactlyEntriesOf(
                        entryMap(entry((Object) y, "v"),
                                entry((Object) x, "v"))),
                actual, Evaluation.Status.SATISFIED);
        assertEquals(2, first.equalsCalls + second.equalsCalls);
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

        TreeMap<String, String> tree = new TreeMap<>();
        tree.put("a", "1");
        assertStatus(containsKey((String) null), tree,
                Evaluation.Status.UNSATISFIED);
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
    void exactFactoryRetainsExpectedProbeWithoutConstructionCopy()
            throws Exception {
        MapRun exact = exactRun(ExactCase.MATCH);
        assertEquals(NONE, mapAccess(exact.expected(), exact.expectedEntries(),
                exact.extraExpectedOperands()));
        exact.evaluate();
        assertEquals(new MapAccess(1, 0, 1, 1, 3, 2, 2, 2, 0),
                mapAccess(exact.expected(), exact.expectedEntries(),
                        exact.extraExpectedOperands()));
    }

    @Test
    void aggregateFactoriesApplyExactValidationAndAccessContracts()
            throws Exception {
        List<Function<Map<String, String>, ?>> membershipFactories = List.of(
                MapConditionProvider::containsAllEntriesOf,
                MapConditionProvider::doesNotContainAllEntriesOf,
                MapConditionProvider::containsAnyEntriesOf,
                MapConditionProvider::containsNoEntriesOf);
        for (Function<Map<String, String>, ?> factory : membershipFactories) {
            var expected = entryMap(entry("a", "1"));
            factory.apply(expected);
            assertEquals(1, expected.isEmptyCalls);
            assertEquals(0, expected.sizeCalls);
            assertEquals(0, expected.entrySetCalls);
        }

        assertEquals(
                "expected entries must not be null",
                assertThrows(NullPointerException.class,
                        () -> containsAllEntriesOf(null)).getMessage());
        assertEquals(
                "expected entries must not be null",
                assertThrows(NullPointerException.class,
                        () -> containsExactlyEntriesOf(null)).getMessage());
        assertEquals("expected entries must not be empty",
                assertThrows(IllegalArgumentException.class,
                        () -> containsAllEntriesOf(Map.of())).getMessage());
    }

    @Test
    void membershipFailsAtActualAndExpectedBoundaries() {
        List<FailurePoint> points = List.of(FailurePoint.values());
        for (FailurePoint point : points.subList(
                FailurePoint.ACTUAL_ENTRY_SET.ordinal(), points.size())) {
            assertFailure(false, point);
        }
    }

    @Test
    void exactFailsAtActualAndExpectedBoundaries() {
        for (FailurePoint point : List.of(FailurePoint.values())) {
            assertFailure(true, point);
        }
    }

    @Test
    void diagnosticsDoNotTraverseMapAgain() {
        var actual = entryMap(entry("a", "1"));
        FakeTime time = new FakeTime(0);
        var timed = new StructuralAwaitStage<>(
                (MapSource<ProbeContainers.EntryMap<String, String>>) () -> {
                    time.advanceNanos(2);
                    return actual;
                }, Map::size,
                defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)), time, time);

        assertThrows(AwaitTimeoutException.class,
                () -> timed.until(
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
                import static io.github.gromoff97.awium.conditioning.providers.MapConditionProvider.*;
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
                import static io.github.gromoff97.awium.conditioning.providers.MapConditionProvider.*;
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
            LinkedHashMap<String, String> actual, boolean positiveSatisfied)
            throws Exception {
        String expected = pair.singularExpected();
        Evaluation<?> positive = evaluate(pair.positive(), actual,
                expected == null ? null
                        : "map to contain expected " + expected);
        Evaluation<?> negative = evaluate(pair.negative(), actual,
                expected == null ? null
                        : "map not to contain expected " + expected);
        assertEquals(positiveSatisfied ? Evaluation.Status.SATISFIED
                        : Evaluation.Status.UNSATISFIED,
                positive.status(), pair.name());
        assertNotEquals(positive.status(), negative.status(), pair.name());
        Evaluation<?> satisfied = positiveSatisfied ? positive : negative;
        Evaluation<?> unsatisfied = positiveSatisfied ? negative : positive;
        assertSame(actual, satisfied.result(), pair.name());
        if (expected != null) {
            assertEquals("map " + (positiveSatisfied
                            ? "contained" : "did not contain")
                            + " expected " + expected,
                    unsatisfied.mismatch(), pair.name());
        }
    }

    private static <K, V, M extends Map<K, V>> Evaluation<?> evaluate(
            PreservingCondition<? super M> condition, M actual)
            throws Exception {
        return evaluate(condition, actual, null);
    }

    private static <K, V, M extends Map<K, V>> Evaluation<?> evaluate(
            PreservingCondition<? super M> condition, M actual,
            String expectedDescription) throws Exception {
        RuntimeCondition<M, M> runtime = preserving(condition);
        String description = runtime.description().get();
        if (expectedDescription == null) {
            assertFalse(description.isBlank());
        } else {
            assertEquals(expectedDescription, description);
        }
        return runtime.evaluate(actual);
    }

    private static <K, V, M extends Map<K, V>> void assertStatus(
            PreservingCondition<? super M> condition, M actual,
            Evaluation.Status expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual).status());
    }

    private static void assertMembershipAccess(MembershipCase testCase,
            Evaluation.Status status, Access expected)
            throws Exception {
        assertAccess(membershipRun(testCase), status, expected,
                testCase.name());
    }

    private static void assertExactAccess(ExactCase testCase,
            Evaluation.Status status, Access expected)
            throws Exception {
        assertAccess(exactRun(testCase), status, expected, testCase.name());
    }

    private static void assertAccess(MapRun run, Evaluation.Status status,
            Access expected, String name)
            throws Exception {
        assertEquals(status, run.evaluate().status(), name);
        assertEquals(expected, snapshot(run), name);
    }

    private static void assertFailure(boolean exact, FailurePoint point) {
        assertFailFast(failureRun(exact, point));
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

    private static MapRun membershipRun(MembershipCase testCase) {
        return switch (testCase) {
            case KEY_MATCH -> singularRun(Singular.KEY, "b", "2");
            case VALUE_MATCH -> singularRun(Singular.VALUE, "b", "2");
            case ENTRY_MATCH -> singularRun(Singular.ENTRY, "b", "2");
            case KEY_EXHAUSTED ->
                    singularRun(Singular.KEY, "x", "2");
            case VALUE_EXHAUSTED ->
                    singularRun(Singular.VALUE, "b", "9");
            case ENTRY_EXHAUSTED ->
                    singularRun(Singular.ENTRY, "b", "9");
            case ANY_MATCH -> aggregateRun(false,
                    List.of(probe("x", "9"), probe("b", "2")));
            case ANY_EXHAUSTED -> aggregateRun(false,
                    List.of(probe("x", "9"), probe("y", "8")));
            case ALL_FOUND -> aggregateRun(true,
                    List.of(probe("a", "1"), probe("b", "2")));
            case ALL_EXHAUSTED -> aggregateRun(true,
                    List.of(probe("a", "1"), probe("x", "9")));
        };
    }

    private static MapRun singularRun(Singular singular,
            String expectedKeyLabel, String expectedValueLabel) {
        List<EntryProbe> actualEntries = List.of(
                probe("a", "1"), probe("b", "2"), probe("c", "3"));
        var actual = entryMap(actualEntries);
        CountingValue expectedKey = new CountingValue(expectedKeyLabel);
        CountingValue expectedValue = new CountingValue(expectedValueLabel);
        PreservingCondition<? super Map<Object, Object>> condition = switch (singular) {
            case KEY -> containsKey(expectedKey);
            case VALUE -> containsValue(expectedValue);
            case ENTRY -> containsEntry(expectedKey, expectedValue);
        };
        return new MapRun(actual, null, condition, actualEntries, List.of(),
                List.of(expectedKey, expectedValue));
    }

    private static MapRun aggregateRun(boolean all,
            List<EntryProbe> expectedEntries) {
        List<EntryProbe> actualEntries = List.of(
                probe("a", "1"), probe("b", "2"), probe("c", "3"));
        var actual = entryMap(actualEntries);
        var expected = entryMap(expectedEntries);
        PreservingCondition<? super Map<Object, Object>> condition = all
                ? containsAllEntriesOf(expected)
                : containsAnyEntriesOf(expected);
        return new MapRun(actual, expected, condition, actualEntries,
                expectedEntries, List.of());
    }

    private static MapRun exactRun(ExactCase testCase) {
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
        PreservingCondition<? super Map<Object, Object>> condition =
                containsExactlyEntriesOf(expected);
        return new MapRun(actual, expected, condition, actualEntries,
                expectedEntries, List.of());
    }

    private static FailureRun failureRun(boolean exact, FailurePoint point) {
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
                ? containsExactlyEntriesOf(expected)
                : containsAnyEntriesOf(expected);
        return new FailureRun(new MapRun(actual, expected, condition,
                List.of(actualEntry), List.of(expectedEntry), List.of()), cause);
    }

    private static Access snapshot(MapRun run) {
        return new Access(mapAccess(run.actual(), run.actualEntries(), List.of()),
                mapAccess(run.expected(), run.expectedEntries(),
                        run.extraExpectedOperands()));
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
        return new ProbeContainers.EntryMap<>(List.of(entries));
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

    private static <K, V> Map<K, V> reportedMap(
            Map<K, V> entries, int reportedSize) {
        return new LinkedHashMap<>(entries) {
            @Override
            public int size() {
                return reportedSize;
            }
        };
    }

    private record Pair(String name, String singularExpected,
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
            return MapConditionsTest.evaluate(condition, actual);
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
        VALUE_EQUALITY
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

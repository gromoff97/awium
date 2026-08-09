package io.github.gromoff97.assertility;

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
import java.util.ArrayList;
import java.util.Arrays;
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
            new Pair("containsKey", AwaitConditions.containsKey("b"),
                    AwaitConditions.doesNotContainKey("b"),
                    map("a", "1", "b", "2"),
                    map("a", "1", "c", "2")),
            new Pair("containsValue", AwaitConditions.containsValue("2"),
                    AwaitConditions.doesNotContainValue("2"),
                    map("a", "1", "b", "2"),
                    map("a", "1", "b", "3")),
            new Pair("containsEntry", AwaitConditions.containsEntry("b", "2"),
                    AwaitConditions.doesNotContainEntry("b", "2"),
                    map("a", "1", "b", "2"),
                    map("a", "1", "b", "3")),
            new Pair("containsAllEntriesOf",
                    AwaitConditions.containsAllEntriesOf(
                            map("a", "1", "b", "2")),
                    AwaitConditions.doesNotContainAllEntriesOf(
                            map("a", "1", "b", "2")),
                    map("a", "1", "b", "2", "c", "3"),
                    map("a", "1", "b", "3")),
            new Pair("containsAnyEntriesOf",
                    AwaitConditions.containsAnyEntriesOf(
                            map("x", "9", "b", "2")),
                    AwaitConditions.containsNoEntriesOf(
                            map("x", "9", "b", "2")),
                    map("a", "1", "b", "2"),
                    map("a", "1", "c", "3")),
            new Pair("containsExactlyEntriesOf",
                    AwaitConditions.containsExactlyEntriesOf(
                            map("a", "1", "b", "2")),
                    AwaitConditions.doesNotContainExactlyEntriesOf(
                            map("a", "1", "b", "2")),
                    map("b", "2", "a", "1"),
                    map("a", "1", "b", "3")));

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
    void nullActualIsUnsatisfiedForEverySignWithoutExpectedAccess()
            throws Exception {
        var expected = entryMap(entry("a", "1"));
        List<PreservingCondition<? super ProbeContainers.EntryMap<String,
                String>>> conditions = List.of(
                        AwaitConditions.containsKey("a"),
                        AwaitConditions.doesNotContainKey("a"),
                        AwaitConditions.containsValue("1"),
                        AwaitConditions.doesNotContainValue("1"),
                        AwaitConditions.containsEntry("a", "1"),
                        AwaitConditions.doesNotContainEntry("a", "1"),
                        AwaitConditions.containsAllEntriesOf(expected),
                        AwaitConditions.doesNotContainAllEntriesOf(expected),
                        AwaitConditions.containsAnyEntriesOf(expected),
                        AwaitConditions.containsNoEntriesOf(expected),
                        AwaitConditions.containsExactlyEntriesOf(expected),
                        AwaitConditions.doesNotContainExactlyEntriesOf(expected));

        for (PreservingCondition<? super ProbeContainers.EntryMap<String,
                String>> condition : conditions) {
            Evaluation<?> evaluation = ConditionAdapters.<
                    ProbeContainers.EntryMap<String, String>>preserving(
                            condition).evaluate(null);
            assertEquals(Evaluation.Status.UNSATISFIED, evaluation.status());
            assertEquals("map was null", evaluation.mismatch());
        }
        assertEquals(0, expected.sizeCalls);
        assertEquals(0, expected.entrySetCalls);
    }

    @Test
    void membershipUsesOneActualEntryIteratorAndNoLookupOrSize()
            throws Exception {
        Map<String, String> expected = map("a", "1", "b", "2");
        List<PreservingCondition<? super ProbeContainers.EntryMap<String,
                String>>> conditions = List.of(
                        AwaitConditions.containsKey("b"),
                        AwaitConditions.doesNotContainKey("x"),
                        AwaitConditions.containsValue("2"),
                        AwaitConditions.doesNotContainValue("9"),
                        AwaitConditions.containsEntry("b", "2"),
                        AwaitConditions.doesNotContainEntry("x", "9"),
                        AwaitConditions.containsAllEntriesOf(expected),
                        AwaitConditions.doesNotContainAllEntriesOf(
                                map("a", "1", "x", "9")),
                        AwaitConditions.containsAnyEntriesOf(expected),
                        AwaitConditions.containsNoEntriesOf(
                                map("x", "9")));

        for (PreservingCondition<? super ProbeContainers.EntryMap<String,
                String>> condition : conditions) {
            var actual = entryMap(entry("a", "1"), entry("b", "2"));
            ConditionAdapters.<ProbeContainers.EntryMap<String, String>>
                    preserving(condition).evaluate(actual);
            assertMembershipAccess(actual);
        }
    }

    @Test
    void allMembershipIsSetLikeForRepeatedExpectedPositions()
            throws Exception {
        var expected = entryMap(entry("a", "1"), entry("a", "1"));
        var actual = entryMap(entry("a", "1"));

        assertSatisfied(evaluate(
                AwaitConditions.containsAllEntriesOf(expected), actual, false),
                actual);
        assertEquals(1, actual.nextCalls);
    }

    @Test
    void exactReadsBothSizesOnceAndTraversesOnlyEqualPositiveMaps()
            throws Exception {
        var differentActual = entryMap(entry("a", "1"));
        var differentExpected = entryMap(
                entry("a", "1"), entry("b", "2"));
        evaluate(AwaitConditions.containsExactlyEntriesOf(differentExpected),
                differentActual, false);
        assertExactAccess(differentActual, 1, 0, 0);
        assertExactAccess(differentExpected, 1, 0, 0);

        var emptyActual = MapConditionsTest.<String, String>entryMap();
        var emptyExpected = MapConditionsTest.<String, String>entryMap();
        assertSatisfied(evaluate(
                AwaitConditions.containsExactlyEntriesOf(emptyExpected),
                emptyActual, false), emptyActual);
        assertExactAccess(emptyActual, 1, 0, 0);
        assertExactAccess(emptyExpected, 1, 0, 0);

        var equalActual = entryMap(
                entry("b", "2"), entry("a", "1"));
        var equalExpected = entryMap(
                entry("a", "1"), entry("b", "2"));
        assertSatisfied(evaluate(
                AwaitConditions.containsExactlyEntriesOf(equalExpected),
                equalActual, false), equalActual);
        assertExactAccess(equalActual, 1, 1, 2);
        assertExactAccess(equalExpected, 1, 1, 2);
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

        assertStatus(AwaitConditions.containsExactlyEntriesOf(
                        entryMap(entry((Object) x, "v"),
                                entry((Object) y, "v"))),
                actual, Evaluation.Status.UNSATISFIED);
        assertEquals(2, first.equalsCalls + second.equalsCalls);
        assertEquals(0, x.equalsCalls + y.equalsCalls);

        first.equalsCalls = 0;
        second.equalsCalls = 0;
        assertStatus(AwaitConditions.containsExactlyEntriesOf(
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

        assertStatus(AwaitConditions.containsKey(expectedKey), actual,
                Evaluation.Status.SATISFIED);
        assertStatus(AwaitConditions.containsValue(expectedValue), actual,
                Evaluation.Status.SATISFIED);
        assertStatus(AwaitConditions.containsEntry(expectedKey, expectedValue),
                actual, Evaluation.Status.SATISFIED);
        assertEquals(2, actualKey.equalsCalls);
        assertEquals(2, actualValue.equalsCalls);
        assertEquals(0, expectedKey.equalsCalls);
        assertEquals(0, expectedValue.equalsCalls);

        var mismatched = entry("actual", "value");
        mismatched.valueFailure = new IllegalStateException(
                "value must not be read");
        assertStatus(AwaitConditions.containsEntry("expected", "value"),
                entryMap(mismatched), Evaluation.Status.UNSATISFIED);
        assertEquals(0, mismatched.valueCalls);

        Map<Object, Object> arrays = new LinkedHashMap<>();
        arrays.put(new int[] {1, 2}, new Object[] {new int[] {3, 4}});
        assertStatus(AwaitConditions.containsEntry(new int[] {1, 2},
                        new Object[] {new int[] {3, 4}}),
                arrays, Evaluation.Status.SATISFIED);
    }

    @Test
    void nullKeysValuesAndLookupRejectingMapsUseScanSemantics()
            throws Exception {
        HashMap<String, String> nullable = new HashMap<>();
        nullable.put(null, null);
        nullable.put("present", null);
        assertStatus(AwaitConditions.containsKey((String) null), nullable,
                Evaluation.Status.SATISFIED);
        assertStatus(AwaitConditions.containsValue((String) null), nullable,
                Evaluation.Status.SATISFIED);
        assertStatus(AwaitConditions.containsEntry("present", null), nullable,
                Evaluation.Status.SATISFIED);
        assertStatus(AwaitConditions.containsEntry("absent", null), nullable,
                Evaluation.Status.UNSATISFIED);

        TreeMap<String, String> tree = new TreeMap<>();
        tree.put("a", "1");
        assertStatus(AwaitConditions.containsKey((String) null), tree,
                Evaluation.Status.UNSATISFIED);
        assertStatus(AwaitConditions.containsEntry(null, null), tree,
                Evaluation.Status.UNSATISFIED);

        var rejecting = entryMap(entry("a", "1"));
        assertStatus(AwaitConditions.containsEntry("a", "1"), rejecting,
                Evaluation.Status.SATISFIED);
        assertNoLookupCalls(rejecting);
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

        assertStatus(AwaitConditions.containsExactlyEntriesOf(equal), actual,
                Evaluation.Status.SATISFIED);
        assertStatus(AwaitConditions.containsExactlyEntriesOf(unequal), actual,
                Evaluation.Status.UNSATISFIED);
    }

    @Test
    void aggregateInputsAreRetainedByReference() throws Exception {
        ArrayList<Map.Entry<String, String>> membershipEntries =
                new ArrayList<>(List.of(entry("before", "1")));
        var membershipExpected = new ProbeContainers.EntryMap<>(
                membershipEntries);
        PreservingCondition<?> membership =
                AwaitConditions.containsAnyEntriesOf(membershipExpected);
        membershipEntries.set(0, entry("after", "2"));
        var membershipActual = entryMap(entry("after", "2"));
        assertSatisfied(evaluate(castString(membership), membershipActual,
                false), membershipActual);

        ArrayList<Map.Entry<String, String>> exactEntries =
                new ArrayList<>(List.of(entry("before", "1")));
        var exactExpected = new ProbeContainers.EntryMap<>(exactEntries);
        PreservingCondition<?> exact =
                AwaitConditions.containsExactlyEntriesOf(exactExpected);
        exactEntries.set(0, entry("after", "2"));
        var exactActual = entryMap(entry("after", "2"));
        assertSatisfied(evaluate(castString(exact), exactActual, false),
                exactActual);
    }

    @Test
    void aggregateFactoriesApplyExactValidationAndAccessContracts()
            throws Exception {
        List<Function<Map<String, String>, ?>> membershipFactories = List.of(
                AwaitConditions::containsAllEntriesOf,
                AwaitConditions::doesNotContainAllEntriesOf,
                AwaitConditions::containsAnyEntriesOf,
                AwaitConditions::containsNoEntriesOf);
        for (Function<Map<String, String>, ?> factory : membershipFactories) {
            var expected = entryMap(entry("a", "1"));
            factory.apply(expected);
            assertEquals(1, expected.isEmptyCalls);
            assertEquals(0, expected.sizeCalls);
            assertEquals(0, expected.entrySetCalls);
        }

        List<Executable> nullFactories = List.of(
                () -> AwaitConditions.containsAllEntriesOf(null),
                () -> AwaitConditions.doesNotContainAllEntriesOf(null),
                () -> AwaitConditions.containsAnyEntriesOf(null),
                () -> AwaitConditions.containsNoEntriesOf(null),
                () -> AwaitConditions.containsExactlyEntriesOf(null),
                () -> AwaitConditions.doesNotContainExactlyEntriesOf(null));
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
        assertStatus(AwaitConditions.containsExactlyEntriesOf(Map.of()),
                emptyActual, Evaluation.Status.SATISFIED);
        assertStatus(AwaitConditions.doesNotContainExactlyEntriesOf(Map.of()),
                emptyActual, Evaluation.Status.UNSATISFIED);
    }

    @Test
    void structuralAndEqualityFailuresKeepConditionOriginForNegativeForms() {
        RuntimeException entrySetCause = new IllegalStateException("entrySet");
        var entrySetActual = entryMap(entry("a", "1"));
        entrySetActual.entrySetFailure = entrySetCause;
        assertFailFast(entrySetActual,
                AwaitConditions.doesNotContainKey("a"), entrySetCause);

        RuntimeException iteratorCause = new IllegalStateException("iterator");
        var iteratorActual = entryMap(entry("a", "1"));
        iteratorActual.iteratorFailure = iteratorCause;
        assertFailFast(iteratorActual,
                AwaitConditions.doesNotContainValue("1"), iteratorCause);

        RuntimeException nextCause = new IllegalStateException("next");
        var nextActual = entryMap(entry("a", "1"));
        nextActual.failingNext = 1;
        nextActual.nextFailure = nextCause;
        assertFailFast(nextActual,
                AwaitConditions.doesNotContainEntry("a", "1"), nextCause);

        RuntimeException accessorCause = new IllegalStateException("key");
        var failingEntry = entry("a", "1");
        failingEntry.keyFailure = accessorCause;
        assertFailFast(entryMap(failingEntry),
                AwaitConditions.doesNotContainKey("a"), accessorCause);

        RuntimeException equalityCause = new IllegalStateException("equals");
        assertFailFast(entryMap(entry(new ThrowingEquals(equalityCause), "1")),
                AwaitConditions.doesNotContainKey(
                        new ThrowingEquals(null)), equalityCause);

        RuntimeException actualSizeCause = new IllegalStateException(
                "actual size");
        var actualSize = entryMap(entry("a", "1"));
        actualSize.sizeFailure = actualSizeCause;
        assertFailFast(actualSize,
                AwaitConditions.doesNotContainExactlyEntriesOf(
                        map("a", "1")), actualSizeCause);

        RuntimeException expectedSizeCause = new IllegalStateException(
                "expected size");
        var expectedSize = entryMap(entry("a", "1"));
        expectedSize.sizeFailure = expectedSizeCause;
        assertFailFast(entryMap(entry("a", "1")),
                AwaitConditions.doesNotContainExactlyEntriesOf(expectedSize),
                expectedSizeCause);

        RuntimeException expectedIteratorCause = new IllegalStateException(
                "expected iterator");
        var expectedIterator = entryMap(entry("a", "1"));
        expectedIterator.iteratorFailure = expectedIteratorCause;
        assertFailFast(entryMap(entry("a", "1")),
                AwaitConditions.doesNotContainExactlyEntriesOf(
                        expectedIterator), expectedIteratorCause);
    }

    @Test
    void diagnosticsDoNotTraverseMapAgain() {
        var actual = entryMap(entry("a", "1"));

        assertThrows(AwaitTimeoutException.class,
                () -> timed(actual).until(
                        AwaitConditions.containsKey("missing")
                                .because("required")));

        assertEquals(1, actual.entrySetCalls);
        assertEquals(1, actual.iteratorCalls);
        assertEquals(1, actual.nextCalls);
    }

    @Test
    void genericFactoriesPreserveBoundsAndConcreteMapResults()
            throws IOException {
        assertTrue(CompilationSupport.compiles(temporaryDirectory, """
                import static io.github.gromoff97.assertility.Assertility.await;
                import static io.github.gromoff97.assertility.AwaitConditions.*;
                import io.github.gromoff97.assertility.*;
                import java.util.*;

                final class Contract {
                    void check(Map<Integer, String> expected,
                            AwaitSources.MapSource<Number, CharSequence,
                                    HashMap<Number, CharSequence>> source) {
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
        assertFalse(CompilationSupport.compiles(temporaryDirectory, """
                import static io.github.gromoff97.assertility.Assertility.await;
                import static io.github.gromoff97.assertility.AwaitConditions.*;
                import io.github.gromoff97.assertility.*;
                import java.util.*;

                final class Contract {
                    void check(Map<Object, Object> broad,
                            AwaitSources.MapSource<String, String,
                                    HashMap<String, String>> source) {
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
        ConditionRuntime<M, M> runtime = explained
                ? ConditionAdapters.preserving(condition.because("reason"))
                : ConditionAdapters.preserving(condition);
        assertEquals(explained ? "reason" : null, runtime.explanation());
        assertFalse(runtime.description().get().isBlank());
        return runtime.evaluate(actual);
    }

    private static <K, V, M extends Map<K, V>> void assertStatus(
            PreservingCondition<? super M> condition, M actual,
            Evaluation.Status expected) throws Exception {
        assertEquals(expected, evaluate(condition, actual, false).status());
    }

    private static void assertSatisfied(Evaluation<?> evaluation,
            Object actual) {
        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
        assertSame(actual, evaluation.result());
        assertNull(evaluation.mismatch());
    }

    private static void assertMembershipAccess(
            ProbeContainers.EntryMap<?, ?> actual) {
        assertEquals(0, actual.sizeCalls);
        assertEquals(0, actual.isEmptyCalls);
        assertEquals(1, actual.entrySetCalls);
        assertEquals(1, actual.iteratorCalls);
        assertNoLookupCalls(actual);
    }

    private static void assertExactAccess(
            ProbeContainers.EntryMap<?, ?> map, int size, int iterators,
            int next) {
        assertEquals(size, map.sizeCalls);
        assertEquals(iterators, map.entrySetCalls);
        assertEquals(iterators, map.iteratorCalls);
        assertEquals(next, map.nextCalls);
        assertNoLookupCalls(map);
    }

    private static void assertNoLookupCalls(
            ProbeContainers.EntryMap<?, ?> map) {
        assertEquals(0, map.containsKeyCalls);
        assertEquals(0, map.getCalls);
        assertEquals(0, map.containsValueCalls);
        assertEquals(0, map.equalsCalls);
        assertEquals(0, map.hashCodeCalls);
    }

    private static <K, V> void assertFailFast(
            ProbeContainers.EntryMap<K, V> actual,
            PreservingCondition<? super ProbeContainers.EntryMap<K, V>>
                    condition,
            RuntimeException cause) {
        AwaitConditionEvaluationException failure = assertThrows(
                AwaitConditionEvaluationException.class,
                () -> Assertility.await((AwaitSources.MapSource<K, V,
                        ProbeContainers.EntryMap<K, V>>) () -> actual)
                        .until(condition));
        assertSame(cause, failure.getCause());
    }

    private static MapUntil<String, String,
            ProbeContainers.EntryMap<String, String>> timed(
                    ProbeContainers.EntryMap<String, String> actual) {
        FakeTime time = new FakeTime(0);
        AwaitChain<ProbeContainers.EntryMap<String, String>> chain =
                new AwaitChain<>(() -> {
                    time.advanceNanos(2);
                    return actual;
                }, WaitConfig.defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)), time, time,
                new InterruptGuard(), new FailureFactory());
        return new MapStageAdapters.MapAfterUpToStage<>(chain);
    }

    @SuppressWarnings("unchecked")
    private static PreservingCondition<? super ProbeContainers.EntryMap<String,
            String>> castString(PreservingCondition<?> condition) {
        return (PreservingCondition<? super ProbeContainers.EntryMap<String,
                String>>) condition;
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static <K, V> ProbeContainers.EntryMap<K, V> entryMap(
            Map.Entry<K, V>... entries) {
        return new ProbeContainers.EntryMap<>(
                new ArrayList<>(Arrays.asList(entries)));
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

    private static final class Directional {
        private final boolean result;
        private int equalsCalls;

        private Directional(boolean result) {
            this.result = result;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof Directional && result;
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }

    private static final class ExpectedValue {
        private final String value;
        private int equalsCalls;

        private ExpectedValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof ExpectedValue expected
                    && value.equals(expected.value);
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
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

    private static final class ThrowingEquals {
        private final RuntimeException failure;

        private ThrowingEquals(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public boolean equals(Object other) {
            if (failure != null) {
                throw failure;
            }
            return other instanceof ThrowingEquals;
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }
}

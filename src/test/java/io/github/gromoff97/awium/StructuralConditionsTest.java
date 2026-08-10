package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.diagnostic.*;

import io.github.gromoff97.awium.internal.engine.*;

import io.github.gromoff97.awium.exception.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuralConditionsTest {

    private static final List<Case> CASES = List.of(
            new Case(AwaitConditions.empty, 0, 1, "to be empty"),
            new Case(AwaitConditions.nonEmpty, 1, 0, "to be non-empty"),
            new Case(AwaitConditions.sizeExactly(2), 2, 1,
                    "size to be exactly 2"),
            new Case(AwaitConditions.sizeNotExactly(2), 1, 2,
                    "size not to be exactly 2"),
            new Case(AwaitConditions.sizeGreaterThan(2), 3, 2,
                    "size to be greater than 2"),
            new Case(AwaitConditions.sizeAtLeast(2), 2, 1,
                    "size to be at least 2"),
            new Case(AwaitConditions.sizeLessThan(2), 1, 2,
                    "size to be less than 2"),
            new Case(AwaitConditions.sizeAtMost(2), 2, 3,
                    "size to be at most 2"));

    @Test
    void rawAndExplainedConditionsUseOneSizeReadForCollections()
            throws Exception {
        for (Case testCase : CASES) {
            assertCollectionEvaluation(testCase, false);
            assertCollectionEvaluation(testCase, true);
        }
    }

    @Test
    void rawAndExplainedConditionsUseOneSizeReadForMaps() throws Exception {
        for (Case testCase : CASES) {
            assertMapEvaluation(testCase, false);
            assertMapEvaluation(testCase, true);
        }
    }

    @Test
    void nullContainersShortCircuitEveryRawAndExplainedCondition()
            throws Exception {
        for (Case testCase : CASES) {
            assertUnsatisfied(collection(testCase.condition()).evaluate(null),
                    "collection was null");
            assertUnsatisfied(collection(testCase.condition().because("reason"))
                    .evaluate(null), "collection was null");
            assertUnsatisfied(map(testCase.condition()).evaluate(null),
                    "map was null");
            assertUnsatisfied(map(testCase.condition().because("reason"))
                    .evaluate(null), "map was null");
        }
    }

    @Test
    void namedRelationPairsAreExactComplements() throws Exception {
        List<Pair> pairs = List.of(
                new Pair(AwaitConditions.sizeExactly(2),
                        AwaitConditions.sizeNotExactly(2)),
                new Pair(AwaitConditions.sizeGreaterThan(2),
                        AwaitConditions.sizeAtMost(2)),
                new Pair(AwaitConditions.sizeAtLeast(2),
                        AwaitConditions.sizeLessThan(2)));

        for (Pair pair : pairs) {
            for (int size = 0; size <= 4; size++) {
                var positive = new ProbeContainers.ProbeCollection<>(size);
                var negative = new ProbeContainers.ProbeCollection<>(size);
                assertNotEquals(collection(pair.positive()).evaluate(positive).status(),
                        collection(pair.negative()).evaluate(negative).status());
                assertNoFallback(positive);
                assertNoFallback(negative);
            }
        }
    }

    @Test
    void successfulFacadesReturnTheExactConcreteContainers() {
        var collection = new ProbeContainers.ProbeCollection<Object>(1);
        var map = new ProbeContainers.ProbeMap<Object, Object>(1);

        ProbeContainers.ProbeCollection<Object> returnedCollection =
                Awium.await((AwaitSources.CollectionSource<Object,
                        ProbeContainers.ProbeCollection<Object>>) () -> collection)
                        .until(AwaitConditions.nonEmpty);
        ProbeContainers.ProbeMap<Object, Object> returnedMap =
                Awium.await((AwaitSources.MapSource<Object, Object,
                        ProbeContainers.ProbeMap<Object, Object>>) () -> map)
                        .until(AwaitConditions.nonEmpty.because("required"));

        assertSame(collection, returnedCollection);
        assertSame(map, returnedMap);
        assertNoFallback(collection);
        assertNoFallback(map);
    }

    @Test
    void terminalDiagnosticsReuseTheCapturedSize() {
        var rawCollection = new ProbeContainers.ProbeCollection<Object>(1);
        var explainedCollection = new ProbeContainers.ProbeCollection<Object>(1);
        var rawMap = new ProbeContainers.ProbeMap<Object, Object>(1);
        var explainedMap = new ProbeContainers.ProbeMap<Object, Object>(1);

        assertThrows(AwaitTimeoutException.class,
                () -> timedCollection(rawCollection)
                        .until(AwaitConditions.empty));
        assertThrows(AwaitTimeoutException.class,
                () -> timedCollection(explainedCollection)
                        .until(AwaitConditions.empty.because("required")));
        assertThrows(AwaitTimeoutException.class,
                () -> timedMap(rawMap).until(AwaitConditions.empty));
        assertThrows(AwaitTimeoutException.class,
                () -> timedMap(explainedMap)
                        .until(AwaitConditions.empty.because("required")));

        assertNoFallback(rawCollection);
        assertNoFallback(explainedCollection);
        assertNoFallback(rawMap);
        assertNoFallback(explainedMap);
    }

    @Test
    void throwingSizeIsAnExactFailFastConditionCauseWithoutFallback() {
        var collectionCause = new IllegalStateException("collection size failed");
        var explainedCollectionCause = new IllegalStateException(
                "explained collection size failed");
        var mapCause = new IllegalStateException("map size failed");
        var explainedMapCause = new IllegalStateException(
                "explained map size failed");
        var collection = new ProbeContainers.ProbeCollection<Object>(
                collectionCause);
        var explainedCollection = new ProbeContainers.ProbeCollection<Object>(
                explainedCollectionCause);
        var map = new ProbeContainers.ProbeMap<Object, Object>(mapCause);
        var explainedMap = new ProbeContainers.ProbeMap<Object, Object>(
                explainedMapCause);

        assertSame(collectionCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> Awium.await((AwaitSources.CollectionSource<Object,
                        ProbeContainers.ProbeCollection<Object>>) () -> collection)
                        .until(AwaitConditions.nonEmpty)).getCause());
        assertSame(explainedCollectionCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> Awium.await((AwaitSources.CollectionSource<Object,
                        ProbeContainers.ProbeCollection<Object>>)
                        () -> explainedCollection)
                        .until(AwaitConditions.nonEmpty.because("required")))
                .getCause());
        assertSame(mapCause, assertThrows(AwaitConditionEvaluationException.class,
                () -> Awium.await((AwaitSources.MapSource<Object, Object,
                        ProbeContainers.ProbeMap<Object, Object>>) () -> map)
                        .until(AwaitConditions.nonEmpty)).getCause());
        assertSame(explainedMapCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> Awium.await((AwaitSources.MapSource<Object, Object,
                        ProbeContainers.ProbeMap<Object, Object>>) () -> explainedMap)
                        .until(AwaitConditions.nonEmpty.because("required")))
                .getCause());

        assertNoFallback(collection);
        assertNoFallback(explainedCollection);
        assertNoFallback(map);
        assertNoFallback(explainedMap);
    }

    @Test
    void sizeFactoriesRejectNegativeBoundsAndAllowZero() {
        List<java.util.function.IntFunction<StructuralCondition>> factories = List.of(
                AwaitConditions::sizeExactly,
                AwaitConditions::sizeNotExactly,
                AwaitConditions::sizeGreaterThan,
                AwaitConditions::sizeAtLeast,
                AwaitConditions::sizeLessThan,
                AwaitConditions::sizeAtMost);

        for (var factory : factories) {
            assertThrows(IllegalArgumentException.class, () -> factory.apply(-1));
            assertDoesNotThrow(() -> factory.apply(0));
        }
    }

    @Test
    void stateFactoriesAlwaysCreateThePublishedFieldDescriptors() {
        assertNotNull(StructuralConditions.empty());
        assertNotNull(StructuralConditions.nonEmpty());
    }

    private static void assertCollectionEvaluation(Case testCase,
            boolean explained) throws Exception {
        var matching = new ProbeContainers.ProbeCollection<Object>(
                testCase.matchingSize());
        var mismatching = new ProbeContainers.ProbeCollection<Object>(
                testCase.mismatchingSize());
        ConditionRuntime<ProbeContainers.ProbeCollection<Object>,
                ProbeContainers.ProbeCollection<Object>> runtime = explained
                        ? collection(testCase.condition().because("reason"))
                        : collection(testCase.condition());

        assertSatisfied(runtime.evaluate(matching), matching);
        assertUnsatisfied(runtime.evaluate(mismatching),
                mismatch("collection", testCase, testCase.mismatchingSize()));
        assertEquals("collection " + testCase.description(),
                runtime.description().get());
        assertEquals(explained ? "reason" : null, runtime.explanation());
        assertNoFallback(matching);
        assertNoFallback(mismatching);
    }

    private static void assertMapEvaluation(Case testCase, boolean explained)
            throws Exception {
        var matching = new ProbeContainers.ProbeMap<Object, Object>(
                testCase.matchingSize());
        var mismatching = new ProbeContainers.ProbeMap<Object, Object>(
                testCase.mismatchingSize());
        ConditionRuntime<ProbeContainers.ProbeMap<Object, Object>,
                ProbeContainers.ProbeMap<Object, Object>> runtime = explained
                        ? map(testCase.condition().because("reason"))
                        : map(testCase.condition());

        assertSatisfied(runtime.evaluate(matching), matching);
        assertUnsatisfied(runtime.evaluate(mismatching),
                mismatch("map", testCase, testCase.mismatchingSize()));
        assertEquals("map " + testCase.description(), runtime.description().get());
        assertEquals(explained ? "reason" : null, runtime.explanation());
        assertNoFallback(matching);
        assertNoFallback(mismatching);
    }

    private static String mismatch(String subject, Case testCase, int size) {
        if (testCase.condition() == AwaitConditions.empty) {
            return subject + " was non-empty";
        }
        if (testCase.condition() == AwaitConditions.nonEmpty) {
            return subject + " was empty";
        }
        return subject + " size was " + size;
    }

    private static <C extends Collection<?>> ConditionRuntime<C, C> collection(
            StructuralCondition condition) {
        return ConditionAdapters.structural(
                condition, "collection", Collection::size);
    }

    private static <C extends Collection<?>> ConditionRuntime<C, C> collection(
            ExplainedStructuralCondition condition) {
        return ConditionAdapters.structural(
                condition, "collection", Collection::size);
    }

    private static <M extends Map<?, ?>> ConditionRuntime<M, M> map(
            StructuralCondition condition) {
        return ConditionAdapters.structural(condition, "map", Map::size);
    }

    private static <M extends Map<?, ?>> ConditionRuntime<M, M> map(
            ExplainedStructuralCondition condition) {
        return ConditionAdapters.structural(condition, "map", Map::size);
    }

    private static CollectionUntil<Object,
            ProbeContainers.ProbeCollection<Object>> timedCollection(
                    ProbeContainers.ProbeCollection<Object> actual) {
        FakeTime time = new FakeTime(0);
        AwaitChain<ProbeContainers.ProbeCollection<Object>> chain = new AwaitChain<>(
                () -> {
                    time.advanceNanos(2);
                    return actual;
                }, WaitConfiguration.defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)), time, time,
                new Interrupts(), new FailureFactory());
        return new CollectionStageAdapters.CollectionAfterUpToStage<>(chain);
    }

    private static MapUntil<Object, Object,
            ProbeContainers.ProbeMap<Object, Object>> timedMap(
                    ProbeContainers.ProbeMap<Object, Object> actual) {
        FakeTime time = new FakeTime(0);
        AwaitChain<ProbeContainers.ProbeMap<Object, Object>> chain = new AwaitChain<>(
                () -> {
                    time.advanceNanos(2);
                    return actual;
                }, WaitConfiguration.defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)), time, time,
                new Interrupts(), new FailureFactory());
        return new MapStageAdapters.MapAfterUpToStage<>(chain);
    }

    private static void assertSatisfied(Evaluation<?> evaluation, Object actual) {
        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
        assertSame(actual, evaluation.result());
        assertNull(evaluation.mismatch());
    }

    private static void assertUnsatisfied(Evaluation<?> evaluation,
            String mismatch) {
        assertEquals(Evaluation.Status.UNSATISFIED, evaluation.status());
        assertNull(evaluation.result());
        assertEquals(mismatch, evaluation.mismatch());
    }

    private static void assertNoFallback(
            ProbeContainers.ProbeCollection<?> probe) {
        assertEquals(1, probe.sizeCalls);
        assertEquals(0, probe.isEmptyCalls);
        assertEquals(0, probe.iteratorCalls);
    }

    private static void assertNoFallback(ProbeContainers.ProbeMap<?, ?> probe) {
        assertEquals(1, probe.sizeCalls);
        assertEquals(0, probe.isEmptyCalls);
        assertEquals(0, probe.entrySetCalls);
    }

    private record Case(StructuralCondition condition, int matchingSize,
            int mismatchingSize, String description) {
    }

    private record Pair(StructuralCondition positive, StructuralCondition negative) {
    }
}

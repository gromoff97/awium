package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.structural;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import io.github.gromoff97.awium.diagnostics.FailureFactory;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.await.StructuralAwait;
import io.github.gromoff97.awium.await.stages.StructuralAwaitStage;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
            new Case(empty, 0, 1, "to be empty"),
            new Case(nonEmpty, 1, 0, "to be non-empty"),
            new Case(sizeExactly(2), 2, 1,
                    "size to be exactly 2"),
            new Case(sizeNotExactly(2), 1, 2,
                    "size not to be exactly 2"),
            new Case(sizeGreaterThan(2), 3, 2,
                    "size to be greater than 2"),
            new Case(sizeAtLeast(2), 2, 1,
                    "size to be at least 2"),
            new Case(sizeLessThan(2), 1, 2,
                    "size to be less than 2"),
            new Case(sizeAtMost(2), 2, 3,
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
                new Pair(sizeExactly(2),
                        sizeNotExactly(2)),
                new Pair(sizeGreaterThan(2),
                        sizeAtMost(2)),
                new Pair(sizeAtLeast(2),
                        sizeLessThan(2)));

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
                await((CollectionSource<
                        ProbeContainers.ProbeCollection<Object>>) () -> collection)
                        .until(nonEmpty);
        ProbeContainers.ProbeMap<Object, Object> returnedMap =
                await((MapSource<
                        ProbeContainers.ProbeMap<Object, Object>>) () -> map)
                        .until(nonEmpty.because("required"));

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
                        .until(empty));
        assertThrows(AwaitTimeoutException.class,
                () -> timedCollection(explainedCollection)
                        .until(empty.because("required")));
        assertThrows(AwaitTimeoutException.class,
                () -> timedMap(rawMap).until(empty));
        assertThrows(AwaitTimeoutException.class,
                () -> timedMap(explainedMap)
                        .until(empty.because("required")));

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
                () -> await((CollectionSource<
                        ProbeContainers.ProbeCollection<Object>>) () -> collection)
                        .until(nonEmpty)).getCause());
        assertSame(explainedCollectionCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await((CollectionSource<
                        ProbeContainers.ProbeCollection<Object>>)
                        () -> explainedCollection)
                        .until(nonEmpty.because("required")))
                .getCause());
        assertSame(mapCause, assertThrows(AwaitConditionEvaluationException.class,
                () -> await((MapSource<
                        ProbeContainers.ProbeMap<Object, Object>>) () -> map)
                        .until(nonEmpty)).getCause());
        assertSame(explainedMapCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await((MapSource<
                        ProbeContainers.ProbeMap<Object, Object>>) () -> explainedMap)
                        .until(nonEmpty.because("required")))
                .getCause());

        assertNoFallback(collection);
        assertNoFallback(explainedCollection);
        assertNoFallback(map);
        assertNoFallback(explainedMap);
    }

    @Test
    void sizeFactoriesRejectNegativeBoundsAndAllowZero() {
        List<java.util.function.IntFunction<StructuralCondition>> factories = List.of(
                ConditionProvider::sizeExactly,
                ConditionProvider::sizeNotExactly,
                ConditionProvider::sizeGreaterThan,
                ConditionProvider::sizeAtLeast,
                ConditionProvider::sizeLessThan,
                ConditionProvider::sizeAtMost);

        for (var factory : factories) {
            assertThrows(IllegalArgumentException.class, () -> factory.apply(-1));
            assertDoesNotThrow(() -> factory.apply(0));
        }
    }

    private static void assertCollectionEvaluation(Case testCase,
            boolean explained) throws Exception {
        var matching = new ProbeContainers.ProbeCollection<Object>(
                testCase.matchingSize());
        var mismatching = new ProbeContainers.ProbeCollection<Object>(
                testCase.mismatchingSize());
        RuntimeCondition<ProbeContainers.ProbeCollection<Object>,
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
        RuntimeCondition<ProbeContainers.ProbeMap<Object, Object>,
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
        if (testCase.condition() == empty) {
            return subject + " was non-empty";
        }
        if (testCase.condition() == nonEmpty) {
            return subject + " was empty";
        }
        return subject + " size was " + size;
    }

    private static <C extends Collection<?>> RuntimeCondition<C, C> collection(
            StructuralCondition condition) {
        return structural(
                condition, "collection", Collection::size);
    }

    private static <C extends Collection<?>> RuntimeCondition<C, C> collection(
            StructuralCondition.ExplainedCondition condition) {
        return structural(
                condition, "collection", Collection::size);
    }

    private static <M extends Map<?, ?>> RuntimeCondition<M, M> map(
            StructuralCondition condition) {
        return structural(condition, "map", Map::size);
    }

    private static <M extends Map<?, ?>> RuntimeCondition<M, M> map(
            StructuralCondition.ExplainedCondition condition) {
        return structural(condition, "map", Map::size);
    }

    private static StructuralAwait.Until<
            ProbeContainers.ProbeCollection<Object>> timedCollection(
                    ProbeContainers.ProbeCollection<Object> actual) {
        FakeTime time = new FakeTime(0);
        return new StructuralAwaitStage<>(
                (CollectionSource<ProbeContainers.ProbeCollection<Object>>) () -> {
                    time.advanceNanos(2);
                    return actual;
                }, Collection::size,
                defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)), time, time,
                new FailureFactory());
    }

    private static StructuralAwait.Until<
            ProbeContainers.ProbeMap<Object, Object>> timedMap(
                    ProbeContainers.ProbeMap<Object, Object> actual) {
        FakeTime time = new FakeTime(0);
        return new StructuralAwaitStage<>(
                (MapSource<ProbeContainers.ProbeMap<Object, Object>>) () -> {
                    time.advanceNanos(2);
                    return actual;
                }, Map::size,
                defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)), time, time,
                new FailureFactory());
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

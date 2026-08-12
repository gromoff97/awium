package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.structural;
import static io.github.gromoff97.awium.conditioning.conditions.StructuralCondition.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.await.stages.StructuralAwaitStage;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void rawConditionsUseOneSizeReadForCollections()
            throws Exception {
        for (Case testCase : CASES) {
            assertCollectionEvaluation(testCase);
        }
    }

    @Test
    void nullContainersShortCircuitRawConditions()
            throws Exception {
        assertUnsatisfied(collection(empty).evaluate(null),
                "collection was null");
        assertUnsatisfied(RuntimeCondition.<Map<?, ?>>structural(
                        empty, "map", Map::size).evaluate(null),
                "map was null");
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
        assertEquals(1, collection.sizeCalls);
        assertEquals(1, map.sizeCalls);
    }

    @Test
    void terminalDiagnosticsReuseTheCapturedSize() {
        var rawCollection = new ProbeContainers.ProbeCollection<Object>(1);
        var rawMap = new ProbeContainers.ProbeMap<Object, Object>(1);
        FakeTime collectionTime = new FakeTime(0);
        FakeTime mapTime = new FakeTime(0);

        AwaitTimeoutException collectionFailure = assertThrows(
                AwaitTimeoutException.class,
                () -> new StructuralAwaitStage<>(
                        (CollectionSource<ProbeContainers.ProbeCollection<Object>>)
                                () -> {
                                    collectionTime.advanceNanos(2);
                                    return rawCollection;
                                },
                        Collection::size,
                        defaults().withEvery(Duration.ofNanos(1))
                                .withUpTo(Duration.ofNanos(2)),
                        collectionTime, collectionTime).until(empty));
        AwaitTimeoutException mapFailure = assertThrows(AwaitTimeoutException.class,
                () -> new StructuralAwaitStage<>(
                        (MapSource<ProbeContainers.ProbeMap<Object, Object>>)
                                () -> {
                                    mapTime.advanceNanos(2);
                                    return rawMap;
                                },
                        Map::size,
                        defaults().withEvery(Duration.ofNanos(1))
                                .withUpTo(Duration.ofNanos(2)),
                        mapTime, mapTime).until(empty));

        assertTrue(collectionFailure.getMessage()
                .contains("collection was non-empty"));
        assertTrue(mapFailure.getMessage().contains("map was non-empty"));
        assertEquals(1, rawCollection.sizeCalls);
        assertEquals(1, rawMap.sizeCalls);
    }

    @Test
    void throwingSizeIsAnExactFailFastConditionCauseWithoutFallback() {
        var collectionCause = new IllegalStateException("collection size failed");
        var mapCause = new IllegalStateException("map size failed");
        var collection = new ProbeContainers.ProbeCollection<Object>(
                collectionCause);
        var map = new ProbeContainers.ProbeMap<Object, Object>(mapCause);

        assertSame(collectionCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await((CollectionSource<
                        ProbeContainers.ProbeCollection<Object>>) () -> collection)
                        .until(nonEmpty)).getCause());
        assertSame(mapCause, assertThrows(AwaitConditionEvaluationException.class,
                () -> await((MapSource<
                        ProbeContainers.ProbeMap<Object, Object>>) () -> map)
                        .until(nonEmpty)).getCause());

        assertEquals(1, collection.sizeCalls);
        assertEquals(1, map.sizeCalls);
    }

    @Test
    void sizedFactoryRejectsNegativeBoundsAndAllowsZero() {
        assertThrows(IllegalArgumentException.class, () -> sizeExactly(-1));
        assertDoesNotThrow(() -> sizeExactly(0));
    }

    private static void assertCollectionEvaluation(Case testCase)
            throws Exception {
        var matching = new ProbeContainers.ProbeCollection<Object>(
                testCase.matchingSize());
        var mismatching = new ProbeContainers.ProbeCollection<Object>(
                testCase.mismatchingSize());
        RuntimeCondition<ProbeContainers.ProbeCollection<Object>,
                ProbeContainers.ProbeCollection<Object>> runtime =
                        collection(testCase.condition());

        Evaluation<?> satisfied = runtime.evaluate(matching);
        assertEquals(Evaluation.Status.SATISFIED, satisfied.status());
        assertSame(matching, satisfied.result());
        assertNull(satisfied.mismatch());
        assertUnsatisfied(runtime.evaluate(mismatching),
                testCase.condition() == empty
                        ? "collection was non-empty"
                        : testCase.condition() == nonEmpty
                                ? "collection was empty"
                                : "collection size was "
                                        + testCase.mismatchingSize());
        assertEquals("collection " + testCase.description(),
                runtime.description().get());
        assertNull(runtime.explanation());
        assertEquals(1, matching.sizeCalls);
        assertEquals(1, mismatching.sizeCalls);
    }

    private static <C extends Collection<?>> RuntimeCondition<C, C> collection(
            StructuralCondition condition) {
        return structural(
                condition, "collection", Collection::size);
    }

    private static void assertUnsatisfied(Evaluation<?> evaluation,
            String mismatch) {
        assertEquals(Evaluation.Status.UNSATISFIED, evaluation.status());
        assertNull(evaluation.result());
        assertEquals(mismatch, evaluation.mismatch());
    }

    private record Case(StructuralCondition condition, int matchingSize,
            int mismatchingSize, String description) {
    }
}

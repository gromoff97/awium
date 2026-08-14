package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.*;
import static io.github.gromoff97.awium.conditioning.conditions.StructuralCondition.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedStructuralAwait;
import static java.time.Duration.ofNanos;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;
import io.github.gromoff97.awium.sources.Source;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuralConditionsTest {

    @Test
    void rawConditionsUseOneSizeReadForCollections()
            throws Exception {
        for (Case testCase : cases()) {
            assertCollectionEvaluation(testCase);
        }
    }

    @Test
    void nullContainersRemainUnsatisfied() {
        FakeTime collectionTime = new FakeTime(0);
        FakeTime mapTime = new FakeTime(0);

        assertThrows(AwaitTimeoutException.class,
                () -> timedStructuralAwait((Source<Collection<?>>) () -> null,
                        "collection", Collection::size,
                        defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        collectionTime, collectionTime).until(empty));
        assertThrows(AwaitTimeoutException.class,
                () -> timedStructuralAwait((Source<Map<?, ?>>) () -> null,
                        "map", Map::size,
                        defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        mapTime, mapTime).until(empty));
    }

    @Test
    void publicFacadesKeepCollectionAndMapDiagnosticsDistinct() {
        assertSubjectFailure(() -> await(
                        (CollectionSource<List<String>>) () -> List.of("value"))
                        .every(ofNanos(1))
                        .upTo(ofNanos(2)).until(empty),
                "collection", "map");
        assertSubjectFailure(() -> await(
                        (MapSource<Map<String, String>>)
                                () -> Map.of("key", "value"))
                        .every(ofNanos(1))
                        .upTo(ofNanos(2)).until(empty),
                "map", "collection");
    }

    @Test
    void terminalDiagnosticsReuseTheCapturedSize() {
        var rawCollection = new ProbeContainers.ProbeCollection<Object>(1);
        var rawMap = new ProbeContainers.ProbeMap<Object, Object>();
        FakeTime collectionTime = new FakeTime(0);
        FakeTime mapTime = new FakeTime(0);

        AwaitTimeoutException collectionFailure = assertThrows(
                AwaitTimeoutException.class,
                () -> timedStructuralAwait(
                        (Source<ProbeContainers.ProbeCollection<Object>>)
                                () -> {
                                    collectionTime.advanceNanos(2);
                                    return rawCollection;
                                },
                        "collection", Collection::size,
                        defaults().withEvery(ofNanos(1))
                                .withUpTo(ofNanos(2)),
                        collectionTime, collectionTime).until(empty));
        AwaitTimeoutException mapFailure = assertThrows(AwaitTimeoutException.class,
                () -> timedStructuralAwait(
                        (Source<ProbeContainers.ProbeMap<Object, Object>>)
                                () -> {
                                    mapTime.advanceNanos(2);
                                    return rawMap;
                                },
                        "map", Map::size,
                        defaults().withEvery(ofNanos(1))
                                .withUpTo(ofNanos(2)),
                        mapTime, mapTime).until(empty));

        assertTrue(collectionFailure.getMessage().contains("collection"));
        assertTrue(mapFailure.getMessage().contains("map"));
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

    @Test
    void structuralAwaitValidatesBeforeSourceRetrieval() {
        FakeTime time = new FakeTime(0);
        var sourceCalls = new int[1];
        Source<List<String>> source = () -> {
            sourceCalls[0]++;
            return List.of();
        };

        assertTrue(assertThrows(NullPointerException.class,
                () -> timedStructuralAwait(source, "collection", List::size,
                        defaults(), time, time).until((StructuralCondition) null))
                .getMessage().contains("condition"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> timedStructuralAwait(source, null, List::size,
                        defaults(), time, time).until(empty))
                .getMessage().contains("subject"));
        assertEquals("subject must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> timedStructuralAwait(source, " \n ", List::size,
                                defaults(), time, time).until(empty))
                        .getMessage());
        assertTrue(assertThrows(NullPointerException.class,
                () -> timedStructuralAwait(source, "collection", null,
                        defaults(), time, time))
                .getMessage().contains("size function"));
        assertEquals(0, sourceCalls[0]);
    }

    private static void assertCollectionEvaluation(Case testCase)
            throws Exception {
        var matching = new ProbeContainers.ProbeCollection<Object>(
                testCase.matchingSize());
        var mismatching = new ProbeContainers.ProbeCollection<Object>(
                testCase.mismatchingSize());
        Evaluation<?> satisfied = testCase.condition().evaluate(
                matching.size(), matching, "collection");
        assertEquals(SATISFIED, satisfied.status());
        assertSame(matching, satisfied.result());
        assertNull(satisfied.mismatch());
        assertUnsatisfied(testCase.condition().evaluate(
                mismatching.size(), mismatching, "collection"));
        assertTrue(!testCase.condition().description("collection").isBlank());
        assertEquals(1, matching.sizeCalls);
        assertEquals(1, mismatching.sizeCalls);
    }

    private static void assertUnsatisfied(Evaluation<?> evaluation) {
        assertEquals(UNSATISFIED, evaluation.status());
        assertNull(evaluation.result());
        assertTrue(!evaluation.mismatch().isBlank());
    }

    private static void assertSubjectFailure(
            org.junit.jupiter.api.function.Executable terminal,
            String subject, String otherSubject) {
        String message = assertThrows(
                AwaitTimeoutException.class, terminal).getMessage();
        assertTrue(message.contains(subject));
        assertFalse(message.contains(otherSubject));
    }

    private static List<Case> cases() {
        return List.of(new Case(empty, 0, 1), new Case(nonEmpty, 1, 0),
                new Case(sizeExactly(2), 2, 1),
                new Case(sizeNotExactly(2), 1, 2),
                new Case(sizeGreaterThan(2), 3, 2),
                new Case(sizeAtLeast(2), 2, 1),
                new Case(sizeLessThan(2), 1, 2),
                new Case(sizeAtMost(2), 2, 3));
    }

    private record Case(StructuralCondition condition, int matchingSize,
            int mismatchingSize) {}
}

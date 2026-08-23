package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedCollectionAwait;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNSATISFIED;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.noElements;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.singleElement;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.hasElements;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.elementCountAtLeast;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.elementCountAtMost;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.elementCountBetween;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.elementCount;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.elementCountGreaterThan;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.elementCountLessThan;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.elementCountIsNot;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CollectionSizeConditionsTest {

    @Test
    void hasSingleElementReturnsTheTypedElement() {
        String element = new String("element");
        CollectionSource<ArrayList<String>> source = () -> new ArrayList<>(List.of(element));

        String selected = await(source).until(singleElement);
        String explained = await(source).until(
                singleElement.because("exactly one result is required"));
        String nullElement = await((CollectionSource<List<String>>)
                () -> Collections.singletonList(null)).until(singleElement);

        assertSame(element, selected);
        assertSame(element, explained);
        assertNull(nullElement);
        assertEquals("collection has a single element", singleElement.description());
        assertEquals("collection size was 0",
                singleElement.evaluate(List.of()).mismatch());
        assertEquals("collection size was 2",
                singleElement.evaluate(List.of("first", "second")).mismatch());
    }

    @Test
    void conditionsEvaluateEverySizeRelation() throws Exception {
        for (Case testCase : cases()) {
            var matching = new ProbeContainers.ProbeCollection<Object>(testCase.matchingSize());
            var mismatching = new ProbeContainers.ProbeCollection<Object>(testCase.mismatchingSize());

            Evaluation<?> satisfied = testCase.condition().delegate().evaluate(matching);
            assertEquals(SATISFIED, satisfied.status());
            assertSame(matching, satisfied.result());
            assertNull(satisfied.mismatch());
            assertUnsatisfied(testCase.condition().delegate().evaluate(mismatching));
            assertFalse(testCase.condition().delegate().description().isBlank());
            assertEquals(1, matching.sizeCalls);
            assertEquals(1, mismatching.sizeCalls);
        }
        assertEquals("collection size was 1",
                elementCount(2).delegate().evaluate(List.of("value")).mismatch());
    }

    @Test
    void nullCollectionRemainsUnsatisfied() {
        FakeTime time = new FakeTime(0);

        assertThrows(AwaitTimeoutException.class,
                () -> timedCollectionAwait((Source<Collection<Object>>) () -> null,
                        defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(noElements));
    }

    @Test
    void diagnosticsUseCollectionVocabularyAndCapturedSize() {
        var actual = new ProbeContainers.ProbeCollection<Object>(1);
        FakeTime time = new FakeTime(0);

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> timedCollectionAwait(
                        (Source<ProbeContainers.ProbeCollection<Object>>) () -> {
                            time.advanceNanos(2);
                            return actual;
                        }, defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(noElements));

        assertTrue(failure.getMessage().contains("collection"));
        assertFalse(failure.getMessage().contains("map"));
        assertEquals(1, actual.sizeCalls);
    }

    @Test
    void throwingSizeIsTheExactFailFastConditionCause() {
        var cause = new IllegalStateException("collection size failed");
        var collection = new ProbeContainers.ProbeCollection<Object>(cause);

        assertSame(cause, assertThrows(AwaitConditionEvaluationException.class,
                () -> await((CollectionSource<ProbeContainers.ProbeCollection<Object>>)
                        () -> collection).until(hasElements)).getCause());
        assertEquals(1, collection.sizeCalls);
    }

    @Test
    void sizedFactoriesRejectNegativeBoundsAndAllowZero() {
        assertThrows(IllegalArgumentException.class, () -> elementCount(-1));
        assertThrows(IllegalArgumentException.class, () -> elementCountBetween(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> elementCountBetween(2, 1));
        assertDoesNotThrow(() -> elementCount(0));
        assertDoesNotThrow(() -> elementCountBetween(0, 0));
    }

    @Test
    void betweenIncludesBothBoundsAndRejectsValuesOutsideThem() throws Exception {
        assertEquals(SATISFIED, elementCountBetween(2, 4).delegate().evaluate(List.of(1, 2)).status());
        assertEquals(SATISFIED, elementCountBetween(2, 4).delegate().evaluate(List.of(1, 2, 3, 4)).status());
        assertUnsatisfied(elementCountBetween(2, 4).delegate().evaluate(List.of(1)));
        assertUnsatisfied(elementCountBetween(2, 4).delegate().evaluate(List.of(1, 2, 3, 4, 5)));
    }

    @Test
    void nullConditionIsRejectedBeforeSourceRetrieval() {
        FakeTime time = new FakeTime(0);
        int[] sourceCalls = {0};
        Source<List<String>> source = () -> {
            sourceCalls[0]++;
            return List.of();
        };

        assertTrue(assertThrows(NullPointerException.class,
                () -> timedCollectionAwait(source, defaults(), time, time).until(
                        (PreservingCondition<Collection<String>>) null))
                .getMessage().contains("condition"));
        assertEquals(0, sourceCalls[0]);
    }

    private static void assertUnsatisfied(Evaluation<?> evaluation) {
        assertEquals(UNSATISFIED, evaluation.status());
        assertNull(evaluation.result());
        assertFalse(evaluation.mismatch().isBlank());
    }

    private static List<Case> cases() {
        return List.of(new Case(noElements, 0, 1), new Case(hasElements, 1, 0),
                new Case(elementCount(2), 2, 1),
                new Case(elementCountIsNot(2), 1, 2),
                new Case(elementCountGreaterThan(2), 3, 2),
                new Case(elementCountAtLeast(2), 2, 1),
                new Case(elementCountLessThan(2), 1, 2),
                new Case(elementCountAtMost(2), 2, 3));
    }

    private record Case(PreservingCondition<Collection<?>> condition, int matchingSize,
            int mismatchingSize) {}
}

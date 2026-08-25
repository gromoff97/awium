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
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.empty;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.single;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.nonEmpty;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeAtLeast;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeAtMost;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeBetween;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.size;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeGreaterThan;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeLessThan;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeIsNot;
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
    void hasSingleElementReturnsTheTypedElement() throws Exception {
        String element = new String("element");
        CollectionSource<ArrayList<String>> source = () -> new ArrayList<>(List.of(element));

        String selected = await(source).until(single);
        String explained = await(source).until(
                single.because("exactly one result is required"));
        String nullElement = await((CollectionSource<List<String>>)
                () -> Collections.singletonList(null)).until(single);

        assertSame(element, selected);
        assertSame(element, explained);
        assertNull(nullElement);
        assertEquals("collection has a single element", single.delegate().description());
        assertEquals("collection size was 0",
                single.delegate().evaluate(List.of()).mismatch());
        assertEquals("collection size was 2",
                single.delegate().evaluate(List.of("first", "second")).mismatch());
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
                size(2).delegate().evaluate(List.of("value")).mismatch());
    }

    @Test
    void nullCollectionRemainsUnsatisfied() {
        FakeTime time = new FakeTime(0);

        assertThrows(AwaitTimeoutException.class,
                () -> timedCollectionAwait((Source<Collection<Object>>) () -> null,
                        defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(empty));
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
                        time, time).until(empty));

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
                        () -> collection).until(nonEmpty)).getCause());
        assertEquals(1, collection.sizeCalls);
    }

    @Test
    void sizedFactoriesRejectNegativeBoundsAndAllowZero() {
        assertThrows(IllegalArgumentException.class, () -> size(-1));
        assertThrows(IllegalArgumentException.class, () -> sizeBetween(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> sizeBetween(2, 1));
        assertDoesNotThrow(() -> size(0));
        assertDoesNotThrow(() -> sizeBetween(0, 0));
    }

    @Test
    void betweenIncludesBothBoundsAndRejectsValuesOutsideThem() throws Exception {
        assertEquals(SATISFIED, sizeBetween(2, 4).delegate().evaluate(List.of(1, 2)).status());
        assertEquals(SATISFIED, sizeBetween(2, 4).delegate().evaluate(List.of(1, 2, 3, 4)).status());
        assertUnsatisfied(sizeBetween(2, 4).delegate().evaluate(List.of(1)));
        assertUnsatisfied(sizeBetween(2, 4).delegate().evaluate(List.of(1, 2, 3, 4, 5)));
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
        return List.of(new Case(empty, 0, 1), new Case(nonEmpty, 1, 0),
                new Case(size(2), 2, 1),
                new Case(sizeIsNot(2), 1, 2),
                new Case(sizeGreaterThan(2), 3, 2),
                new Case(sizeAtLeast(2), 2, 1),
                new Case(sizeLessThan(2), 1, 2),
                new Case(sizeAtMost(2), 2, 3));
    }

    private record Case(PreservingCondition<Collection<?>> condition, int matchingSize,
            int mismatchingSize) {}
}

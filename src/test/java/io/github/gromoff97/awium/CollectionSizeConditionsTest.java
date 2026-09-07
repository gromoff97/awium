package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;

import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;

import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Source.CollectionSource;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.timedCollectionAwait;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.CollectionConditions.empty;
import static io.github.gromoff97.awium.CollectionConditions.single;
import static io.github.gromoff97.awium.CollectionConditions.nonEmpty;
import static io.github.gromoff97.awium.CollectionConditions.sizeAtLeast;
import static io.github.gromoff97.awium.CollectionConditions.sizeAtMost;
import static io.github.gromoff97.awium.CollectionConditions.sizeBetween;
import static io.github.gromoff97.awium.CollectionConditions.size;
import static io.github.gromoff97.awium.CollectionConditions.sizeGreaterThan;
import static io.github.gromoff97.awium.CollectionConditions.sizeLessThan;
import static io.github.gromoff97.awium.CollectionConditions.sizeIsNot;
import static io.github.gromoff97.awium.WaitConfiguration.defaults;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CollectionSizeConditionsTest {

    @Test
    void hasSingleElementReturnsTheTypedElement() throws Exception {
        var pollingTime = new FakeTime(0);
        String element = new String("element");
        CollectionSource<ArrayList<String>> source = () -> new ArrayList<>(List.of(element));

        String selected = await(source).usingTime(pollingTime, pollingTime).until(single);
        String explained = await(source).usingTime(pollingTime, pollingTime).until(single.because("exactly one result is required"));
        String nullElement = await((CollectionSource<List<String>>)
                () -> Collections.singletonList(null)).usingTime(pollingTime, pollingTime).until(single);

        assertSame(element, selected);
        assertSame(element, explained);
        assertNull(nullElement);
        assertEquals("collection has a single element", description(single));
        assertEquals("collection size was 0",
                mismatch(evaluate(single, List.of())));
        assertEquals("collection size was 2",
                mismatch(evaluate(single, List.of("first", "second"))));
    }

    @Test
    void conditionsEvaluateEverySizeRelation() throws Exception {
        for (Case testCase : cases()) {
            var matching = new ProbeContainers.ProbeCollection<Object>(testCase.matchingSize());
            var mismatching = new ProbeContainers.ProbeCollection<Object>(testCase.mismatchingSize());

            ConditionResult<?> satisfied = evaluate(testCase.condition(), matching);
            assertEquals(Satisfied.class, satisfied.getClass());
            assertSame(matching, result(satisfied));
            assertUnsatisfied(evaluate(testCase.condition(), mismatching));
            assertFalse(description(testCase.condition()).isBlank());
            assertEquals(1, matching.sizeCalls);
            assertEquals(1, mismatching.sizeCalls);
        }
        assertEquals("collection size was 1",
                mismatch(evaluate(size(2), List.of("value"))));
    }

    @Test
    void nullCollectionRemainsUnsatisfied() {
        FakeTime time = new FakeTime(0);

        assertFailure(TIMEOUT,
                () -> timedCollectionAwait((Source<Collection<Object>>) () -> null,
                        defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(empty));
    }

    @Test
    void diagnosticsUseCollectionVocabularyAndCapturedSize() {
        var actual = new ProbeContainers.ProbeCollection<Object>(1);
        FakeTime time = new FakeTime(0);

        var failure = assertFailure(TIMEOUT,
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
        var pollingTime = new FakeTime(0);
        var cause = new IllegalStateException("collection size failed");
        var collection = new ProbeContainers.ProbeCollection<Object>(cause);

        assertSame(cause, assertFailure(CONDITION_FAILED,
                () -> await((CollectionSource<ProbeContainers.ProbeCollection<Object>>)
                        () -> collection).usingTime(pollingTime, pollingTime).until(nonEmpty)).getCause());
        assertEquals(1, collection.sizeCalls);
    }

    @Test
    void sizedFactoriesRejectNegativeBoundsAndAllowZero() {
        assertEquals("size must be non-negative", assertThrows(
                IllegalArgumentException.class, () -> size(-1)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> sizeBetween(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> sizeBetween(2, 1));
        assertDoesNotThrow(() -> size(0));
        assertDoesNotThrow(() -> sizeBetween(0, 0));
    }

    @Test
    void betweenIncludesBothBoundsAndRejectsValuesOutsideThem() throws Exception {
        assertEquals(Satisfied.class, evaluate(sizeBetween(2, 4), List.of(1, 2)).getClass());
        assertEquals(Satisfied.class,
                evaluate(sizeBetween(2, 4), List.of(1, 2, 3, 4)).getClass());
        assertUnsatisfied(evaluate(sizeBetween(2, 4), List.of(1)));
        assertUnsatisfied(evaluate(sizeBetween(2, 4), List.of(1, 2, 3, 4, 5)));
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
                () -> timedCollectionAwait(source, defaults(), time, time).until((PreservingCondition<Collection<String>>) null))
                .getMessage().contains("condition"));
        assertEquals(0, sourceCalls[0]);
    }

    private static void assertUnsatisfied(ConditionResult<?> evaluation) {
        assertInstanceOf(ConditionResult.Unsatisfied.class, evaluation);
        assertFalse(mismatch(evaluation).isBlank());
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

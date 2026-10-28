package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.ConditionResult.Satisfied;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.CollectionConditions.empty;
import static io.github.gromoff97.awium.CollectionConditions.nonEmpty;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.CollectionConditions.hasSize;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeAtLeast;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeAtMost;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeBetween;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeGreaterThan;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeNotEqualTo;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeLessThan;
import static io.github.gromoff97.awium.ConditionTestRuntime.assertUnsatisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionSizeConditionsTest {

    @Test
    void hasSingleElementReturnsTheTypedElement() {
        var pollingTime = new FakeTime(0);
        String element = new String("element");
        java.util.concurrent.Callable<ArrayList<String>> source = () -> new ArrayList<>(List.of(element));

        String selected = await(source::call).usingTime(pollingTime, pollingTime).until(singleElement);
        String nullElement = await((Await.CollectionSource<List<String>>)
                () -> Collections.singletonList(null)).usingTime(pollingTime, pollingTime).until(singleElement);

        assertSame(element, selected);
        assertNull(nullElement);
        assertTrue(assertFailure(TIMEOUT, () -> await(() -> List.of())
                .usingTime(pollingTime, pollingTime).upTo(ofNanos(1)).until(singleElement))
                .getMessage().contains("collection size was 0"));
        assertTrue(assertFailure(TIMEOUT, () -> await(() -> List.of("first", "second"))
                .usingTime(pollingTime, pollingTime).upTo(ofNanos(1)).until(singleElement))
                .getMessage().contains("collection size was greater than 1"));
    }

    @Test
    void conditionsEvaluateEverySizeRelation() {
        for (Case testCase : cases()) {
            var matching = new ProbeContainers.ProbeCollection<Object>(testCase.matchingSize());
            var mismatching = new ProbeContainers.ProbeCollection<Object>(testCase.mismatchingSize());

            assertSame(matching, result(evaluate(testCase.condition(), matching)));
            assertUnsatisfied(evaluate(testCase.condition(), mismatching));
            assertEquals("collection was null", mismatch(evaluate(testCase.condition(), null)));
            assertEquals(1, matching.sizeCalls);
            assertEquals(1, mismatching.sizeCalls);
        }
        assertEquals("collection size was 1",
                mismatch(evaluate(hasSize(2), List.of("value"))));
    }

    @Test
    void nullCollectionRemainsUnsatisfied() {
        FakeTime time = new FakeTime(0);
        Collection<Object> actual = null;

        assertFailure(TIMEOUT,
                () -> await(() -> actual).usingTime(time, time)
                        .every(ofNanos(1)).upTo(ofNanos(2)).until(empty));
    }

    @Test
    void diagnosticsUseCollectionVocabularyAndCapturedSize() {
        var actual = new ProbeContainers.ProbeCollection<Object>(1);
        FakeTime time = new FakeTime(0);

        var failure = assertFailure(TIMEOUT,
                () -> await(() -> {
                    time.advanceNanos(2);
                    return actual;
                }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(empty));

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
                () -> await(() -> collection).usingTime(pollingTime, pollingTime).until(nonEmpty)).getCause());
        assertEquals(1, collection.sizeCalls);
    }

    @Test
    void sizedFactoriesRejectNegativeBoundsAndAllowZero() {
        assertEquals("size must be non-negative", assertThrows(
                IllegalArgumentException.class, () -> hasSize(-1)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> hasSizeBetween(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> hasSizeBetween(2, 1));
        assertDoesNotThrow(() -> hasSize(0));
        assertDoesNotThrow(() -> hasSizeBetween(0, 0));
    }

    @Test
    void betweenIncludesBothBoundsAndRejectsValuesOutsideThem() {
        assertEquals(Satisfied.class, evaluate(hasSizeBetween(2, 4), List.of(1, 2)).getClass());
        assertEquals(Satisfied.class,
                evaluate(hasSizeBetween(2, 4), List.of(1, 2, 3, 4)).getClass());
        assertUnsatisfied(evaluate(hasSizeBetween(2, 4), List.of(1)));
        assertUnsatisfied(evaluate(hasSizeBetween(2, 4), List.of(1, 2, 3, 4, 5)));
    }

    private static List<Case> cases() {
        return List.of(new Case(empty, 0, 1), new Case(nonEmpty, 1, 0),
                new Case(hasSize(2), 2, 1),
                new Case(hasSizeNotEqualTo(2), 1, 2),
                new Case(hasSizeGreaterThan(2), 3, 2),
                new Case(hasSizeAtLeast(2), 2, 1),
                new Case(hasSizeLessThan(2), 1, 2),
                new Case(hasSizeAtMost(2), 2, 3));
    }

    private record Case(PreservingCondition<Collection<?>> condition, int matchingSize,
            int mismatchingSize) {}
}

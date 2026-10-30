package io.github.gromoff97.awium;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.internal.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.empty;
import static io.github.gromoff97.awium.CollectionConditions.hasSize;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeAtLeast;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeAtMost;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeBetween;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeGreaterThan;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeLessThan;
import static io.github.gromoff97.awium.CollectionConditions.hasSizeNotEqualTo;
import static io.github.gromoff97.awium.CollectionConditions.nonEmpty;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.ConditionTestRuntime.assertUnsatisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static io.github.gromoff97.awium.ProbeContainers.sizeOnlyCollection;
import static java.util.Collections.singletonList;

class CollectionSizeConditionsTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void hasSingleElementReturnsTheTypedElement() {
        String element = new String("element");
        java.util.concurrent.Callable<ArrayList<String>> source = () -> new ArrayList<>(List.of(element));

        String selected = await(source::call).usingTime(time, time).until(singleElement);
        String nullElement = await((Await.CollectionSource<List<String>>)
                () -> singletonList(null)).usingTime(time, time).until(singleElement);

        assertSame(element, selected);
        assertNull(nullElement);
        assertThat(assertFailure(TIMEOUT, () -> await(() -> List.of())
                .usingTime(time, time).upTo(ofNanos(1)).until(singleElement)))
                .hasMessageContaining("collection size was 0");
        assertThat(assertFailure(TIMEOUT, () -> await(() -> List.of("first", "second"))
                .usingTime(time, time).upTo(ofNanos(1)).until(singleElement)))
                .hasMessageContaining("collection size was greater than 1");
    }

    @ParameterizedTest(name = "{0}: matches size {1}, rejects size {2}")
    @MethodSource("cases")
    void conditionsEvaluateEverySizeRelation(PreservingCondition<Collection<?>> condition, int matchingSize, int mismatchingSize) {
        var matching = sizeOnlyCollection(matchingSize);
        var mismatching = sizeOnlyCollection(mismatchingSize);

        assertSame(matching, result(evaluate(condition, matching)));
        assertUnsatisfied(evaluate(condition, mismatching));
        assertEquals("collection was null", mismatch(evaluate(condition, null)));
        verify(matching).size();
        verify(mismatching).size();
    }

    @Test
    void nullCollectionRemainsUnsatisfied() {
        Collection<Object> actual = null;

        assertFailure(TIMEOUT,
                () -> await(() -> actual).usingTime(time, time)
                        .every(ofNanos(1)).upTo(ofNanos(2)).until(empty));
    }

    @Test
    void diagnosticsUseCollectionVocabularyAndCapturedSize() {
        assertEquals("collection size was 1", mismatch(evaluate(hasSize(2), List.of("value"))));
        var actual = sizeOnlyCollection(1);

        var failure = assertFailure(TIMEOUT,
                () -> await(() -> {
                    time.advanceNanos(2);
                    return actual;
                }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(empty));

        assertThat(failure.getMessage()).contains("collection").doesNotContain("map");
        verify(actual).size();
    }

    @Test
    void throwingSizeIsTheExactFailFastConditionCause() {
        var cause = new IllegalStateException("collection size failed");
        var collection = sizeOnlyCollection(0);
        when(collection.size()).thenThrow(cause);

        assertSame(cause, assertFailure(CONDITION_FAILED,
                () -> await(() -> collection).usingTime(time, time).until(nonEmpty)).getCause());
        verify(collection).size();
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
        assertInstanceOf(Satisfied.class, evaluate(hasSizeBetween(2, 4), List.of(1, 2)));
        assertInstanceOf(Satisfied.class,
                evaluate(hasSizeBetween(2, 4), List.of(1, 2, 3, 4)));
        assertUnsatisfied(evaluate(hasSizeBetween(2, 4), List.of(1)));
        assertUnsatisfied(evaluate(hasSizeBetween(2, 4), List.of(1, 2, 3, 4, 5)));
    }

    private static List<Arguments> cases() {
        return List.of(arguments(empty, 0, 1), arguments(nonEmpty, 1, 0),
                arguments(hasSize(2), 2, 1),
                arguments(hasSizeNotEqualTo(2), 1, 2),
                arguments(hasSizeGreaterThan(2), 3, 2),
                arguments(hasSizeAtLeast(2), 2, 1),
                arguments(hasSizeLessThan(2), 1, 2),
                arguments(hasSizeAtMost(2), 2, 3));
    }

}

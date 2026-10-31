package io.github.gromoff97.awium;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionTestRuntime.assertUnsatisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.empty;
import static io.github.gromoff97.awium.MapConditions.hasSize;
import static io.github.gromoff97.awium.MapConditions.hasSizeAtLeast;
import static io.github.gromoff97.awium.MapConditions.hasSizeAtMost;
import static io.github.gromoff97.awium.MapConditions.hasSizeBetween;
import static io.github.gromoff97.awium.MapConditions.hasSizeGreaterThan;
import static io.github.gromoff97.awium.MapConditions.hasSizeLessThan;
import static io.github.gromoff97.awium.MapConditions.hasSizeNotEqualTo;
import static io.github.gromoff97.awium.MapConditions.nonEmpty;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static io.github.gromoff97.awium.ProbeContainers.sizeOnlyMap;

class MapSizeConditionsTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void hasSingleEntryReturnsTheTypedEntry() {
        java.util.concurrent.Callable<LinkedHashMap<String, Integer>> source = () -> {
            var map = new LinkedHashMap<String, Integer>();
            map.put("key", 42);
            return map;
        };

        Map.Entry<String, Integer> selected = await(source::call).usingTime(time, time).until(singleEntry);

        assertEquals("key", selected.getKey());
        assertEquals(42, selected.getValue());
        assertThat(assertFailure(TIMEOUT, () -> await(() -> Map.of())
                .usingTime(time, time).upTo(ofNanos(1)).until(singleEntry)))
                .hasMessageContaining("map size was 0");
        assertThat(assertFailure(TIMEOUT, () -> await(() -> Map.of("first", 1, "second", 2))
                .usingTime(time, time).upTo(ofNanos(1)).until(singleEntry)))
                .hasMessageContaining("map size was greater than 1");
    }

    @ParameterizedTest
    @MethodSource("cases")
    void conditionsEvaluateEverySizeRelation(Supplier<PreservingCondition<Map<?, ?>>> factory, int matchingSize, int mismatchingSize) {
        var condition = factory.get();
        Map<Integer, Integer> matching = mapWithSize(matchingSize);
        Map<Integer, Integer> mismatching = mapWithSize(mismatchingSize);

        assertSame(matching, result(evaluate(condition, matching)));
        assertUnsatisfied(evaluate(condition, mismatching));
        assertEquals("map was null", mismatch(evaluate(condition, null)));
    }

    @Test
    void nullMapRemainsUnsatisfied() {
        Map<Object, Object> actual = null;

        assertFailure(TIMEOUT,
                () -> await(() -> actual).usingTime(time, time)
                        .every(ofNanos(1)).upTo(ofNanos(2)).until(empty));
    }

    @Test
    void diagnosticsUseMapVocabularyAndCapturedSize() {
        assertEquals("map size was 1", mismatch(evaluate(hasSize(2), Map.of("key", "value"))));
        var actual = sizeOnlyMap();

        var failure = assertFailure(TIMEOUT,
                () -> await(() -> {
                    time.advanceNanos(2);
                    return actual;
                }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(empty));

        assertThat(failure.getMessage()).contains("map").doesNotContain("collection");
        verify(actual).size();
    }

    @Test
    void throwingSizeIsTheExactFailFastConditionCause() {
        var cause = new IllegalStateException("map size failed");
        var map = sizeOnlyMap();
        when(map.size()).thenThrow(cause);

        assertSame(cause, assertFailure(CONDITION_FAILED,
                () -> await(() -> map).usingTime(time, time).until(nonEmpty)).getCause());
        verify(map).size();
    }

    @Test
    void sizedFactoriesRejectNegativeBoundsAndAllowZero() {
        assertThrows(IllegalArgumentException.class, () -> hasSize(-1));
        assertThrows(IllegalArgumentException.class, () -> hasSizeBetween(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> hasSizeBetween(2, 1));
        assertDoesNotThrow(() -> hasSize(0));
        assertDoesNotThrow(() -> hasSizeBetween(0, 0));
    }

    @Test
    void betweenIncludesBothBoundsAndRejectsValuesOutsideThem() {
        assertInstanceOf(Satisfied.class, evaluate(hasSizeBetween(2, 4), mapWithSize(2)));
        assertInstanceOf(Satisfied.class, evaluate(hasSizeBetween(2, 4), mapWithSize(4)));
        assertUnsatisfied(evaluate(hasSizeBetween(2, 4), mapWithSize(1)));
        assertUnsatisfied(evaluate(hasSizeBetween(2, 4), mapWithSize(5)));
    }

    private static Map<Integer, Integer> mapWithSize(int size) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (int value = 0; value < size; value++) {
            map.put(value, value);
        }
        return map;
    }

    private static List<Arguments> cases() {
        return List.of(sizeCase("empty", () -> empty, 0, 1), sizeCase("nonEmpty", () -> nonEmpty, 1, 0),
                sizeCase("hasSize", () -> hasSize(2), 2, 1),
                sizeCase("hasSizeNotEqualTo", () -> hasSizeNotEqualTo(2), 1, 2),
                sizeCase("hasSizeGreaterThan", () -> hasSizeGreaterThan(2), 3, 2),
                sizeCase("hasSizeAtLeast", () -> hasSizeAtLeast(2), 2, 1),
                sizeCase("hasSizeLessThan", () -> hasSizeLessThan(2), 1, 2),
                sizeCase("hasSizeAtMost", () -> hasSizeAtMost(2), 2, 3));
    }

    private static Arguments sizeCase(String name, Supplier<PreservingCondition<Map<?, ?>>> factory, int matchingSize, int mismatchingSize) {
        return argumentSet(name, factory, matchingSize, mismatchingSize);
    }
}

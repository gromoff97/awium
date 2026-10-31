package io.github.gromoff97.awium.conditions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.assertUnsatisfied;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.mismatch;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.rejection;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.result;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.thrown;
import static io.github.gromoff97.awium.conditions.MapConditions.empty;
import static io.github.gromoff97.awium.conditions.MapConditions.hasSize;
import static io.github.gromoff97.awium.conditions.MapConditions.hasSizeAtLeast;
import static io.github.gromoff97.awium.conditions.MapConditions.hasSizeAtMost;
import static io.github.gromoff97.awium.conditions.MapConditions.hasSizeBetween;
import static io.github.gromoff97.awium.conditions.MapConditions.hasSizeGreaterThan;
import static io.github.gromoff97.awium.conditions.MapConditions.hasSizeLessThan;
import static io.github.gromoff97.awium.conditions.MapConditions.hasSizeNotEqualTo;
import static io.github.gromoff97.awium.conditions.MapConditions.nonEmpty;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.ProbeContainers.sizeOnlyMap;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapSizeConditionsTest {

    @Test
    void hasSingleEntryReturnsTheTypedEntry() {
        java.util.concurrent.Callable<LinkedHashMap<String, Integer>> source = () -> {
            var map = new LinkedHashMap<String, Integer>();
            map.put("key", 42);
            return map;
        };

        Map.Entry<String, Integer> selected = await(source::call).until(singleEntry);

        assertEquals("key", selected.getKey());
        assertEquals(42, selected.getValue());
        assertThat(rejection(singleEntry, Map.of()).mismatch()).contains("map size was 0");
        assertThat(rejection(singleEntry, Map.of("first", 1, "second", 2)).mismatch()).contains("map size was greater than 1");
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

        rejection(empty, actual);
    }

    @Test
    void diagnosticsUseMapVocabularyAndCapturedSize() {
        assertEquals("map size was 1", mismatch(evaluate(hasSize(2), Map.of("key", "value"))));
        var actual = sizeOnlyMap();

        var failure = rejection(empty, actual);

        assertThat(failure.mismatch()).contains("map").doesNotContain("collection");
        verify(actual).size();
    }

    @Test
    void throwingSizeIsTheExactFailFastConditionCause() {
        var cause = new IllegalStateException("map size failed");
        var map = sizeOnlyMap();
        when(map.size()).thenThrow(cause);

        assertSame(cause, thrown(nonEmpty, map));
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

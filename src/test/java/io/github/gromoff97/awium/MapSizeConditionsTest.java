package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.internal.ConditionEvaluation.Satisfied;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapSizeConditionsTest {

    @Test
    void hasSingleEntryReturnsTheTypedEntry() {
        var pollingTime = new FakeTime(0);
        java.util.concurrent.Callable<LinkedHashMap<String, Integer>> source = () -> {
            var map = new LinkedHashMap<String, Integer>();
            map.put("key", 42);
            return map;
        };

        Map.Entry<String, Integer> selected = await(source::call).usingTime(pollingTime, pollingTime).until(singleEntry);

        assertEquals("key", selected.getKey());
        assertEquals(42, selected.getValue());
        assertTrue(assertFailure(TIMEOUT, () -> await(() -> Map.of())
                .usingTime(pollingTime, pollingTime).upTo(ofNanos(1)).until(singleEntry))
                .getMessage().contains("map size was 0"));
        assertTrue(assertFailure(TIMEOUT, () -> await(() -> Map.of("first", 1, "second", 2))
                .usingTime(pollingTime, pollingTime).upTo(ofNanos(1)).until(singleEntry))
                .getMessage().contains("map size was greater than 1"));
    }

    @Test
    void conditionsEvaluateEverySizeRelation() {
        for (Case testCase : cases()) {
            Map<Integer, Integer> matching = mapWithSize(testCase.matchingSize());
            Map<Integer, Integer> mismatching = mapWithSize(testCase.mismatchingSize());

            assertSame(matching, result(evaluate(testCase.condition(), matching)));
            assertUnsatisfied(evaluate(testCase.condition(), mismatching));
            assertEquals("map was null", mismatch(evaluate(testCase.condition(), null)));
        }
        assertEquals("map size was 1",
                mismatch(evaluate(hasSize(2), Map.of("key", "value"))));
    }

    @Test
    void nullMapRemainsUnsatisfied() {
        FakeTime time = new FakeTime(0);
        Map<Object, Object> actual = null;

        assertFailure(TIMEOUT,
                () -> await(() -> actual).usingTime(time, time)
                        .every(ofNanos(1)).upTo(ofNanos(2)).until(empty));
    }

    @Test
    void diagnosticsUseMapVocabularyAndCapturedSize() {
        var actual = new ProbeContainers.ProbeMap<Object, Object>();
        FakeTime time = new FakeTime(0);

        var failure = assertFailure(TIMEOUT,
                () -> await(() -> {
                    time.advanceNanos(2);
                    return actual;
                }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(empty));

        assertTrue(failure.getMessage().contains("map"));
        assertFalse(failure.getMessage().contains("collection"));
        assertEquals(1, actual.sizeCalls);
    }

    @Test
    void throwingSizeIsTheExactFailFastConditionCause() {
        var pollingTime = new FakeTime(0);
        var cause = new IllegalStateException("map size failed");
        var map = new ProbeContainers.ProbeMap<Object, Object>(cause);

        assertSame(cause, assertFailure(CONDITION_FAILED,
                () -> await(() -> map).usingTime(pollingTime, pollingTime).until(nonEmpty)).getCause());
        assertEquals(1, map.sizeCalls);
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
        assertEquals(Satisfied.class, evaluate(hasSizeBetween(2, 4), mapWithSize(2)).getClass());
        assertEquals(Satisfied.class, evaluate(hasSizeBetween(2, 4), mapWithSize(4)).getClass());
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

    private static List<Case> cases() {
        return List.of(new Case(empty, 0, 1), new Case(nonEmpty, 1, 0),
                new Case(hasSize(2), 2, 1),
                new Case(hasSizeNotEqualTo(2), 1, 2),
                new Case(hasSizeGreaterThan(2), 3, 2),
                new Case(hasSizeAtLeast(2), 2, 1),
                new Case(hasSizeLessThan(2), 1, 2),
                new Case(hasSizeAtMost(2), 2, 3));
    }

    private record Case(PreservingCondition<Map<?, ?>> condition, int matchingSize,
            int mismatchingSize) {}
}

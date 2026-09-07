package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;

import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;

import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Source.MapSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.timedMapAwait;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.MapConditions.empty;
import static io.github.gromoff97.awium.MapConditions.sameSizeAs;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.MapConditions.nonEmpty;
import static io.github.gromoff97.awium.MapConditions.size;
import static io.github.gromoff97.awium.MapConditions.sizeAtLeast;
import static io.github.gromoff97.awium.MapConditions.sizeAtMost;
import static io.github.gromoff97.awium.MapConditions.sizeBetween;
import static io.github.gromoff97.awium.MapConditions.sizeGreaterThan;
import static io.github.gromoff97.awium.MapConditions.sizeIsNot;
import static io.github.gromoff97.awium.MapConditions.sizeLessThan;
import static io.github.gromoff97.awium.WaitConfiguration.defaults;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapSizeConditionsTest {

    @Test
    void hasSingleEntryReturnsTheTypedEntry() throws Exception {
        var pollingTime = new FakeTime(0);
        MapSource<LinkedHashMap<String, Integer>> source = () -> {
            var map = new LinkedHashMap<String, Integer>();
            map.put("key", 42);
            return map;
        };

        Map.Entry<String, Integer> selected = await(source).usingTime(pollingTime, pollingTime).until(singleEntry);
        Map.Entry<String, Integer> explained = await(source).usingTime(pollingTime, pollingTime).until(singleEntry.because("exactly one result is required"));

        assertEquals("key", selected.getKey());
        assertEquals(42, selected.getValue());
        assertEquals(selected, explained);
        assertEquals("map has a single entry", description(singleEntry));
        assertEquals("map size was 0",
                mismatch(evaluate(singleEntry, Map.of())));
        assertEquals("map size was 2",
                mismatch(evaluate(singleEntry, Map.of("first", 1, "second", 2))));
    }

    @Test
    void conditionsEvaluateEverySizeRelation() throws Exception {
        for (Case testCase : cases()) {
            Map<Integer, Integer> matching = mapWithSize(testCase.matchingSize());
            Map<Integer, Integer> mismatching = mapWithSize(testCase.mismatchingSize());

            ConditionResult<?> satisfied = evaluate(testCase.condition(), matching);
            assertEquals(Satisfied.class, satisfied.getClass());
            assertSame(matching, result(satisfied));
            assertUnsatisfied(evaluate(testCase.condition(), mismatching));
            assertFalse(description(testCase.condition()).isBlank());
        }
        assertEquals("map size was 1",
                mismatch(evaluate(size(2), Map.of("key", "value"))));
    }

    @Test
    void nullMapRemainsUnsatisfied() {
        FakeTime time = new FakeTime(0);

        assertFailure(TIMEOUT,
                () -> timedMapAwait((Source<Map<Object, Object>>) () -> null,
                        defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(empty));
    }

    @Test
    void diagnosticsUseMapVocabularyAndCapturedSize() {
        var actual = new ProbeContainers.ProbeMap<Object, Object>();
        FakeTime time = new FakeTime(0);

        var failure = assertFailure(TIMEOUT,
                () -> timedMapAwait(
                        (Source<ProbeContainers.ProbeMap<Object, Object>>) () -> {
                            time.advanceNanos(2);
                            return actual;
                        }, defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(empty));

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
                () -> await((MapSource<ProbeContainers.ProbeMap<Object, Object>>)
                        () -> map).usingTime(pollingTime, pollingTime).until(nonEmpty)).getCause());
        assertEquals(1, map.sizeCalls);
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
        assertEquals(Satisfied.class, evaluate(sizeBetween(2, 4), mapWithSize(2)).getClass());
        assertEquals(Satisfied.class, evaluate(sizeBetween(2, 4), mapWithSize(4)).getClass());
        assertUnsatisfied(evaluate(sizeBetween(2, 4), mapWithSize(1)));
        assertUnsatisfied(evaluate(sizeBetween(2, 4), mapWithSize(5)));
    }

    @Test
    void sameSizeAsReadsTheExpectedMapWhenEvaluated() throws Exception {
        var expected = new LinkedHashMap<>(Map.of(1, 1));
        var condition = sameSizeAs(expected);
        expected.put(2, 2);

        assertUnsatisfied(evaluate(condition, Map.of(1, 1)));
        assertEquals(Satisfied.class, evaluate(condition, Map.of(1, 1, 2, 2)).getClass());
    }

    @Test
    void nullConditionIsRejectedBeforeSourceRetrieval() {
        FakeTime time = new FakeTime(0);
        int[] sourceCalls = {0};
        Source<Map<String, String>> source = () -> {
            sourceCalls[0]++;
            return Map.of();
        };

        assertTrue(assertThrows(NullPointerException.class,
                () -> timedMapAwait(source, defaults(), time, time).until((PreservingCondition<Map<String, String>>) null))
                .getMessage().contains("condition"));
        assertEquals(0, sourceCalls[0]);
    }

    private static void assertUnsatisfied(ConditionResult<?> evaluation) {
        assertInstanceOf(ConditionResult.Unsatisfied.class, evaluation);
        assertFalse(mismatch(evaluation).isBlank());
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
                new Case(size(2), 2, 1),
                new Case(sizeIsNot(2), 1, 2),
                new Case(sizeGreaterThan(2), 3, 2),
                new Case(sizeAtLeast(2), 2, 1),
                new Case(sizeLessThan(2), 1, 2),
                new Case(sizeAtMost(2), 2, 3),
                new Case(sameSizeAs(mapWithSize(2)), 2, 1));
    }

    private record Case(PreservingCondition<Map<?, ?>> condition, int matchingSize,
            int mismatchingSize) {}
}

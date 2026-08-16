package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.MapSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedMapAwait;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNSATISFIED;
import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.noEntries;
import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.singleEntry;
import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.hasEntries;
import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.entryCountAtLeast;
import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.entryCountAtMost;
import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.entryCount;
import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.entryCountGreaterThan;
import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.entryCountLessThan;
import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.entryCountNot;
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

class MapSizeConditionsTest {

    @Test
    void hasSingleEntryReturnsTheTypedEntry() {
        MapSource<LinkedHashMap<String, Integer>> source = () -> {
            var map = new LinkedHashMap<String, Integer>();
            map.put("key", 42);
            return map;
        };

        Map.Entry<String, Integer> selected = await(source).until(singleEntry);
        Map.Entry<String, Integer> explained = await(source).until(
                singleEntry.because("exactly one result is required"));

        assertEquals("key", selected.getKey());
        assertEquals(42, selected.getValue());
        assertEquals(selected, explained);
        assertEquals("map has a single entry", singleEntry.description());
        assertEquals("map size was 0",
                singleEntry.evaluate(Map.of()).mismatch());
        assertEquals("map size was 2",
                singleEntry.evaluate(Map.of("first", 1, "second", 2)).mismatch());
    }

    @Test
    void conditionsEvaluateEverySizeRelation() throws Exception {
        for (Case testCase : cases()) {
            Map<Integer, Integer> matching = mapWithSize(testCase.matchingSize());
            Map<Integer, Integer> mismatching = mapWithSize(testCase.mismatchingSize());

            Evaluation<?> satisfied = testCase.condition().delegate().evaluate(matching);
            assertEquals(SATISFIED, satisfied.status());
            assertSame(matching, satisfied.result());
            assertNull(satisfied.mismatch());
            assertUnsatisfied(testCase.condition().delegate().evaluate(mismatching));
            assertFalse(testCase.condition().delegate().description().isBlank());
        }
        assertEquals("map size was 1",
                entryCount(2).delegate().evaluate(Map.of("key", "value")).mismatch());
    }

    @Test
    void nullMapRemainsUnsatisfied() {
        FakeTime time = new FakeTime(0);

        assertThrows(AwaitTimeoutException.class,
                () -> timedMapAwait((Source<Map<Object, Object>>) () -> null,
                        defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(noEntries));
    }

    @Test
    void diagnosticsUseMapVocabularyAndCapturedSize() {
        var actual = new ProbeContainers.ProbeMap<Object, Object>();
        FakeTime time = new FakeTime(0);

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> timedMapAwait(
                        (Source<ProbeContainers.ProbeMap<Object, Object>>) () -> {
                            time.advanceNanos(2);
                            return actual;
                        }, defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(noEntries));

        assertTrue(failure.getMessage().contains("map"));
        assertFalse(failure.getMessage().contains("collection"));
        assertEquals(1, actual.sizeCalls);
    }

    @Test
    void throwingSizeIsTheExactFailFastConditionCause() {
        var cause = new IllegalStateException("map size failed");
        var map = new ProbeContainers.ProbeMap<Object, Object>(cause);

        assertSame(cause, assertThrows(AwaitConditionEvaluationException.class,
                () -> await((MapSource<ProbeContainers.ProbeMap<Object, Object>>)
                        () -> map).until(hasEntries)).getCause());
        assertEquals(1, map.sizeCalls);
    }

    @Test
    void sizedFactoriesRejectNegativeBoundsAndAllowZero() {
        assertThrows(IllegalArgumentException.class, () -> entryCount(-1));
        assertDoesNotThrow(() -> entryCount(0));
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
                () -> timedMapAwait(source, defaults(), time, time).until(
                        (PreservingCondition<Map<String, String>>) null))
                .getMessage().contains("condition"));
        assertEquals(0, sourceCalls[0]);
    }

    private static void assertUnsatisfied(Evaluation<?> evaluation) {
        assertEquals(UNSATISFIED, evaluation.status());
        assertNull(evaluation.result());
        assertFalse(evaluation.mismatch().isBlank());
    }

    private static Map<Integer, Integer> mapWithSize(int size) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (int value = 0; value < size; value++) {
            map.put(value, value);
        }
        return map;
    }

    private static List<Case> cases() {
        return List.of(new Case(noEntries, 0, 1), new Case(hasEntries, 1, 0),
                new Case(entryCount(2), 2, 1),
                new Case(entryCountNot(2), 1, 2),
                new Case(entryCountGreaterThan(2), 3, 2),
                new Case(entryCountAtLeast(2), 2, 1),
                new Case(entryCountLessThan(2), 1, 2),
                new Case(entryCountAtMost(2), 2, 3));
    }

    private record Case(PreservingCondition<Map<?, ?>> condition, int matchingSize,
            int mismatchingSize) {}
}

package io.github.gromoff97.awium;

import io.github.gromoff97.awium.exception.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ObjectAndOptionalAwaitTest {

    @Test
    void objectTerminalsReturnPreservedAndConditionSelectedResults() {
        Object actual = new Object();
        Object preserved = Awium.await(
                (AwaitSources.Source<Object>) () -> actual)
                .until(AwaitConditions.isNotNull);
        Integer selected = Awium.await(
                (AwaitSources.Source<String>) () -> "value")
                .until(AwaitConditions.<String, Integer>condition("length",
                        value -> Evaluation.satisfied(value.length())));

        assertSame(actual, preserved);
        assertEquals(5, selected);
    }

    @Test
    void optionalPresentUnwrapsTheObservedValue() {
        Object value = new Object();

        Object result = Awium.await(
                (AwaitSources.OptionalSource<Object>) () -> Optional.of(value))
                .until(AwaitConditions.present);

        assertSame(value, result);
    }

    @Test
    void voidAndNullableSelectingTerminalsReturnNullOnSuccess() {
        Void nullValue = Awium.await(
                (AwaitSources.Source<Object>) () -> null)
                .until(AwaitConditions.isNull);
        Void absentValue = Awium.await(
                (AwaitSources.OptionalSource<Object>) Optional::empty)
                .until(AwaitConditions.absent);
        String selectedNull = Awium.await(
                (AwaitSources.Source<String>) () -> "value")
                .until(AwaitConditions.<String, String>passed(value -> null)
                        .because("nullable property"));

        assertNull(nullValue);
        assertNull(absentValue);
        assertNull(selectedNull);
    }

    @Test
    void optionalValueConditionsReturnTheContainedValueThroughUntil() {
        var equalValue = new Object();
        var differentValue = new Object();

        Object equal = Awium.await(
                (AwaitSources.OptionalSource<Object>)
                        () -> Optional.of(equalValue))
                .until(AwaitConditions.hasValueEqualTo(equalValue));
        Object different = Awium.await(
                (AwaitSources.OptionalSource<Object>)
                        () -> Optional.of(differentValue))
                .until(AwaitConditions.hasValueNotEqualTo(equalValue)
                        .because("different value"));

        assertSame(equalValue, equal);
        assertSame(differentValue, different);
    }

    @Test
    void collectionAndMapStructuralTerminalsPreserveConcreteSources() {
        ArrayList<String> list = new ArrayList<>(List.of("value"));
        HashMap<String, Integer> map = new HashMap<>(Map.of("value", 1));
        StructuralCondition structural = AwaitConditions.nonEmpty;

        ArrayList<String> returnedList = Awium.await(
                (AwaitSources.SequencedCollectionSource<String,
                        ArrayList<String>>) () -> list).until(structural);
        HashMap<String, Integer> returnedMap = Awium.await(
                (AwaitSources.MapSource<String, Integer,
                        HashMap<String, Integer>>) () -> map).until(structural);

        assertSame(list, returnedList);
        assertSame(map, returnedMap);
    }

    @Test
    void reusableStageRetainsTheExactSourceAndStartsEachTerminalFresh() {
        CyclingSource source = new CyclingSource();
        FakeTime time = new FakeTime(0);
        AwaitChain<String> chain = new AwaitChain<>(source, WaitConfig.defaults()
                .withEvery(Duration.ofNanos(1)).withUpTo(Duration.ofNanos(10)),
                time, time, new InterruptGuard(), new FailureFactory());
        ObjectUntil<String> stage =
                new ObjectStageAdapters.ObjectAfterUpToStage<>(chain);
        Condition<String, String> evenObservation = AwaitConditions.condition(
                "even observation", value -> Integer.parseInt(value.substring(1)) % 2 == 0
                        ? Evaluation.satisfied(value)
                        : Evaluation.unsatisfied("odd observation"));

        assertSame(source, chain.source());
        assertEquals("v2", stage.until(evenObservation));
        assertEquals("v4", stage.until(evenObservation));
        assertEquals(4, source.calls.get());
    }

    @Test
    void reusableStageStartsFreshAfterControlledAndUncontrolledFailures() {
        FakeTime time = new FakeTime(0);
        AtomicInteger sourceCalls = new AtomicInteger();
        ObjectUntil<String> stage = stage(time, () -> {
            sourceCalls.incrementAndGet();
            return "value";
        });
        Condition<String, String> never = AwaitConditions.condition(
                "never", value -> Evaluation.unsatisfied("not yet"));
        var failure = new IllegalStateException("condition failed");
        Condition<String, String> broken = AwaitConditions.condition(
                "broken", value -> {
                    throw failure;
                });

        assertThrows(AwaitTimeoutException.class, () -> stage.until(never));
        assertSame(failure, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> stage.until(broken)).getCause());
        String result = stage.until(AwaitConditions.condition(
                "ready", Evaluation::satisfied));

        assertEquals("value", result);
        assertEquals(5, sourceCalls.get());
    }

    @Test
    void reusableConditionRetainsOnlyItsOwnStateAcrossFreshWaits() {
        FakeTime time = new FakeTime(0);
        AtomicInteger evaluations = new AtomicInteger();
        ObjectUntil<String> stage = stage(time, () -> "value");
        Condition<String, String> secondEvaluationOnward =
                AwaitConditions.condition("second evaluation onward", value ->
                        evaluations.incrementAndGet() >= 2
                                ? Evaluation.satisfied(value)
                                : Evaluation.unsatisfied("first evaluation"));

        assertEquals("value", stage.until(secondEvaluationOnward));
        assertEquals("value", stage.until(secondEvaluationOnward));
        assertEquals(3, evaluations.get());
    }

    @Test
    void collectionAndMapNullObservationsUseFacadeSpecificMismatch() {
        StructuralCondition structural = AwaitConditions.nonEmpty;

        AwaitTimeoutException collectionFailure = assertThrows(
                AwaitTimeoutException.class,
                () -> nullCollectionStage().until(structural));
        AwaitTimeoutException mapFailure = assertThrows(AwaitTimeoutException.class,
                () -> nullMapStage().until(structural));

        assertTrue(collectionFailure.getMessage().contains("collection was null"));
        assertTrue(mapFailure.getMessage().contains("map was null"));
    }

    private static CollectionUntil<String, List<String>> nullCollectionStage() {
        FakeTime time = new FakeTime(0);
        AwaitChain<List<String>> chain = new AwaitChain<>(() -> null,
                WaitConfig.defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)),
                time, time, new InterruptGuard(), new FailureFactory());
        return new CollectionStageAdapters.CollectionAfterUpToStage<>(chain);
    }

    private static MapUntil<String, String, Map<String, String>> nullMapStage() {
        FakeTime time = new FakeTime(0);
        AwaitChain<Map<String, String>> chain = new AwaitChain<>(() -> null,
                WaitConfig.defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)),
                time, time, new InterruptGuard(), new FailureFactory());
        return new MapStageAdapters.MapAfterUpToStage<>(chain);
    }

    private static <T> ObjectUntil<T> stage(
            FakeTime time, AwaitSources.Source<T> source) {
        AwaitChain<T> chain = new AwaitChain<>(source,
                WaitConfig.defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(3)),
                time, time, new InterruptGuard(), new FailureFactory());
        return new ObjectStageAdapters.ObjectAfterUpToStage<>(chain);
    }

    private static final class CyclingSource implements AwaitSources.Source<String> {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public String get() {
            return "v" + calls.incrementAndGet();
        }
    }
}

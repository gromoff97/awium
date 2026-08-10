package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import io.github.gromoff97.awium.diagnostics.FailureFactory;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.await.StructuralAwait;
import io.github.gromoff97.awium.await.stages.AwaitStage;
import io.github.gromoff97.awium.await.stages.StructuralAwaitStage;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;
import io.github.gromoff97.awium.sources.OptionalSource;
import io.github.gromoff97.awium.sources.Source;

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
        Object preserved = await(
                (Source<Object>) () -> actual)
                .until(isNotNull);
        Integer selected = await(
                (Source<String>) () -> "value")
                .until(ConditionProvider.<String, Integer>condition("length",
                        value -> satisfied(value.length())));

        assertSame(actual, preserved);
        assertEquals(5, selected);
    }

    @Test
    void optionalPresentUnwrapsTheObservedValue() {
        Object value = new Object();

        Object result = await(
                (OptionalSource<Object>) () -> Optional.of(value))
                .until(present);

        assertSame(value, result);
    }

    @Test
    void voidAndNullableSelectingTerminalsReturnNullOnSuccess() {
        Void nullValue = await(
                (Source<Object>) () -> null)
                .until(isNull);
        Void absentValue = await(
                (OptionalSource<Object>) Optional::empty)
                .until(absent);
        String selectedNull = await(
                (Source<String>) () -> "value")
                .until(ConditionProvider.<String, String>passed(value -> null)
                        .because("nullable property"));

        assertNull(nullValue);
        assertNull(absentValue);
        assertNull(selectedNull);
    }

    @Test
    void optionalValueConditionsReturnTheContainedValueThroughUntil() {
        var equalValue = new Object();
        var differentValue = new Object();

        Object equal = await(
                (OptionalSource<Object>)
                        () -> Optional.of(equalValue))
                .until(hasValueEqualTo(equalValue));
        Object different = await(
                (OptionalSource<Object>)
                        () -> Optional.of(differentValue))
                .until(hasValueNotEqualTo(equalValue)
                        .because("different value"));

        assertSame(equalValue, equal);
        assertSame(differentValue, different);
    }

    @Test
    void collectionAndMapStructuralTerminalsPreserveConcreteSources() {
        ArrayList<String> list = new ArrayList<>(List.of("value"));
        HashMap<String, Integer> map = new HashMap<>(Map.of("value", 1));
        StructuralCondition structural = nonEmpty;

        ArrayList<String> returnedList = await(
                (CollectionSource<ArrayList<String>>) () -> list)
                .until(structural);
        HashMap<String, Integer> returnedMap = await(
                (MapSource<HashMap<String, Integer>>) () -> map)
                .until(structural);

        assertSame(list, returnedList);
        assertSame(map, returnedMap);
    }

    @Test
    void reusableStageRetainsTheExactSourceAndStartsEachTerminalFresh() {
        CyclingSource source = new CyclingSource();
        FakeTime time = new FakeTime(0);
        AwaitStage<String> stage = new AwaitStage<>(source,
                defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(10)),
                time, time, new FailureFactory());
        Condition<String, String> evenObservation = condition(
                "even observation", value -> Integer.parseInt(value.substring(1)) % 2 == 0
                        ? satisfied(value)
                        : unsatisfied("odd observation"));

        assertEquals("v2", stage.until(evenObservation));
        assertEquals("v4", stage.until(evenObservation));
        assertEquals(4, source.calls.get());
    }

    @Test
    void reusableStageStartsFreshAfterControlledAndUncontrolledFailures() {
        FakeTime time = new FakeTime(0);
        AtomicInteger sourceCalls = new AtomicInteger();
        Await.Until<String> stage = stage(time, () -> {
            sourceCalls.incrementAndGet();
            return "value";
        });
        Condition<String, String> never = condition(
                "never", value -> unsatisfied("not yet"));
        var failure = new IllegalStateException("condition failed");
        Condition<String, String> broken = condition(
                "broken", value -> {
                    throw failure;
                });

        assertThrows(AwaitTimeoutException.class, () -> stage.until(never));
        assertSame(failure, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> stage.until(broken)).getCause());
        String result = stage.until(condition(
                "ready", Evaluation::satisfied));

        assertEquals("value", result);
        assertEquals(5, sourceCalls.get());
    }

    @Test
    void reusableConditionRetainsOnlyItsOwnStateAcrossFreshWaits() {
        FakeTime time = new FakeTime(0);
        AtomicInteger evaluations = new AtomicInteger();
        Await.Until<String> stage = stage(time, () -> "value");
        Condition<String, String> secondEvaluationOnward =
                condition("second evaluation onward", value ->
                        evaluations.incrementAndGet() >= 2
                                ? satisfied(value)
                                : unsatisfied("first evaluation"));

        assertEquals("value", stage.until(secondEvaluationOnward));
        assertEquals("value", stage.until(secondEvaluationOnward));
        assertEquals(3, evaluations.get());
    }

    @Test
    void collectionAndMapNullObservationsUseFacadeSpecificMismatch() {
        StructuralCondition structural = nonEmpty;

        AwaitTimeoutException collectionFailure = assertThrows(
                AwaitTimeoutException.class,
                () -> nullCollectionStage().until(structural));
        AwaitTimeoutException mapFailure = assertThrows(AwaitTimeoutException.class,
                () -> nullMapStage().until(structural));

        assertTrue(collectionFailure.getMessage().contains("collection was null"));
        assertTrue(mapFailure.getMessage().contains("map was null"));
    }

    private static StructuralAwait.Until<List<String>> nullCollectionStage() {
        FakeTime time = new FakeTime(0);
        return new StructuralAwaitStage<>(
                (CollectionSource<List<String>>) () -> null,
                java.util.Collection::size,
                defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)),
                time, time, new FailureFactory());
    }

    private static StructuralAwait.Until<Map<String, String>> nullMapStage() {
        FakeTime time = new FakeTime(0);
        return new StructuralAwaitStage<>(
                (MapSource<Map<String, String>>) () -> null,
                Map::size,
                defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)),
                time, time, new FailureFactory());
    }

    private static <T> Await.Until<T> stage(
            FakeTime time, Source<T> source) {
        return new AwaitStage<>(source,
                defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(3)),
                time, time, new FailureFactory());
    }

    private static final class CyclingSource implements Source<String> {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public String get() {
            return "v" + calls.incrementAndGet();
        }
    }
}

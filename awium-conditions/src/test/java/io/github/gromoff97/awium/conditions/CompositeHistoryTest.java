package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.ExecutionStep;
import io.github.gromoff97.awium.execution.WaitCompletion;
import io.github.gromoff97.awium.execution.WaitTiming;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.attempts;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.observation;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.probe;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.rejection;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.condition;
import static io.github.gromoff97.awium.conditions.Conditions.equalTo;
import static io.github.gromoff97.awium.conditions.Conditions.instanceOf;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.conditions.OptionalConditions.present;
import static io.github.gromoff97.awium.execution.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.time.Duration.ofNanos;
import static java.util.Collections.nCopies;
import static java.util.Map.Entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CompositeHistoryTest {

    @Test
    void capturedRetainsEveryObservationTransitionWithinAnUnsuccessfulStage() {
        var observations = List.of(nCopies(4, 0), nCopies(2, 10), nCopies(6, 20), nCopies(12, 30), nCopies(6, 40))
                .stream().flatMap(List::stream).toList();
        var run = ConditionExecution.<Integer, List<Integer>>start(captured(equalTo(100), equalTo(200)), true);
        for (int actual : observations) {
            var rejected = assertInstanceOf(ConditionResult.Unsatisfied.class, probe(run, actual));
            run.record(new UnsatisfiedObservation<>(actual, rejected.mismatch(), rejected.cause()));
        }
        var result = run.finish(new WaitCompletion.Timeout(new ExecutionStep<>(false, ACQUISITION, 0,
                new UnsatisfiedObservation<>(40, "not equal", null))), mock(WaitTiming.class));
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(4L, 6L, 12L, 24L, 30L);
        assertThat(attempts(result)).extracting(ConditionAssertions::observation).containsExactly(0, 10, 20, 30, 40);
        assertTrue(result.conditions().getLast().attempts().isEmpty());
    }

    @Test
    void capturedKeepsEverySuccessfulObservationAndTheIdentityOfEachStage() {
        int[] calls = {0};
        var stage = condition("same description", actual -> satisfied(actual));
        var result = tryAwait(() -> ++calls[0])
                .every(ofNanos(1))
                .until(captured(stage, stage, stage));

        assertEquals(List.of(1, 2, 3), result.orThrow());
        assertEquals(3, attempts(result).size());
        assertThat(result.conditions()).extracting(history -> history.attempts().lastKey()).containsExactly(1L, 2L, 3L);
        result.conditions().forEach(history -> assertSame(stage, history.condition()));
    }

    @Test
    void historyRetainsSourceTypesAlongsideEveryContainerSelection() {
        var optional = tryAwait(() -> Optional.of("ready"))
                .every(ofNanos(1))
                .until(present);
        var collection = tryAwait(() -> List.of("ready"))
                .persisting(ofNanos(0))
                .until(singleElement);
        var map = tryAwait(() -> Map.of("user", "ready"))
                .every(ofNanos(1)).persisting(ofNanos(0));
        var value = map.until(valueFor("user"));
        var entry = map.until(singleEntry);

        String selectedOptional = optional.orThrow();
        Optional<String> observedOptional = observation(attempts(optional).getFirst());
        String selectedElement = collection.orThrow();
        List<String> observedCollection = observation(attempts(collection).getFirst());
        String selectedValue = value.orThrow();
        Map<String, String> observedMap = observation(attempts(value).getFirst());
        Entry<String, String> selectedEntry = entry.orThrow();
        assertEquals("ready", selectedOptional);
        assertEquals(Optional.of("ready"), observedOptional);
        assertEquals("ready", selectedElement);
        assertEquals(List.of("ready"), observedCollection);
        assertEquals("ready", selectedValue);
        assertEquals(Map.of("user", "ready"), observedMap);
        assertEquals(Map.entry("user", "ready"), selectedEntry);
    }

    @Test
    void nestedReasonCopiesKeepTheSameMismatchAndTheirExplanation() {
        var original = singleElement(equalTo("ready"));
        var explained = original.because("one ready element");
        assertEquals(rejection(original, List.of()), rejection(explained, List.of()));
        assertEquals("one ready element", explained.explanation());
    }

    @Test
    void repeatedSelectorsKeepTheirNestingLevelWithoutSplittingSameLevelRepetitions() {
        var emptyOuter = List.<List<String>>of();
        var emptyInner = List.of(List.<String>of());
        var ready = List.of(List.of("ready"));
        var observations = List.of(emptyOuter, emptyOuter, emptyInner, emptyInner, ready).iterator();
        var check = singleElement(
                singleElement(instanceOf(String.class)));
        var result = tryAwait(observations::next).every(ofNanos(1)).until(check);

        String selected = result.orThrow();
        assertEquals("ready", selected);
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(2L, 4L, 5L);
        assertThat(attempts(result).subList(0, 2))
                .extracting(attempt -> assertInstanceOf(UnsatisfiedObservation.class, attempt).mismatch())
                .containsExactly("collection size was 0", "single element: collection size was 0");
        assertSame(check, result.conditions().getFirst().condition());
    }

    @Test
    void nestedSelectionScopeAccumulatesAcrossDifferentContainerKinds() {
        var observations = List.of(Optional.<List<List<String>>>empty(),
                Optional.of(List.<List<String>>of()), Optional.of(List.of(List.<String>of())),
                Optional.of(List.of(List.of("ready")))).iterator();
        var result = tryAwait(observations::next).every(ofNanos(1))
                .until(hasValue(singleElement(
                        singleElement(instanceOf(String.class)))));

        assertEquals("ready", result.orThrow());
        assertEquals(4, attempts(result).size());
        assertThat(attempts(result).subList(0, 3))
                .extracting(attempt -> assertInstanceOf(UnsatisfiedObservation.class, attempt).mismatch())
                .containsExactly("optional was empty", "optional value: collection size was 0",
                        "optional value: single element: collection size was 0");
    }
}

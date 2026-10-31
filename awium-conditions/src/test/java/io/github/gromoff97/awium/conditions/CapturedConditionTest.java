package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.conditions.Conditions;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.result.AwaitResult;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.conditions.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.conditions.CollectionConditions.hasSize;
import static io.github.gromoff97.awium.conditions.CollectionConditions.lastElement;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.assertUnsatisfied;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.attempts;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.failure;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.observation;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.probe;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.uncontrolled;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.condition;
import static io.github.gromoff97.awium.conditions.Conditions.equalTo;
import static io.github.gromoff97.awium.conditions.Conditions.exactlyInstanceOf;
import static io.github.gromoff97.awium.conditions.Conditions.instanceOf;
import static io.github.gromoff97.awium.conditions.Conditions.isNotNull;
import static io.github.gromoff97.awium.conditions.Conditions.isNull;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.MapConditions.entryFor;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.present;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.*;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.Attempt.Operation.SOURCE;
import static io.github.gromoff97.awium.result.Attempt.Operation;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapturedConditionTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void mixedChecksAndSelectionsCaptureEachCompleteObservation() {
        var first = List.of(new Payment(Status.CREATED, "first"));
        var second = List.of(new Payment(Status.PENDING, "second"));
        var third = List.of(new Payment(Status.FINISHED, "third"));
        var observations = List.of(first, second, third);
        int[] reads = {0};
        var waiting = await(() -> observations.get(reads[0]++ % observations.size()))
                .every(ofNanos(1));

        List<List<Payment>> values = waiting.until(captured(
                hasSize(1),
                singleElement,
                singleElement(instanceOf(Payment.class))));
        AwaitResult<List<Payment>, List<List<Payment>>> result = tryAwait(() -> observations.get(reads[0]++ % observations.size()))
                .every(ofNanos(1)).until(captured(
                hasSize(1),
                singleElement,
                singleElement(instanceOf(Payment.class))));

        assertSame(first, values.getFirst());
        assertSame(second, values.get(1));
        assertSame(third, values.getLast());
        List<List<Payment>> captured = result.orThrow();
        assertEquals(values, captured);
        assertSame(captured, result.orThrow());
        assertThat(attempts(result)).hasOnlyElementsOfType(SatisfiedObservation.class)
                .extracting(ConditionAssertions::observation).containsExactlyElementsOf(observations);
        assertEquals(6, reads[0]);
    }

    @Test
    void differentDeclaredStageResultsDoNotChangeTheCaptureType() {
        Object text = new String("ready");
        Object number = Integer.valueOf(1234);
        Object[] observations = {false, text, false, number};
        int[] reads = {0};
        var waiting = await(() -> observations[reads[0]++ % observations.length])
                .every(ofNanos(1));

        List<Object> values = waiting.until(captured(
                instanceOf(String.class), instanceOf(Integer.class)));
        AwaitResult<Object, List<Object>> result = tryAwait(() -> observations[reads[0]++ % observations.length])
                .every(ofNanos(1)).until(captured(
                instanceOf(String.class), instanceOf(Integer.class)));

        assertSame(text, values.getFirst());
        assertSame(number, values.getLast());
        assertEquals(values, result.orThrow());
        assertEquals(4, attempts(result).size());
        assertEquals(8, reads[0]);
    }

    @Test
    void reusableCaptureKeepsTheCompleteSourceTypeWithoutExplicitArguments() {
        var payment = new Payment(Status.FINISHED, "paid");
        var payments = new ArrayList<>(List.of(payment));
        var checks = captured(isNotNull, isNotNull).because("observations");
        var selections = captured(singleElement, singleElement).because("elements");

        var sources = await(() -> payments).every(ofNanos(1)).until(checks);
        ArrayList<Payment> original = sources.getFirst();
        assertSame(payments, original);
        assertEquals("paid", original.getFirst().detail());

        var selected = await(() -> payments).every(ofNanos(1)).until(selections);
        assertEquals("paid", selected.getFirst().getFirst().detail());
        var strings = await(() -> List.of("ready")).every(ofNanos(1)).until(selections);
        assertEquals(5, strings.getFirst().getFirst().length());

        var optionals = await(() -> Optional.of(payment)).every(ofNanos(1))
                .until(captured(present, present).because("present values"));
        assertEquals("paid", optionals.getFirst().orElseThrow().detail());
        var mapped = await(() -> Map.of("id", payment)).every(ofNanos(1))
                .until(captured(valueFor("id"), valueFor("id")).because("map values"));
        assertEquals("paid", mapped.getFirst().get("id").detail());
        var entries = await(() -> Map.of("id", payment)).every(ofNanos(1))
                .until(captured(entryFor("id"), singleEntry).because("entries"));
        assertEquals("paid", entries.getFirst().get("id").detail());
        var narrowed = await(() -> (Object) payment).every(ofNanos(1))
                .until(captured(instanceOf(Payment.class), instanceOf(Payment.class)).because("payments"));
        assertSame(payment, narrowed.getFirst());

        var single = await(() -> Optional.of(payment)).until(captured(present));
        assertEquals(1, single.size());
        assertSame(payment, single.getFirst().orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("earlyFailures")
    void failureBeforeTheFirstCheckIdentifiesItsStageWithoutInventingAnEvaluation(
            Runnable beforeAwait, int expectedReads, Operation operation, Reason reason, BiConsumer<Throwable, Throwable> assertCause) {
        var first = equalTo("ready");
        var sequence = captured(first, equalTo("done")).because("lifecycle");
        var cause = new IllegalStateException("source unavailable");
        int[] reads = {0};
        Callable<String> source = () -> {
            reads[0]++;
            throw cause;
        };
        beforeAwait.run();
        var result = tryAwait(source).until(sequence);
        var failure = failure(result);

        assertEquals(1, attempts(result).size());
        assertSame(first, result.conditions().getFirst().condition());
        assertTrue(result.conditions().getLast().attempts().isEmpty());
        assertEquals(expectedReads, reads[0]);
        uncontrolled(operation, attempts(result).getFirst());
        assertEquals(reason, failure.reason());
        assertCause.accept(cause, failure.cause());
        assertThat(failure.message()).contains("Because: lifecycle", "stage 1/2");
    }

    private static List<Arguments> earlyFailures() {
        return List.of(
                argumentSet("source failure", (Runnable) () -> {}, 1, SOURCE, SOURCE_FAILED,
                        (BiConsumer<Throwable, Throwable>) Assertions::assertSame),
                argumentSet("already interrupted", (Runnable) () -> currentThread().interrupt(), 0, WAITING, INTERRUPTED,
                        (BiConsumer<Throwable, Throwable>) (expected, actual) -> assertInstanceOf(InterruptedException.class, actual)));
    }

    @Test
    void narrowingSequencesCaptureTheSourceAndRetryInBothTerminals() {
        var first = new String("first");
        var second = new String("second");
        Object[] observations = {42, first, 43L, second};
        int[] reads = {0};
        var waiting = await(() -> observations[reads[0]++ % observations.length])
                .every(ofNanos(1));
        var start = instanceOf(String.class);
        var finish = exactlyInstanceOf(String.class);

        var values = waiting.until(captured(start, finish));
        AwaitResult<Object, List<Object>> attempt = tryAwait(() -> observations[reads[0]++ % observations.length])
                .every(ofNanos(1)).until(captured(start, finish));

        assertSame(first, values.getFirst());
        assertSame(second, values.getLast());
        assertEquals(values, attempt.orThrow());
        assertEquals(4, attempts(attempt).size());
        assertInstanceOf(SatisfiedObservation.class, attempts(attempt).get(1));
        assertSame(first, observation(attempts(attempt).get(1)));
        assertEquals(8, reads[0]);
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    @Test
    void nestedNarrowingSequencesRetainTheirOriginalContainers() {
        Integer first = 1000;
        Integer second = 2000;
        List<List<Number>> observations = List.of(List.of(first), List.of(second));
        int[] reads = {0};
        var waiting = await(() -> observations.get(reads[0]++ % observations.size()))
                .every(ofNanos(1));

        var values = waiting.until(captured(singleElement(instanceOf(Integer.class)),
                singleElement(exactlyInstanceOf(Integer.class))));
        AwaitResult<List<Number>, List<List<Number>>> attempt = tryAwait(() -> observations.get(reads[0]++ % observations.size()))
                .every(ofNanos(1)).until(captured(
                singleElement(instanceOf(Integer.class)),
                singleElement(exactlyInstanceOf(Integer.class))));

        assertEquals(1000, values.getFirst().getFirst().intValue());
        assertSame(observations.getFirst(), values.getFirst());
        assertSame(observations.getLast(), values.getLast());
        assertEquals(values, attempt.orThrow());
        assertEquals(2, attempts(attempt).size());
        assertEquals(4, reads[0]);
    }

    @Test
    void mapValueSequenceKeepsNullableObservationsAndRefreshesOnlyTheLastStage() {
        var first = java.util.Collections.<String, Number>singletonMap("first", null);
        Map<String, Number> acquired = Map.of("last", new BigDecimal("2.00"));
        Map<String, Number> refreshed = Map.of("last", new BigDecimal("3.00"));
        var run = ConditionExecution.<Map<String, Number>, List<Map<String, Number>>>start(
                captured(valueFor("first"), valueFor("last", isNotNull)), false);

        probe(run, first);
        probe(run, acquired);
        var initial = run.result();
        probe(run, refreshed);

        assertTrue(run.complete());
        assertSame(first, run.result().getFirst());
        assertSame(refreshed, run.result().getLast());
        assertSame(acquired, initial.getLast());
        assertThrows(UnsupportedOperationException.class, initial::clear);
    }

    @Test
    void expectedSequencesCaptureTheOriginalObservationsInBothTerminals() {
        Number first = new BigDecimal("1.00");
        Number second = new BigInteger("2");
        Number third = 1024L;
        Number[] observations = {first, second, third, first, second, third};
        int[] reads = {0};
        var waiting = await(() -> observations[reads[0]++]).every(ofNanos(1));
        var firstStage = equalTo(new BigDecimal("1.00"));
        var secondStage = equalTo(new BigInteger("2"));
        var finalStage = equalTo(1024L);

        var sequence = captured(firstStage, secondStage, finalStage).because("numeric states");
        List<Number> values = waiting.until(sequence);
        AwaitResult<Number, List<Number>> captured = tryAwait(() -> observations[reads[0]++]).every(ofNanos(1)).until(sequence);

        assertSame(first, values.get(0));
        assertSame(second, values.get(1));
        assertSame(third, values.get(2));
        List<Number> results = captured.orThrow();
        assertSame(first, results.get(0));
        assertSame(second, results.get(1));
        assertSame(third, results.get(2));
        assertEquals(3, attempts(captured).size());
        assertEquals(6, reads[0]);
    }

    @Test
    void selectedSequenceRefreshesOnlyItsLastObservation() {
        var first = List.of("first", "ignored");
        var acquired = List.of("ignored", "acquired");
        var refreshed = List.of("ignored", "refreshed");
        var run = ConditionExecution.<List<String>, List<List<String>>>start(
                captured(firstElement, lastElement), false);
        probe(run, first);
        probe(run, acquired);
        probe(run, refreshed);
        assertEquals(List.of(first, refreshed), run.result());
        assertSame(first, run.result().getFirst());
        assertSame(refreshed, run.result().getLast());
    }

    @Test
    void historyRecordsTheCheckedStageAndItsActualResult() {
        var source = List.of("created", "created", "processing", "completed").iterator();
        var first = equalTo("created");
        var second = equalTo("processing");
        var third = equalTo("completed");
        var sequence = captured(first, second, third).because("lifecycle");
        var result = tryAwait(source::next).every(ofNanos(1)).until(sequence);

        assertThat(result.conditions()).extracting(history -> history.condition())
                .containsExactly(first, second, third);
        assertThat(result.conditions()).extracting(history -> List.copyOf(history.attempts().keySet()))
                .containsExactly(List.of(1L), List.of(2L, 3L), List.of(4L));
        assertThat(attempts(result)).hasExactlyElementsOfTypes(SatisfiedObservation.class, UnsatisfiedObservation.class,
                        SatisfiedObservation.class, SatisfiedObservation.class)
                .allSatisfy(attempt -> assertThat(observation(attempt)).isNotEmpty());

        assertEquals(List.of("created", "processing", "completed"),
                result.orThrow());
    }

    @ParameterizedTest
    @CsvSource({"1,false", "1,true", "2,false", "2,true"})
    void interruptedStageIsNotCapturedAfterANormalCallbackReturn(int stage, boolean matching) {
        var first = Conditions.<String>matches(value -> {
            if (stage == 1) currentThread().interrupt();
            return stage == 2 || matching;
        });
        var second = Conditions.<String>matches(value -> {
            currentThread().interrupt();
            return matching;
        });
        var run = ConditionExecution.<String, List<String>>start(captured(first, second), false);
        if (stage == 2) probe(run, "ready");
        run.beginAttempt();
        assertThrows(InterruptedException.class, () -> run.probe("ready"));
        assertEquals(stage - 1, run.result().size());
        assertFalse(run.complete());
        assertTrue(currentThread().isInterrupted());
    }

    private record Payment(Status status, String detail) {}

    private enum Status { CREATED, PENDING, FINISHED }

    @Test
    void capturesExactlyOnePredicateStagePerProbe() {
        int[] calls = {0};
        var stage = matches((Integer value) -> { calls[0]++; return value == 1; });
        var run = ConditionExecution.<Integer, List<Integer>>start(captured(stage, stage, stage), false);
        for (int count = 1; count <= 3; count++) {
            assertInstanceOf(io.github.gromoff97.awium.result.ConditionResult.Satisfied.class, probe(run, 1));
            assertEquals(count, calls[0]);
            assertEquals(count, run.result().size());
            assertEquals(count == 3, run.complete());
        }
        assertEquals(List.of(1, 1, 1), run.result());
    }

    @Test
    void capturesAChangingSourceInStrictOrder() {
        var created = new Payment(Status.CREATED, "accepted");
        var pending = new Payment(Status.PENDING, "settling");
        var finished = new Payment(Status.FINISHED, "paid");
        Payment[] observations = {created, pending, finished};
        int[] next = {0};

        List<Payment> result = await(() -> observations[next[0]++]).every(ofNanos(1)).until(captured(matches(payment -> payment.status() == Status.CREATED),
                matches(payment -> payment.status() == Status.PENDING),
                matches(payment -> payment.status() == Status.FINISHED)));

        assertEquals(List.of(created, pending, finished), result);
    }

    @Test
    void recheckingACompleteSequenceRefreshesOnlyTheFinalStage() {
        int[] earlyCalls = {0};
        int[] finalCalls = {0};
        var run = ConditionExecution.<String, List<String>>start(captured(
                matches((String value) -> { earlyCalls[0]++; return value.equals("early"); }),
                matches((String value) -> { finalCalls[0]++; return !value.equals("early"); })), false);
        for (var actual : List.of("early", "acquired", "refreshed", "latest")) probe(run, actual);
        assertTrue(run.complete());
        assertEquals(List.of("early", "latest"), run.result());
        assertEquals(1, earlyCalls[0]);
        assertEquals(3, finalCalls[0]);
    }

    @Test
    void recheckingTheFinalStageRemovesAnObservationThatNoLongerMatches() {
        var run = ConditionExecution.<String, List<String>>start(captured(equalTo("early"), equalTo("final")), false);
        probe(run, "early");
        probe(run, "final");
        assertTrue(run.complete());
        assertUnsatisfied(probe(run, "rejected"));
        assertFalse(run.complete());
        assertEquals(List.of("early"), run.result());
        probe(run, "final");
        assertTrue(run.complete());
        assertEquals(List.of("early", "final"), run.result());
    }

    @Test
    void doesNotSkipAMissedIntermediateStage() {
        var created = new Payment(Status.CREATED, "accepted");
        var finished = new Payment(Status.FINISHED, "paid");
        var run = ConditionExecution.<Payment, List<Payment>>start(captured(
                matches((Payment value) -> value.status() == Status.CREATED),
                matches((Payment value) -> value.status() == Status.PENDING),
                matches((Payment value) -> value.status() == Status.FINISHED)), false);
        probe(run, created);
        assertUnsatisfied(probe(run, finished));
        assertUnsatisfied(probe(run, finished));
        assertFalse(run.complete());
        assertEquals(List.of(created), run.result());
    }

    @Test
    void capturesProjectedObservations() {
        String[] observations = {"a", "bb"};
        int[] next = {0};

        List<Integer> result = await(() -> observations[next[0]++].length()).every(ofNanos(1)).until(captured(equalTo(1), equalTo(2)));

        assertEquals(List.of(1, 2), result);
    }

    @Test
    void preservingSequenceCapturesEveryStage() {
        String[] observations = {"alpha", "omega"};
        int[] next = {0};

        List<String> result = await(() -> observations[next[0]++]).every(ofNanos(1)).until(captured(matches((String value) -> value.startsWith("a")),
                matches((String value) -> value.endsWith("a"))));

        assertEquals(List.of("alpha", "omega"), result);
    }

    @Test
    void validatesEveryStageBeforeEvaluation() {
        PreservingCondition<String> preserving = matches(value -> true);

        assertThrows(NullPointerException.class,
                () -> captured((PreservingCondition<String>) null, preserving));
        assertThrows(NullPointerException.class,
                () -> captured(preserving, (PreservingCondition<String>) null));
        assertThrows(NullPointerException.class,
                () -> captured(preserving, preserving, (PreservingCondition<String>) null));
        assertThrows(NullPointerException.class,
                () -> captured((PreservingCondition<String>[]) null));
    }

    @Test
    void returnsAnImmutableCapturedCopyThatAllowsNull() {
        List<String> result = await((Callable<String>) () -> null).every(ofNanos(1)).until(captured(condition("first null", ignored -> satisfied(ignored)),
                condition("second null", ignored -> satisfied(ignored))));

        assertEquals(2, result.size());
        assertNull(result.get(0));
        assertNull(result.get(1));
        assertThrows(UnsupportedOperationException.class, () -> result.add("changed"));
    }

    @Test
    void captureSnapshotsTheArrayBeforePollingAndKeepsTheResultType() {
        @SuppressWarnings("unchecked")
        PreservingCondition<Integer>[] stages = (PreservingCondition<Integer>[]) new PreservingCondition<?>[]{
                matches((Integer value) -> value == 1),
                matches((Integer value) -> value == 2)};
        int[] calls = {0};
        var waiting = await(() -> {
            stages[0] = matches(value -> false);
            stages[1] = matches(value -> false);
            return ++calls[0];
        }).every(ofNanos(1));

        var sequence = captured(stages);
        java.util.Arrays.fill(stages, matches((Integer value) -> false));
        List<Integer> values = waiting.until(sequence);
        assertEquals(List.of(1, 2), values);

        calls[0] = 0;
        AwaitResult<Integer, List<Integer>> captured = tryAwait(() -> {
            stages[0] = matches(value -> false);
            stages[1] = matches(value -> false);
            return ++calls[0];
        }).every(ofNanos(1)).until(sequence);
        assertEquals(values, captured.orThrow());
        assertEquals(2, attempts(captured).size());
        assertThrows(IllegalArgumentException.class, () -> captured(java.util.Arrays.copyOf(stages, 0)));
        assertEquals(2, calls[0]);

        calls[0] = 0;
        stages[0] = matches(value -> value == 1);
        var singleton = waiting.until(captured(java.util.Arrays.copyOf(stages, 1)));
        assertEquals(1, singleton.size());
        assertEquals(1, singleton.getFirst().intValue());
    }

    @Test
    void selectedSequenceCapturesOriginalObservations() {
        List<Optional<String>> observations = List.of(Optional.of("first"),
                Optional.of("second"));
        int[] next = {0};

        List<Optional<String>> result = await(() -> observations.get(next[0]++))
                .every(ofNanos(1))
                .until(captured(present, present));

        assertEquals(observations, result);
    }

    @Test
    void sequenceStartsFreshAfterCompleteAndIncompleteExecutions() {
        var stages = captured(equalTo("created"), equalTo("finished"));
        var completed = ConditionExecution.<String, List<String>>start(stages, false);
        probe(completed, "created");
        probe(completed, "finished");
        var incomplete = ConditionExecution.<String, List<String>>start(stages, false);
        probe(incomplete, "created");
        assertUnsatisfied(probe(incomplete, "pending"));
        var next = ConditionExecution.<String, List<String>>start(stages, false);
        assertUnsatisfied(probe(next, "finished"));
        assertTrue(next.result().isEmpty());
        probe(next, "created");
        probe(next, "finished");
        assertEquals(List.of("created", "finished"), next.result());
        assertEquals(next.result(), completed.result());
        assertEquals(List.of("created"), incomplete.result());
    }

    @Test
    void sourceFailureHasNoObservationAndStopsAfterTheCompletedStage() throws Exception {
        var cause = new IllegalStateException("source failed");
        Callable<String> source = mock();
        when(source.call()).thenReturn(null).thenAnswer(ignored -> { throw cause; });
        var result = tryAwait(source).every(ofNanos(1))
                .until(captured(isNull, isNull, isNull));

        verify(source, times(2)).call();
        assertInstanceOf(SatisfiedObservation.class, result.conditions().getFirst().attempts().firstEntry().getValue());
        switch (result.conditions().get(1).attempts().lastEntry().getValue()) {
            case FailedBeforeObservation(var operation, var error) -> {
                assertSame(cause, error);
                assertEquals(SOURCE, operation);
            }
            default -> fail("expected a failure without an observation");
        }
        assertTrue(result.conditions().getLast().attempts().isEmpty());
        assertThat(result.conditions().get(1).toString()).contains("Observed: unavailable");
    }

    @Test
    void returnedNullRemainsAnObservationWhenTheConditionThrows() {
        var cause = new IllegalStateException("check failed");
        int[] reads = {0};
        Callable<String> source = () -> { reads[0]++; return null; };
        var result = tryAwait(source).every(ofNanos(1))
                .until(captured(isNull, condition("broken", actual -> { throw cause; })));

        assertEquals(2, reads[0]);
        assertEquals(2L, result.conditions().getLast().attempts().lastKey());
        switch (result.conditions().getLast().attempts().lastEntry().getValue()) {
            case FailedAfterObservation(var observed, var operation, var error, var location) -> {
                String actual = observed;
                assertNull(actual);
                assertSame(cause, error);
                assertEquals(CONDITION, operation);
                assertNull(location);
            }
            default -> fail("expected a failure with the returned null observation");
        }
        assertSame(cause, failure(result).cause());
        assertThat(result.conditions().getLast().toString()).contains("Observed now: null");
    }
}

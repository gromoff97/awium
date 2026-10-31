package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.uncontrolled;
import static io.github.gromoff97.awium.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.CollectionConditions.hasSize;
import static io.github.gromoff97.awium.CollectionConditions.lastElement;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.exactlyInstanceOf;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.entryFor;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.*;
import static io.github.gromoff97.awium.result.Attempt.Operation;
import static io.github.gromoff97.awium.result.Attempt.Satisfied;
import static io.github.gromoff97.awium.result.Attempt.Unsatisfied;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapturedConditionTest {

    private final FakeTime time = new FakeTime(0);

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
                .usingTime(time, time).every(ofNanos(1));

        List<List<Payment>> values = waiting.until(captured(
                hasSize(1),
                singleElement,
                singleElement(instanceOf(Payment.class))));
        AwaitResult<List<Payment>, List<List<Payment>>> result = diagnostic(waiting).until(captured(
                hasSize(1),
                singleElement,
                singleElement(instanceOf(Payment.class))));

        assertSame(first, values.getFirst());
        assertSame(second, values.get(1));
        assertSame(third, values.getLast());
        List<List<Payment>> captured = result.orThrow();
        assertEquals(values, captured);
        assertSame(captured, result.orThrow());
        assertThat(attempts(result)).hasOnlyElementsOfType(Satisfied.class)
                .extracting(AwaitTestAccess::observation).containsExactlyElementsOf(observations);
        assertEquals(6, reads[0]);
    }

    @Test
    void differentDeclaredStageResultsDoNotChangeTheCaptureType() {
        Object text = new String("ready");
        Object number = Integer.valueOf(1234);
        Object[] observations = {false, text, false, number};
        int[] reads = {0};
        var waiting = await(() -> observations[reads[0]++ % observations.length])
                .usingTime(time, time).every(ofNanos(1));

        List<Object> values = waiting.until(captured(
                instanceOf(String.class), instanceOf(Integer.class)));
        AwaitResult<Object, List<Object>> result = diagnostic(waiting).until(captured(
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

        var sources = await(() -> payments).usingTime(time, time).every(ofNanos(1)).until(checks);
        ArrayList<Payment> original = sources.getFirst();
        assertSame(payments, original);
        assertEquals("paid", original.getFirst().detail());

        var selected = await(() -> payments).usingTime(time, time).every(ofNanos(1)).until(selections);
        assertEquals("paid", selected.getFirst().getFirst().detail());
        var strings = await(() -> List.of("ready")).usingTime(time, time).every(ofNanos(1)).until(selections);
        assertEquals(5, strings.getFirst().getFirst().length());

        var optionals = await(() -> Optional.of(payment)).usingTime(time, time).every(ofNanos(1))
                .until(captured(present, present).because("present values"));
        assertEquals("paid", optionals.getFirst().orElseThrow().detail());
        var mapped = await(() -> Map.of("id", payment)).usingTime(time, time).every(ofNanos(1))
                .until(captured(valueFor("id"), valueFor("id")).because("map values"));
        assertEquals("paid", mapped.getFirst().get("id").detail());
        var entries = await(() -> Map.of("id", payment)).usingTime(time, time).every(ofNanos(1))
                .until(captured(entryFor("id"), singleEntry).because("entries"));
        assertEquals("paid", entries.getFirst().get("id").detail());
        var narrowed = await(() -> (Object) payment).usingTime(time, time).every(ofNanos(1))
                .until(captured(instanceOf(Payment.class), instanceOf(Payment.class)).because("payments"));
        assertSame(payment, narrowed.getFirst());

        var single = await(() -> Optional.of(payment)).usingTime(time, time).until(captured(present));
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

    @ParameterizedTest
    @MethodSource("progressionFailures")
    void sourceAndWaitingFailuresRetainProgressInBothPhasesAndTerminals(
            int completedStages, Reason reason, String failureAt, BinaryOperator<Supplier<AwaitFailure>> terminal) {
        var cause = new IllegalStateException("after a completed stage");
        Consumer<String> boundary = mock();
        doAnswer(ignored -> { throw cause; }).when(boundary).accept(failureAt);
        var waiting = progressing(boundary);
        var first = equalTo("ready");
        var second = equalTo("done");
        var failure = terminal.apply(
                () -> assertThrows(AwaitExecutionException.class, () -> waiting.until(captured(first, second))).failure(),
                () -> {
                    var result = diagnostic(waiting).until(captured(first, second));
                    assertInstanceOf(FailedBeforeObservation.class, attempts(result).getLast());
                    assertSame(second, result.conditions().getLast().condition());
                    assertSame(first, result.conditions().getFirst().condition());
                    return failure(result);
                }).get();

        assertEquals(reason, failure.reason());
        assertSame(cause, failure.cause());
        assertThat(failure.message()).contains("Sequence: captured " + completedStages + "/2", "stage 2/2");
    }

    private static Stream<Arguments> progressionFailures() {
        return terminals().stream().flatMap(terminal -> Stream.of(
                argumentSet(terminal.getName() + " / acquiring / source", 1, SOURCE_FAILED, "source 2", terminal),
                argumentSet(terminal.getName() + " / acquiring / waiting", 1, WAIT_FAILED, "park 1", terminal),
                argumentSet(terminal.getName() + " / persisting / source", 2, SOURCE_FAILED, "source 3", terminal),
                argumentSet(terminal.getName() + " / persisting / waiting", 2, WAIT_FAILED, "park 2", terminal)));
    }

    @ParameterizedTest
    @MethodSource("progressionInterruptions")
    void sourceAndWaitingInterruptionsRetainProgressInBothPhases(int completedStages, String interruptionAt, Class<?> status) {
        Consumer<String> boundary = mock();
        doAnswer(ignored -> { currentThread().interrupt(); return null; }).when(boundary).accept(interruptionAt);
        var waiting = progressing(boundary);
        var first = equalTo("ready");
        var second = equalTo("done");
        var result = diagnostic(waiting).until(captured(first, second));
        var failure = failure(INTERRUPTED, result);
        assertInstanceOf(status, attempts(result).getLast());
        assertSame(second, result.conditions().getLast().condition());
        assertThat(failure.message()).contains("Sequence: captured " + completedStages + "/2", "stage 2/2");
        assertTrue(currentThread().isInterrupted());
    }

    private static List<Arguments> progressionInterruptions() {
        return List.of(argumentSet("acquiring / source", 1, "source 2", FailedAfterObservation.class),
                argumentSet("acquiring / waiting", 1, "park 1", FailedBeforeObservation.class),
                argumentSet("persisting / source", 2, "source 3", FailedAfterObservation.class),
                argumentSet("persisting / waiting", 2, "park 2", FailedBeforeObservation.class));
    }

    private ValueAwait<String> progressing(Consumer<String> boundary) {
        Supplier<String> values = mock();
        when(values.get()).thenReturn("ready", "done");
        int[] reads = {0};
        return await(() -> {
            boundary.accept("source " + ++reads[0]);
            return values.get();
        }).usingTime(time, nanos -> {
            boundary.accept("park " + reads[0]);
            time.accept(nanos);
        }).every(ofNanos(1)).persisting(ofNanos(2));
    }

    @Test
    void narrowingSequencesCaptureTheSourceAndRetryInBothTerminals() {
        var first = new String("first");
        var second = new String("second");
        Object[] observations = {42, first, 43L, second};
        int[] reads = {0};
        var waiting = await(() -> observations[reads[0]++ % observations.length])
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10));
        var start = instanceOf(String.class);
        var finish = exactlyInstanceOf(String.class);

        var values = waiting.until(captured(start, finish));
        AwaitResult<Object, List<Object>> attempt = diagnostic(waiting).until(captured(start, finish));

        assertSame(first, values.getFirst());
        assertSame(second, values.getLast());
        assertEquals(values, attempt.orThrow());
        assertEquals(4, attempts(attempt).size());
        assertInstanceOf(Satisfied.class, attempts(attempt).get(1));
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
                .usingTime(time, time).every(ofNanos(1));

        var values = waiting.until(captured(singleElement(instanceOf(Integer.class)),
                singleElement(exactlyInstanceOf(Integer.class))));
        AwaitResult<List<Number>, List<List<Number>>> attempt = diagnostic(waiting).until(captured(
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
        Number acquired = new BigDecimal("2.00");
        Number persisted = new BigDecimal("3.00");
        var observations = List.of(first, Map.of("last", acquired), Map.of("last", persisted));
        int[] reads = {0};
        var waiting = await(() -> observations.get(reads[0]++ % observations.size()))
                .usingTime(time, time).every(java.time.Duration.ofNanos(1))
                .persisting(java.time.Duration.ofNanos(1));
        var start = valueFor("first");
        var finish = valueFor("last", isNotNull);

        List<Map<String, Number>> values = waiting.until(captured(start, finish));
        AwaitResult<Map<String, Number>, List<Map<String, Number>>> attempt = diagnostic(waiting).until(captured(start, finish));

        assertSame(first, values.getFirst());
        assertNull(values.getFirst().get("first"));
        assertSame(persisted, values.getLast().get("last"));
        assertEquals(values, attempt.orThrow());
        assertEquals(3, attempts(attempt).size());
        assertEquals(6, reads[0]);
        var acquisition = attempts(attempt).get(1);
        assertInstanceOf(Satisfied.class, acquisition);
        assertSame(acquired, observation(acquisition).get("last"));
        var firstAttempt = attempts(attempt).getFirst();
        assertInstanceOf(Satisfied.class, firstAttempt);
        assertSame(first, observation(firstAttempt));
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    @Test
    void expectedSequencesCaptureTheOriginalObservationsInBothTerminals() {
        Number first = new BigDecimal("1.00");
        Number second = new BigInteger("2");
        Number third = 1024L;
        Number[] observations = {first, second, third, first, second, third};
        int[] reads = {0};
        var waiting = timedAwait(() -> observations[reads[0]++], config(1, 5, 0), time, time);
        var firstStage = equalTo(new BigDecimal("1.00"));
        var secondStage = equalTo(new BigInteger("2"));
        var finalStage = equalTo(1024L);

        var sequence = captured(firstStage, secondStage, finalStage).because("numeric states");
        List<Number> values = waiting.until(sequence);
        AwaitResult<Number, List<Number>> captured = diagnostic(waiting).until(sequence);

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
    void diagnosticSelectedSequenceRefreshesOnlyItsLastValueDuringPersistence() {
        var first = new String("first");
        var acquired = new String("acquired");
        var persisted = new String("persisted");
        var observations = List.of(List.of(first, "ignored"), List.of("ignored", acquired), List.of("ignored", persisted));
        int[] reads = {0};

        AwaitResult<List<String>, List<List<String>>> result = tryAwait(() -> observations.get(reads[0]++))
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5)).persisting(ofNanos(1))
                .until(captured(firstElement, lastElement));

        var values = result.orThrow();
        assertSame(observations.getFirst(), values.get(0));
        assertSame(observations.getLast(), values.get(1));
        var earlier = attempts(result).get(1);
        assertInstanceOf(Satisfied.class, earlier);
        assertSame(acquired, observation(earlier).getLast());
        var firstAttempt = attempts(result).getFirst();
        assertInstanceOf(Satisfied.class, firstAttempt);
        assertSame(first, observation(firstAttempt).getFirst());
        assertThrows(UnsupportedOperationException.class, values::clear);
        assertEquals(3, reads[0]);
        assertThat(result.conditions()).extracting(history -> history.attempts().size()).containsExactly(1, 2);
    }

    @Test
    void historyRecordsTheCheckedStageAndItsActualResult() {
        var source = List.of("created", "created", "processing", "completed").iterator();
        var first = equalTo("created");
        var second = equalTo("processing");
        var third = equalTo("completed");
        var sequence = captured(first, second, third).because("lifecycle");
        var result = tryAwait(source::next).usingTime(time, time).every(ofNanos(1)).until(sequence);

        assertThat(result.conditions()).extracting(history -> history.condition())
                .containsExactly(first, second, third);
        assertThat(result.conditions()).extracting(history -> List.copyOf(history.attempts().keySet()))
                .containsExactly(List.of(1L), List.of(2L, 3L), List.of(4L));
        assertThat(attempts(result)).hasExactlyElementsOfTypes(Satisfied.class, Unsatisfied.class,
                        Satisfied.class, Satisfied.class)
                .allSatisfy(attempt -> assertThat(observation(attempt)).isNotEmpty());

        assertEquals(List.of("created", "processing", "completed"),
                result.orThrow());
    }

    @ParameterizedTest
    @MethodSource("interruptedStages")
    void interruptedStageIsNotCapturedAfterANormalCallbackReturn(
            int stage, boolean matching, CheckedPredicate<String> firstCheck, BinaryOperator<Supplier<AwaitFailure>> terminal) {
        int[] sources = {0};
        var waiting = timedAwait(() -> {
            sources[0]++;
            return "ready";
        }, config(1, 3, 0), time, time);
        var first = Conditions.<String>matches(firstCheck);
        var second = Conditions.<String>matches(value -> {
            currentThread().interrupt();
            return matching;
        });
        var failure = terminal.apply(
                () -> assertThrows(AwaitExecutionException.class, () -> waiting.until(captured(first, second))).failure(),
                () -> {
                    var result = diagnostic(waiting).until(captured(first, second));
                    uncontrolled(CONDITION, attempts(result).getLast());
                    assertThat(result.conditions()).filteredOn(history -> !history.attempts().isEmpty()).hasSize(stage);
                    return failure(result);
                }).get();
        assertEquals(INTERRUPTED, failure.reason());
        assertInstanceOf(InterruptedException.class, failure.cause());
        assertThat(failure.message()).contains("Sequence: captured " + (stage - 1) + "/2", "stage " + stage + "/2");
        assertEquals(stage, sources[0]);
        assertTrue(currentThread().isInterrupted());
    }

    private static Stream<Arguments> interruptedStages() {
        return terminals().stream().flatMap(terminal -> Stream.of(false, true).flatMap(matching -> Stream.of(
                argumentSet(terminal.getName() + " / first stage / matching=" + matching, 1, matching,
                        (CheckedPredicate<String>) value -> { currentThread().interrupt(); return matching; }, terminal),
                argumentSet(terminal.getName() + " / second stage / matching=" + matching, 2, matching,
                        (CheckedPredicate<String>) value -> true, terminal))));
    }

    private static List<Named<BinaryOperator<Supplier<AwaitFailure>>>> terminals() {
        return List.of(named("await", (thrown, captured) -> thrown), named("tryAwait", (thrown, captured) -> captured));
    }

    @ParameterizedTest(name = "delay={0}")
    @ValueSource(longs = {4, 5})
    void lateSuccessfulSequenceKeepsTheFinalStageDiagnostics(long delay) {
        var sequence = captured(condition("first stage", (String value) -> satisfied()),
                condition("final stage", (String value) -> {
                    time.advanceNanos(delay);
                    return satisfied();
                }));
        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> "ready", config(1, 5, 0), time, time).until(sequence));
        assertThat(failure).hasMessageContainingAll("Sequence: captured 2/2", "stage 2/2", "final stage");
    }

    private record Payment(Status status, String detail) {}

    private enum Status { CREATED, PENDING, FINISHED }

    @Test
    void capturesExactlyOnePredicateStagePerPollingAttempt() {
        int[] calls = {0};

        List<Integer> result = timedAwait(() -> {
            calls[0]++;
            return 1;
        }, config(1, 10, 0), time, time).until(captured(matches(value -> value == 1),
                matches(value -> value == 1), matches(value -> value == 1)));

        assertEquals(List.of(1, 1, 1), result);
        assertEquals(3, calls[0]);
        assertThat(time.parkRequests).containsExactly(1L, 1L);
    }

    @Test
    void reportsTheNextStageWhenAcquisitionExpiresAfterCapture() {
        var result = timedTryAwait(() -> {
            time.advanceNanos(2);
            return "observed";
        }, config(1, 2, 0), time, time).until(captured(matches(value -> true), matches(value -> true)));
        var outcome = assertInstanceOf(Satisfied.class,
                attempts(result).getFirst());

        assertEquals("observed", observation(attempts(result).getFirst()));
        assertTrue(result.conditions().getLast().attempts().isEmpty());
        var failure = failure(TIMEOUT, result);
        assertThat(failure.message()).contains("Next stage 2/2:");
    }

    @Test
    void capturesAChangingSourceInStrictOrder() {
        var created = new Payment(Status.CREATED, "accepted");
        var pending = new Payment(Status.PENDING, "settling");
        var finished = new Payment(Status.FINISHED, "paid");
        Payment[] observations = {created, pending, finished};
        int[] next = {0};

        List<Payment> result = timedAwait(() -> observations[next[0]++],
                config(1, 10, 0), time, time).until(captured(matches(payment -> payment.status() == Status.CREATED),
                matches(payment -> payment.status() == Status.PENDING),
                matches(payment -> payment.status() == Status.FINISHED)));

        assertEquals(List.of(created, pending, finished), result);
    }

    @Test
    void persistenceReevaluatesAndRefreshesOnlyTheFinalStage() {
        String early = new String("early");
        String acquiredFinal = new String("acquired final");
        String firstPersistedFinal = new String("first persisted final");
        String persistedFinal = new String("final persisted final");
        String[] observations = {early, acquiredFinal, firstPersistedFinal, persistedFinal};
        int[] sourceCalls = {0};
        int[] earlyStageCalls = {0};
        int[] finalStageCalls = {0};

        List<String> result = timedAwait(() -> observations[sourceCalls[0]++],
                config(1, 10, 2), time, time).until(captured(matches(value -> {
                    earlyStageCalls[0]++;
                    return value == early;
                }), matches(value -> {
                    finalStageCalls[0]++;
                    return value != early;
                })));

        assertSame(early, result.get(0));
        assertSame(persistedFinal, result.get(1));
        assertEquals(4, sourceCalls[0]);
        assertEquals(1, earlyStageCalls[0]);
        assertEquals(3, finalStageCalls[0]);
        assertThat(time.parkRequests).containsExactly(1L, 1L, 1L);
    }

    @Test
    void finalStageMismatchDuringPersistenceFailsImmediately() {
        String early = new String("early");
        String acquiredFinal = new String("acquired final");
        String rejectedFinal = new String("rejected final");
        String[] observations = {early, acquiredFinal, rejectedFinal};
        int[] sourceCalls = {0};

        assertFailure(PERSISTENCE_FAILED, () -> timedAwait(
                () -> observations[sourceCalls[0]++], config(1, 10, 10), time, time).until(captured(matches(value -> value == early),
                        matches(value -> value != rejectedFinal))));

        assertEquals(3, sourceCalls[0]);
        assertThat(time.parkRequests).containsExactly(1L, 1L);
    }

    @Test
    void doesNotSkipAMissedIntermediateStage() {
        var created = new Payment(Status.CREATED, "accepted");
        var finished = new Payment(Status.FINISHED, "paid");
        Supplier<Payment> observations = mock();
        when(observations.get()).thenReturn(created, finished);

        assertFailure(TIMEOUT, () -> timedAwait(observations::get, config(1, 3, 0), time, time)
                .until(captured(matches(payment -> payment.status() == Status.CREATED),
                        matches(payment -> payment.status() == Status.PENDING), matches(payment -> payment.status() == Status.FINISHED))));

        verify(observations, times(3)).get();
    }

    @Test
    void capturesProjectedObservations() {
        String[] observations = {"a", "bb"};
        int[] next = {0};

        List<Integer> result = timedAwait(() -> observations[next[0]++].length(),
                config(1, 10, 0), time, time).until(captured(equalTo(1), equalTo(2)));

        assertEquals(List.of(1, 2), result);
    }

    @Test
    void preservingSequenceCapturesEveryStage() {
        String[] observations = {"alpha", "omega"};
        int[] next = {0};

        List<String> result = timedAwait(() -> observations[next[0]++],
                config(1, 10, 0), time, time).until(captured(matches((String value) -> value.startsWith("a")),
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
        List<String> result = timedAwait((Callable<String>) () -> null, config(1, 10, 0),
                time, time).until(captured(condition("first null", ignored -> satisfied()),
                condition("second null", ignored -> satisfied())));

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
        }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5));

        var sequence = captured(stages);
        java.util.Arrays.fill(stages, matches((Integer value) -> false));
        List<Integer> values = waiting.until(sequence);
        assertEquals(List.of(1, 2), values);

        calls[0] = 0;
        AwaitResult<Integer, List<Integer>> captured = diagnostic(waiting).until(sequence);
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
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10))
                .until(captured(present, present));

        assertEquals(observations, result);
    }

    @Test
    void sequenceStartsFreshAfterSuccessAndTimeout() {
        Condition.CapturedSource<Condition.InputCondition<Payment>> lifecycle = captured(matches(value -> value.status() == Status.CREATED),
                matches(value -> value.status() == Status.FINISHED));
        var firstCreated = new Payment(Status.CREATED, "first execution");
        var nextCreated = new Payment(Status.CREATED, "next execution");
        var firstFinished = new Payment(Status.FINISHED, "first completion");
        var nextFinished = new Payment(Status.FINISHED, "next completion");
        var pending = new Payment(Status.PENDING, "waiting");

        assertEquals(List.of(firstCreated, firstFinished), runLifecycle(
                new Payment[]{firstCreated, firstFinished}, lifecycle, 10));
        assertEquals(List.of(nextCreated, nextFinished), runLifecycle(
                new Payment[]{nextCreated, nextFinished}, lifecycle, 10));
        assertFailure(TIMEOUT, () -> runLifecycle(
                new Payment[]{firstCreated, pending, pending}, lifecycle, 3));
        assertEquals(List.of(nextCreated, firstFinished), runLifecycle(
                new Payment[]{nextCreated, firstFinished}, lifecycle, 10));
    }

    private static List<Payment> runLifecycle(Payment[] observations, Condition.CapturedSource<Condition.InputCondition<Payment>> lifecycle, long timeout) {
        var time = new FakeTime(0);
        int[] next = {0};
        return timedAwait(() -> observations[min(next[0]++, observations.length - 1)],
                config(1, timeout, 0), time, time).until(lifecycle);
    }
}

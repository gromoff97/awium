package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.internal.ExecutionStep;
import io.github.gromoff97.awium.internal.WaitCompletion;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.internal.WaitEngine;
import io.github.gromoff97.awium.result.Attempt;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.AwaitTestAccess.uncontrolled;
import static io.github.gromoff97.awium.ConditionSupport.sequence;
import static io.github.gromoff97.awium.Condition.Typed;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.*;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase;
import static io.github.gromoff97.awium.internal.WaitCompletion.*;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.*;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WaitEngineTest {

    private final FakeTime time = new FakeTime(0);

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void alreadyInterruptedWaitOnlyReadsTheClockToStartAndRecordItsFailure() {
        LongSupplier clock = mock();
        Callable<String> source = mock();
        when(clock.getAsLong()).thenReturn(0L, 7L);
        var engine = new WaitEngine<String>(config(1, 10, 0).withTime(clock, time), source,
                sequence(Conditions.isNotNull));
        currentThread().interrupt();

        var failure = assertInstanceOf(Uncontrolled.class, engine.waitFor(ignored -> {}));

        assertInstanceOf(InterruptedException.class, uncontrolled(WAITING, failure.step().attempt()).cause());
        assertEquals(7L, failure.step().completedAfterNanos());
        verify(clock, times(2)).getAsLong();
        verifyNoInteractions(source);
    }

    @Test
    void persistenceRetrievalClockCancellationPreventsAnotherSourceCall() {
        int[] sourceCalls = {0};
        LongSupplier clock = mock();
        when(clock.getAsLong()).thenAnswer(ignored -> time.getAsLong());
        var outcome = new WaitEngine<String>(config(5, 20, 10).withTime(clock, nanos -> {
            time.advanceNanos(nanos);
            doAnswer(ignored -> time.getAsLong())
                    .doAnswer(ignored -> { currentThread().interrupt(); return time.getAsLong(); })
                    .doAnswer(ignored -> time.getAsLong()).when(clock).getAsLong();
        }), () -> { sourceCalls[0]++; return "actual"; },
                sequence(Conditions.condition(
                        "ready", (String actual) -> ConditionResult.satisfied(actual)))).waitFor(ignored -> {});

        var failure = assertInstanceOf(Uncontrolled.class, outcome);
        var attempt = uncontrolled(WAITING, failure.step().attempt());
        assertEquals(1, sourceCalls[0]);
        assertEquals(PERSISTENCE, failure.step().phase());
        assertInstanceOf(FailedBeforeObservation.class, attempt);
        assertInstanceOf(InterruptedException.class, attempt.cause());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void startsAttemptOneImmediatelyAndZeroPersistenceFinishesOnSuccess() {
        var time = new FakeTime(100);
        var starts = new ArrayList<Long>();
        var result = new Object();

        WaitCompletion outcome = wait(time, config(5, 20, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> satisfied(result));

        var success = assertInstanceOf(SatisfiedWait.class, outcome);
        assertEquals(List.of(100L), starts);
        assertThat(time.parkRequests).isEmpty();
        assertEquals(0, completed(success.step()));
        assertEquals("actual", successfulObservation(success.step()));
    }

    @Test
    void measuresEveryFromObservationCompletion() {
        var starts = new ArrayList<Long>();
        Supplier<ConditionResult<String>> evaluations = mock();
        when(evaluations.get()).thenAnswer(ignored -> {
            time.advanceNanos(3);
            return unsatisfied("not yet");
        }).thenReturn(satisfied("ready"));

        WaitCompletion outcome = wait(time, config(5, 20, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> evaluations.get());

        var success = assertInstanceOf(SatisfiedWait.class, outcome);
        assertEquals(List.of(0L, 8L), starts);
        assertThat(time.parkRequests).containsExactly(5L);
        assertEquals("actual", successfulObservation(success.step()));
    }

    @ParameterizedTest(name = "interval={0}, evaluation={1}")
    @CsvSource({"9, 0", "4, 5"})
    void acceptsALaterSuccessCompletingOneNanosecondBeforeTheDeadline(long interval, long evaluation) {
        var starts = new ArrayList<Long>();
        Supplier<ConditionResult<String>> evaluations = mock();
        when(evaluations.get()).thenReturn(unsatisfied("not yet")).thenAnswer(ignored -> {
            time.advanceNanos(evaluation);
            return satisfied("ready");
        });

        WaitCompletion outcome = wait(time, config(interval, 10, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> evaluations.get());

        var success = assertInstanceOf(SatisfiedWait.class, outcome);
        assertEquals(List.of(0L, interval), starts);
        assertThat(time.parkRequests).containsExactly(interval);
        assertEquals(9, completed(success.step()));
        assertEquals("actual", successfulObservation(success.step()));
    }

    @Test
    void timesOutWhileParkedWithoutStartingAtTheDeadline() {
        var time = new FakeTime(100);
        var starts = new ArrayList<Long>();
        var assertion = new AssertionError("third mismatch");
        var evaluations = List.<ConditionResult<Object>>of(unsatisfied("mismatch 1"), unsatisfied("mismatch 2"),
                unsatisfied("mismatch 3", assertion)).iterator();

        WaitCompletion outcome = wait(time, config(4, 10, 0), () -> {
            starts.add(time.getAsLong());
            return new Object();
        }, actual -> evaluations.next());

        var timeout = assertInstanceOf(
                Timeout.class, outcome);
        var unsatisfied = timeout.step();
        assertEquals(List.of(100L, 104L, 108L), starts);
        assertThat(time.parkRequests).containsExactly(4L, 4L, 2L);
        assertEquals(10, timeout.elapsedNanos());
        assertEquals(8, completed(unsatisfied));
        assertEquals("mismatch 3", mismatch(unsatisfied));
        assertSame(assertion, unsatisfied.attempt().cause());
    }

    @Test
    void startsTheFirstObservationEvenIfTheClockReachesTheDeadline() {
        int[] sourceCalls = {0};
        LongSupplier clock = mock();
        when(clock.getAsLong()).thenReturn(0L, 10L);

        WaitCompletion outcome = new WaitEngine<String>(config(1, 10, 0).withTime(clock,
                ignored -> {}), () -> {
                    sourceCalls[0]++;
                    return "actual";
                }, sequence(Conditions.condition("test condition", (String actual) -> ConditionResult.satisfied(actual)))).waitFor(ignored -> {});

        assertInstanceOf(Timeout.class, outcome);
        assertEquals(1, sourceCalls[0]);
    }

    @Test
    void classifiesAnUnsatisfiedObservationCompletingAtTheDeadlineAsLate() {
        var actual = new Object();
        var assertion = new AssertionError("late mismatch");

        WaitCompletion outcome = wait(time, config(3, 10, 0),
                () -> actual, value -> {
                    time.advanceNanos(10);
                    return unsatisfied("late", assertion);
                });

        var timeout = assertInstanceOf(
                Timeout.class, outcome);
        var unsatisfied = timeout.step();
        assertEquals(10, completed(unsatisfied));
        assertEquals(10, timeout.elapsedNanos());
        assertSame(actual, observed(unsatisfied));
        assertEquals("late", mismatch(unsatisfied));
        assertSame(assertion, unsatisfied.attempt().cause());
    }

    @Test
    void classifiesASatisfiedObservationCompletingAfterTheDeadlineAsLate() {
        var actual = new Object();
        var result = new Object();

        WaitCompletion outcome = wait(time, config(3, 10, 0),
                () -> actual, value -> {
                    time.advanceNanos(11);
                    return satisfied(result);
                });

        var timeout = assertInstanceOf(
                Timeout.class, outcome);
        var satisfied = timeout.step();
        assertEquals(11, completed(satisfied));
        assertEquals(11, timeout.elapsedNanos());
        assertSame(actual, observed(satisfied));
        assertSame(actual, successfulObservation(satisfied));
    }

    @Test
    void rechecksTheTargetAfterPrematureAndSpuriousWakeups() {
        time.wakeAfter(2);
        time.wakeAfter(0);
        time.wakeAfter(1);
        var starts = new ArrayList<Long>();
        var evaluations = List.<ConditionResult<String>>of(unsatisfied("not ready"), satisfied("ready")).iterator();

        WaitCompletion outcome = wait(time, config(5, 20, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, value -> evaluations.next());

        assertInstanceOf(SatisfiedWait.class, outcome);
        assertEquals(List.of(0L, 5L), starts);
        assertThat(time.parkRequests).containsExactly(5L, 3L, 3L, 2L);
    }

    @Test
    void usesSubtractionBasedDeadlinesAcrossNanoTimeWraparound() {
        long started = Long.MAX_VALUE - 2;
        var time = new FakeTime(started);
        var starts = new ArrayList<Long>();
        var evaluations = List.<ConditionResult<String>>of(unsatisfied("not ready"), satisfied("ready")).iterator();

        WaitCompletion outcome = wait(time, config(4, 10, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, value -> evaluations.next());

        assertInstanceOf(SatisfiedWait.class, outcome);
        assertEquals(List.of(started, Long.MIN_VALUE + 1), starts);
        assertThat(time.parkRequests).containsExactly(4L);
    }

    @Test
    void uncontrolledObservationWinsEvenWhenItCompletesAfterAcquisitionDeadline() {
        var failure = new IllegalStateException("source failed");

        WaitCompletion outcome = wait(time, config(3, 10, 0), () -> {
            time.advanceNanos(11);
            throw failure;
        }, ConditionResult::satisfied);

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = uncontrolled(SOURCE, uncontrolled.step().attempt());
        assertSame(failure, attempt.cause());
    }

    @ParameterizedTest
    @MethodSource("waitingResults")
    void classifiesAParkingFailureForTheNextAttempt(long persistence, ConditionResult<String> evaluation) {
        var time = new FakeTime(100);
        var failure = new IllegalStateException("park failed");

        WaitCompletion outcome = wait(time, config(5, 20, persistence), nanos -> {
                    throw failure;
                }, () -> "actual",
                actual -> evaluation);

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = uncontrolled(WAITING, uncontrolled.step().attempt());
        assertSame(failure, attempt.cause());
        assertEquals(0, uncontrolled.step().completedAfterNanos());
    }

    @ParameterizedTest
    @MethodSource("waitingResults")
    void detectsAnInterruptRaisedWhileParkedForTheNextAttempt(long persistence, ConditionResult<String> evaluation) {
        var parkCalls = new int[1];

        WaitCompletion outcome = wait(time, config(5, 20, persistence), nanos -> {
                    parkCalls[0]++;
                    currentThread().interrupt();
                }, () -> "actual",
                actual -> evaluation);

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = uncontrolled(WAITING, uncontrolled.step().attempt());
        assertEquals(InterruptedException.class,
                attempt.cause().getClass());
        assertEquals(1, parkCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    private static List<Arguments> waitingResults() {
        return List.of(argumentSet("acquisition", 0L, ConditionResult.<String>unsatisfied("not yet")),
                argumentSet("persistence", 10L, satisfied("ready")));
    }

    @Test
    void recordsAParkingThrowableWithoutSettingTheInterruptFlag() {
        var interruption = new InterruptedException("waiting stopped");

        WaitCompletion outcome = wait(time, config(5, 20, 0), nanos ->
                        rethrow(interruption), () -> "actual",
                actual -> unsatisfied("not yet"));

        var uncontrolled = assertInstanceOf(Uncontrolled.class, outcome);
        var attempt = uncontrolled(WAITING, uncontrolled.step().attempt());
        assertSame(interruption, attempt.cause());
        assertFalse(currentThread().isInterrupted());
    }

    @Test
    void skipsSourceWhenRetrievalClockSetsInterruptFlag() {
        int[] sourceCalls = {0};
        LongSupplier clock = mock();
        when(clock.getAsLong()).thenReturn(0L).thenAnswer(ignored -> {
            currentThread().interrupt();
            return 0L;
        });
        var outcome = new WaitEngine<String>(config(5, 20, 0).withTime(clock,
                nanos -> { throw new AssertionError("parker must not be called"); }),
                () -> { sourceCalls[0]++; return "actual"; },
                sequence(Conditions.condition("ready", (String actual) -> ConditionResult.satisfied(actual)))).waitFor(ignored -> {});

        var failure = assertInstanceOf(Uncontrolled.class, outcome);
        var attempt = uncontrolled(WAITING, failure.step().attempt());
        assertEquals(0, sourceCalls[0]);
        assertInstanceOf(InterruptedException.class, attempt.cause());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void retrievalClockInterruptionWinsOverTheAcquisitionDeadline() {
        int[] sourceCalls = {0};
        LongSupplier clock = mock();
        when(clock.getAsLong()).thenReturn(0L, 0L, 0L, 0L, 0L, 1L)
                .thenAnswer(ignored -> { currentThread().interrupt(); return 2L; }).thenReturn(2L);
        var outcome = new WaitEngine<String>(config(1, 2, 0).withTime(clock, nanos -> {}), () -> {
            sourceCalls[0]++;
            return "actual";
        }, sequence(Conditions.condition("not ready", (String value) -> ConditionResult.unsatisfied("not yet")))).waitFor(ignored -> {});

        var failure = assertInstanceOf(Uncontrolled.class, outcome);
        var attempt = uncontrolled(WAITING, failure.step().attempt());
        assertEquals(1, sourceCalls[0]);
        assertInstanceOf(InterruptedException.class, attempt.cause());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("intervalInterruptions")
    void skipsParkingWhenIntervalClockSetsInterruptFlag(long persistence, ConditionResult<String> evaluation, Phase phase) {
        int[] sourceCalls = {0};
        int[] parkCalls = {0};
        LongSupplier clock = mock();
        when(clock.getAsLong()).thenReturn(0L);
        var outcome = new WaitEngine<String>(config(5, 20, persistence).withTime(clock,
                nanos -> parkCalls[0]++), () -> { sourceCalls[0]++; return "actual"; },
                sequence(Conditions.condition("ready", (String actual) -> {
                    doReturn(0L).doAnswer(ignored -> { currentThread().interrupt(); return 0L; }).when(clock).getAsLong();
                    return evaluation;
                }))).waitFor(ignored -> {});

        var failure = assertInstanceOf(Uncontrolled.class, outcome);
        var attempt = uncontrolled(WAITING, failure.step().attempt());
        assertEquals(1, sourceCalls[0]);
        assertEquals(0, parkCalls[0]);
        assertEquals(phase, failure.step().phase());
        assertInstanceOf(InterruptedException.class, attempt.cause());
        assertTrue(currentThread().isInterrupted());
    }

    private static List<Arguments> intervalInterruptions() {
        return List.of(argumentSet("acquisition", 0L, ConditionResult.unsatisfied("not yet"), ACQUISITION),
                argumentSet("persistence", 10L, ConditionResult.satisfied("actual"), PERSISTENCE));
    }

    @Test
    void parkingErrorsBecomeUncontrolled() {
        var fatal = new InternalError("fatal park");

        var outcome = wait(time, config(5, 20, 0), nanos -> {
                    throw fatal;
                }, () -> "actual",
                        actual -> unsatisfied("not yet"));
        assertSame(fatal, assertInstanceOf(Uncontrolled.class, outcome).step().attempt().cause());
    }

    @Test
    void detectsWaitingInterruptionBeforeTheFirstObservation() {
        currentThread().interrupt();

        WaitCompletion outcome = wait(time, config(3, 10, 0), () -> {
            throw new AssertionError("source must not be called");
        }, ConditionResult::satisfied);

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = uncontrolled(WAITING, uncontrolled.step().attempt());
        assertEquals(InterruptedException.class,
                attempt.cause().getClass());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void observesThePersistenceBoundaryWhileConditionEvaluationsChange() {
        var starts = new ArrayList<Long>();
        var results = List.of("acquired", "second", "third", "boundary").iterator();

        WaitCompletion outcome = wait(time, config(5, 20, 12), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> satisfied(results.next()));

        var success = assertInstanceOf(SatisfiedWait.class, outcome);
        assertEquals(List.of(0L, 5L, 10L, 12L), starts);
        assertThat(time.parkRequests).containsExactly(5L, 5L, 2L);
        assertEquals(12, completed(success.step()));
        assertEquals("actual", successfulObservation(success.step()));
    }

    @Test
    void persistenceRemainderCapsThePollingInterval() {
        var starts = new ArrayList<Long>();

        WaitCompletion outcome = wait(time, config(5, 20, 3), () -> {
                    starts.add(time.getAsLong());
                    return time.getAsLong();
                }, ConditionResult::satisfied);

        var success = assertInstanceOf(SatisfiedWait.class, outcome);
        assertEquals(List.of(0L, 3L), starts);
        assertThat(time.parkRequests).containsExactly(3L);
        assertEquals(3, completed(success.step()));
        assertEquals(3L, successfulObservation(success.step()));
    }

    @Test
    void usesOverflowSafeDeadlinesDuringPersistence() {
        long started = Long.MAX_VALUE - 2;
        var time = new FakeTime(started);
        var starts = new ArrayList<Long>();

        WaitCompletion outcome = wait(time, config(4, 20, 6), () -> {
            starts.add(time.getAsLong());
            return time.getAsLong();
        }, ConditionResult::satisfied);

        var success = assertInstanceOf(SatisfiedWait.class, outcome);
        assertEquals(List.of(
                started, Long.MIN_VALUE + 1, Long.MIN_VALUE + 3), starts);
        assertThat(time.parkRequests).containsExactly(4L, 2L);
        assertEquals(6, completed(success.step()));
        assertEquals(Long.MIN_VALUE + 3, successfulObservation(success.step()));
    }

    @Test
    void persistsBeyondUpToAfterAcquisitionAtTheLastValidNanosecond() {
        var starts = new ArrayList<Long>();
        Supplier<ConditionResult<String>> evaluations = mock();
        when(evaluations.get()).thenReturn(unsatisfied("not yet")).thenAnswer(ignored -> {
            time.advanceNanos(5);
            return satisfied("acquired");
        }).thenReturn(satisfied("persisting")).thenReturn(satisfied("boundary"));

        WaitCompletion outcome = wait(time, config(4, 10, 5), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> evaluations.get());

        var success = assertInstanceOf(SatisfiedWait.class, outcome);
        assertEquals(List.of(0L, 4L, 13L, 14L), starts);
        assertThat(time.parkRequests).containsExactly(4L, 4L, 1L);
        assertEquals(14, completed(success.step()));
        assertEquals("actual", successfulObservation(success.step()));
    }

    @Test
    void treatsARegularSatisfiedObservationCompletingAfterBoundaryAsFinal() {
        var starts = new ArrayList<Long>();
        Supplier<ConditionResult<String>> evaluations = mock();
        when(evaluations.get()).thenReturn(satisfied("acquired")).thenAnswer(ignored -> {
            time.advanceNanos(5);
            return satisfied("late boundary");
        });

        WaitCompletion outcome = wait(time, config(6, 20, 10), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> evaluations.get());

        var success = assertInstanceOf(SatisfiedWait.class, outcome);
        assertEquals(List.of(0L, 6L), starts);
        assertThat(time.parkRequests).containsExactly(6L);
        assertEquals(11, completed(success.step()));
        assertEquals("actual", successfulObservation(success.step()));
    }

    @Test
    void losesPersistenceImmediatelyOnTheFirstUnsatisfiedObservation() {
        var time = new FakeTime(100);
        var failingActual = new Object();
        var assertion = new AssertionError("lost");
        var observations = List.of(new Object(), failingActual).iterator();
        var evaluations = List.of(satisfied(new Object()), unsatisfied("lost", assertion)).iterator();

        WaitCompletion outcome = wait(time, config(5, 20, 15), observations::next, actual -> evaluations.next());

        var loss = assertInstanceOf(PersistenceFailure.class, outcome);
        var unsatisfied = loss.step();
        assertEquals(0, loss.acquiredAfterNanos());
        assertEquals(5, completed(unsatisfied));
        assertSame(failingActual, observed(unsatisfied));
        assertEquals("lost", mismatch(unsatisfied));
        assertSame(assertion, unsatisfied.attempt().cause());
        assertThat(time.parkRequests).containsExactly(5L);
    }

    @Test
    void uncontrolledPersistenceObservationWinsAfterBothDeadlines() {
        var failure = new IllegalStateException("condition failed");
        Supplier<ConditionResult<Object>> evaluations = mock();
        when(evaluations.get()).thenReturn(satisfied(new Object())).thenAnswer(ignored -> {
            time.advanceNanos(10);
            throw failure;
        });

        WaitCompletion outcome = wait(time, config(2, 3, 5), Object::new, actual -> evaluations.get());

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = uncontrolled(CONDITION, uncontrolled.step().attempt());
        assertSame(failure, attempt.cause());
    }

    private static <S, R> WaitCompletion wait(FakeTime time, WaitConfiguration config, Callable<S> source, AwaitAttempt<S, R> condition) {
        return wait(time, config, time, source, condition);
    }

    private static <S, R> WaitCompletion wait(
            FakeTime time,
            WaitConfiguration config,
            LongConsumer parker,
            Callable<S> source,
            AwaitAttempt<S, R> condition) {
        return new WaitEngine<S>(config.withTime(time, parker), source,
                sequence(new Typed<>("test condition", null, condition))).waitFor(ignored -> {});
    }

    private static long completed(ExecutionStep<?> attempt) {
        return attempt.completedAfterNanos();
    }

    private static Object observed(ExecutionStep<?> attempt) {
        return observation(attempt.attempt());
    }

    private static Object successfulObservation(ExecutionStep<?> attempt) {
        return assertInstanceOf(Attempt.SatisfiedObservation.class, attempt.attempt()).observed();
    }

    private static String mismatch(ExecutionStep<?> attempt) {
        return assertInstanceOf(UnsatisfiedObservation.class, attempt.attempt()).mismatch();
    }
}

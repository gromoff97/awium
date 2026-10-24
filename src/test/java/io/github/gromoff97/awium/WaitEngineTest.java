package io.github.gromoff97.awium;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.ConditionTestRuntime.throwUnchecked;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.WaitCompletion.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitEngineTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void startsAttemptOneImmediatelyAndZeroPersistenceReturnsItsResult() {
        var time = new FakeTime(100);
        var starts = new ArrayList<Long>();
        var result = new Object();

        WaitCompletion<?, Object> outcome = wait(time, config(5, 20, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> satisfied(result));

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(100L), starts);
        assertEquals(List.of(), time.parkRequests);
        assertEquals(0, completed(success.attempt()));
        assertEquals(1, success.attempt().number());
        assertSame(result, result(success.attempt()));
    }

    @Test
    void measuresEveryFromObservationCompletion() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitCompletion<?, String> outcome = wait(time, config(5, 20, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> {
            if (calls[0]++ == 0) {
                time.advanceNanos(3);
                return unsatisfied("not yet");
            }
            return satisfied("ready");
        });

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 8L), starts);
        assertEquals(List.of(5L), time.parkRequests);
        assertEquals("ready", result(success.attempt()));
    }

    @Test
    void startsALaterAttemptOneNanosecondBeforeTheAcquisitionDeadline() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitCompletion<?, String> outcome = wait(time, config(9, 10, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> calls[0]++ == 0
                ? unsatisfied("not yet")
                : satisfied("ready"));

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 9L), starts);
        assertEquals(List.of(9L), time.parkRequests);
        assertEquals(9, completed(success.attempt()));
        assertEquals(2, success.attempt().number());
        assertEquals("ready", result(success.attempt()));
    }

    @Test
    void acceptsALaterSuccessCompletingOneNanosecondBeforeTheDeadline() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitCompletion<?, String> outcome = wait(time, config(4, 10, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> {
            if (calls[0]++ == 0) {
                return unsatisfied("not yet");
            }
            time.advanceNanos(5);
            return satisfied("ready");
        });

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 4L), starts);
        assertEquals(List.of(4L), time.parkRequests);
        assertEquals(9, completed(success.attempt()));
        assertEquals(2, success.attempt().number());
        assertEquals("ready", result(success.attempt()));
    }

    @Test
    void timesOutWhileParkedWithoutStartingAtTheDeadline() {
        var time = new FakeTime(100);
        var starts = new ArrayList<Long>();
        var assertion = new AssertionError("third mismatch");
        var calls = new int[1];

        WaitCompletion<?, Object> outcome = wait(time, config(4, 10, 0), () -> {
            starts.add(time.getAsLong());
            return new Object();
        }, actual -> {
            int call = ++calls[0];
            return call == 3
                    ? unsatisfied("mismatch " + call, assertion)
                    : unsatisfied("mismatch " + call);
        });

        var timeout = assertInstanceOf(
                Timeout.class, outcome);
        var unsatisfied = timeout.attempt();
        assertEquals(List.of(100L, 104L, 108L), starts);
        assertEquals(List.of(4L, 4L, 2L), time.parkRequests);
        assertEquals(3, timeout.attempt().number());
        assertEquals(10, timeout.elapsedNanos());
        assertEquals(8, completed(unsatisfied));
        assertEquals("mismatch 3", mismatch(unsatisfied));
        assertSame(assertion, assertion(unsatisfied));
    }

    @Test
    void startsTheFirstObservationEvenIfTheClockReachesTheDeadline() {
        int[] clockCalls = {0};
        int[] sourceCalls = {0};
        LongSupplier clock = () -> clockCalls[0]++ == 0 ? 0 : 10;

        WaitCompletion<?, String> outcome = new WaitEngine<String, String>(config(1, 10, 0).withTime(clock,
                ignored -> {}), () -> {
                    sourceCalls[0]++;
                    return "actual";
                }, ConditionAwaitAttempt.preserving(Conditions.<String>condition("test condition", actual -> satisfied(actual)).definition)).waitFor();

        assertInstanceOf(Timeout.class, outcome);
        assertEquals(1, sourceCalls[0]);
        assertEquals(1, outcome.attempt().number());
    }

    @Test
    void classifiesAnUnsatisfiedObservationCompletingAtTheDeadlineAsLate() {
        var time = new FakeTime(0);
        var actual = new Object();
        var assertion = new AssertionError("late mismatch");

        WaitCompletion<?, Object> outcome = wait(time, config(3, 10, 0),
                () -> actual, value -> {
                    time.advanceNanos(10);
                    return unsatisfied("late", assertion);
                });

        var timeout = assertInstanceOf(
                Timeout.class, outcome);
        var unsatisfied = timeout.attempt();
        assertEquals(1, timeout.attempt().number());
        assertEquals(10, completed(unsatisfied));
        assertEquals(10, timeout.elapsedNanos());
        assertSame(actual, observed(unsatisfied));
        assertEquals("late", mismatch(unsatisfied));
        assertSame(assertion, assertion(unsatisfied));
    }

    @Test
    void classifiesASatisfiedObservationCompletingAfterTheDeadlineAsLate() {
        var time = new FakeTime(0);
        var actual = new Object();
        var result = new Object();

        WaitCompletion<?, Object> outcome = wait(time, config(3, 10, 0),
                () -> actual, value -> {
                    time.advanceNanos(11);
                    return satisfied(result);
                });

        var timeout = assertInstanceOf(
                Timeout.class, outcome);
        var satisfied = timeout.attempt();
        assertEquals(1, timeout.attempt().number());
        assertEquals(11, completed(satisfied));
        assertEquals(11, timeout.elapsedNanos());
        assertSame(actual, observed(satisfied));
        assertSame(result, result(satisfied));
    }

    @Test
    void rechecksTheTargetAfterPrematureAndSpuriousWakeups() {
        var time = new FakeTime(0);
        time.wakeAfter(2);
        time.wakeAfter(0);
        time.wakeAfter(1);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitCompletion<?, String> outcome = wait(time, config(5, 20, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, value -> calls[0]++ == 0
                ? unsatisfied("not ready")
                : satisfied("ready"));

        assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 5L), starts);
        assertEquals(List.of(5L, 3L, 3L, 2L), time.parkRequests);
    }

    @Test
    void usesSubtractionBasedDeadlinesAcrossNanoTimeWraparound() {
        long started = Long.MAX_VALUE - 2;
        var time = new FakeTime(started);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitCompletion<?, String> outcome = wait(time, config(4, 10, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, value -> calls[0]++ == 0
                ? unsatisfied("not ready")
                : satisfied("ready"));

        assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(started, Long.MIN_VALUE + 1), starts);
        assertEquals(List.of(4L), time.parkRequests);
        assertEquals(2, outcome.attempt().number());
    }

    @Test
    void uncontrolledObservationWinsEvenWhenItCompletesAfterAcquisitionDeadline() {
        var time = new FakeTime(0);
        var failure = new IllegalStateException("source failed");

        WaitCompletion<?, Object> outcome = wait(time, config(3, 10, 0), () -> {
            time.advanceNanos(11);
            throw failure;
        }, ConditionResult::satisfied);

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                AttemptRecord.Outcome.SourceRetrievalFailed.class,
                uncontrolled.attempt().outcome());
        assertSame(failure, attempt.cause());
        assertEquals(1, uncontrolled.attempt().number());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 10})
    void classifiesAParkingFailureForTheNextAttempt(long persistence) {
        var time = new FakeTime(100);
        var failure = new IllegalStateException("park failed");

        WaitCompletion<?, String> outcome = wait(time, config(5, 20, persistence), nanos -> {
                    throw failure;
                }, () -> "actual",
                actual -> persistence == 0
                        ? unsatisfied("not yet")
                        : satisfied("ready"));

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                AttemptRecord.Outcome.WaitingFailed.class,
                uncontrolled.attempt().outcome());
        assertEquals(2, uncontrolled.attempt().number());
        assertSame(failure, attempt.cause());
        assertEquals(0, attempt.timing().startOffset().toNanos());
        assertEquals(0, attempt.timing().completionOffset().toNanos());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 10})
    void detectsAnInterruptRaisedWhileParkedForTheNextAttempt(long persistence) {
        var time = new FakeTime(0);
        var parkCalls = new int[1];

        WaitCompletion<?, String> outcome = wait(time, config(5, 20, persistence), nanos -> {
                    parkCalls[0]++;
                    currentThread().interrupt();
                }, () -> "actual",
                actual -> persistence == 0
                        ? unsatisfied("not yet")
                        : satisfied("ready"));

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                AttemptRecord.Outcome.WaitingFailed.class,
                uncontrolled.attempt().outcome());
        assertEquals(2, uncontrolled.attempt().number());
        assertEquals(InterruptedException.class,
                attempt.cause().getClass());
        assertEquals(1, parkCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void recordsAParkingThrowableWithoutSettingTheInterruptFlag() {
        var time = new FakeTime(0);
        var interruption = new InterruptedException("waiting stopped");

        WaitCompletion<?, String> outcome = wait(time, config(5, 20, 0), nanos ->
                        throwUnchecked(interruption), () -> "actual",
                actual -> unsatisfied("not yet"));

        var uncontrolled = assertInstanceOf(Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(AttemptRecord.Outcome.WaitingFailed.class,
                uncontrolled.attempt().outcome());
        assertEquals(2, uncontrolled.attempt().number());
        assertSame(interruption, attempt.cause());
        assertFalse(currentThread().isInterrupted());
    }

    @Test
    void skipsSourceWhenRetrievalClockSetsInterruptFlag() {
        int[] sourceCalls = {0};
        LongSupplier clock = () -> {
            currentThread().interrupt();
            return 0;
        };
        var outcome = new WaitEngine<String, String>(config(5, 20, 0).withTime(clock,
                nanos -> { throw new AssertionError("parker must not be called"); }),
                () -> { sourceCalls[0]++; return "actual"; },
                ConditionAwaitAttempt.preserving(Conditions.<String>condition("ready", ConditionResult::satisfied).definition), 0).waitFor();

        var failure = assertInstanceOf(Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(AttemptRecord.Outcome.WaitingFailed.class, failure.attempt().outcome());
        assertEquals(0, sourceCalls[0]);
        assertEquals(1, failure.attempt().number());
        assertInstanceOf(InterruptedException.class, attempt.cause());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void retrievalClockInterruptionWinsOverTheAcquisitionDeadline() {
        long[] ticks = {0, 0, 0, 0, 0, 1, 2};
        int[] clocks = {0};
        int[] sourceCalls = {0};
        LongSupplier clock = () -> {
            int index = clocks[0]++;
            if (index == ticks.length - 1) currentThread().interrupt();
            return ticks[Math.min(index, ticks.length - 1)];
        };
        var outcome = new WaitEngine<String, String>(config(1, 2, 0).withTime(clock, nanos -> {}), () -> {
            sourceCalls[0]++;
            return "actual";
        }, ConditionAwaitAttempt.preserving(Conditions.<String>condition("not ready", value -> unsatisfied("not yet")).definition)).waitFor();

        var failure = assertInstanceOf(Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(AttemptRecord.Outcome.WaitingFailed.class, failure.attempt().outcome());
        assertEquals(1, sourceCalls[0]);
        assertEquals(2, failure.attempt().number());
        assertInstanceOf(InterruptedException.class, attempt.cause());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 10})
    void skipsParkingWhenIntervalClockSetsInterruptFlag(long persistence) {
        boolean[] evaluated = {false};
        boolean[] completionRecorded = {false};
        int[] sourceCalls = {0};
        int[] parkCalls = {0};
        LongSupplier clock = () -> {
            if (evaluated[0]) {
                if (completionRecorded[0]) currentThread().interrupt();
                completionRecorded[0] = true;
            }
            return 0;
        };
        var outcome = new WaitEngine<String, String>(config(5, 20, persistence).withTime(clock,
                nanos -> parkCalls[0]++), () -> { sourceCalls[0]++; return "actual"; },
                ConditionAwaitAttempt.preserving(Conditions.<String>condition("ready", actual -> {
                    evaluated[0] = true;
                    return persistence == 0 ? unsatisfied("not yet") : satisfied("ready");
                }).definition), 0).waitFor();

        var failure = assertInstanceOf(Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(AttemptRecord.Outcome.WaitingFailed.class, failure.attempt().outcome());
        assertEquals(1, sourceCalls[0]);
        assertEquals(0, parkCalls[0]);
        assertEquals(2, failure.attempt().number());
        assertEquals(persistence == 0 ? AttemptRecord.Phase.ACQUISITION : AttemptRecord.Phase.PERSISTENCE,
                failure.attempt().phase());
        assertInstanceOf(InterruptedException.class, attempt.cause());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void parkingErrorsBecomeUncontrolled() {
        var time = new FakeTime(0);
        var fatal = new InternalError("fatal park");

        var outcome = wait(time, config(5, 20, 0), nanos -> {
                    throw fatal;
                }, () -> "actual",
                        actual -> unsatisfied("not yet"));
        assertSame(fatal, assertInstanceOf(Uncontrolled.class, outcome).attempt().outcome().cause());
    }

    @Test
    void detectsWaitingInterruptionBeforeTheFirstObservation() {
        var time = new FakeTime(0);
        currentThread().interrupt();

        WaitCompletion<?, Object> outcome = wait(time, config(3, 10, 0), () -> {
            throw new AssertionError("source must not be called");
        }, ConditionResult::satisfied);

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                AttemptRecord.Outcome.WaitingFailed.class,
                uncontrolled.attempt().outcome());
        assertEquals(1, uncontrolled.attempt().number());
        assertEquals(InterruptedException.class,
                attempt.cause().getClass());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void observesThePersistenceBoundaryAndReturnsItsChangingResult() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var results = List.of("acquired", "second", "third", "boundary");
        var calls = new int[1];

        WaitCompletion<?, String> outcome = wait(time, config(5, 20, 12), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> satisfied(results.get(calls[0]++)));

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 5L, 10L, 12L), starts);
        assertEquals(List.of(5L, 5L, 2L), time.parkRequests);
        assertEquals(12, completed(success.attempt()));
        assertEquals(4, success.attempt().number());
        assertEquals("boundary", result(success.attempt()));
    }

    @Test
    void persistenceRemainderCapsThePollingInterval() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();

        WaitCompletion<?, Long> outcome = wait(time, config(5, 20, 3), () -> {
                    starts.add(time.getAsLong());
                    return time.getAsLong();
                }, ConditionResult::satisfied);

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 3L), starts);
        assertEquals(List.of(3L), time.parkRequests);
        assertEquals(3, completed(success.attempt()));
        assertEquals(2, success.attempt().number());
        assertEquals(3L, result(success.attempt()));
    }

    @Test
    void usesOverflowSafeDeadlinesDuringPersistence() {
        long started = Long.MAX_VALUE - 2;
        var time = new FakeTime(started);
        var starts = new ArrayList<Long>();

        WaitCompletion<?, Long> outcome = wait(time, config(4, 20, 6), () -> {
            starts.add(time.getAsLong());
            return time.getAsLong();
        }, ConditionResult::satisfied);

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(
                started, Long.MIN_VALUE + 1, Long.MIN_VALUE + 3), starts);
        assertEquals(List.of(4L, 2L), time.parkRequests);
        assertEquals(6, completed(success.attempt()));
        assertEquals(Long.MIN_VALUE + 3, result(success.attempt()));
    }

    @Test
    void persistsBeyondUpToAfterAcquisitionAtTheLastValidNanosecond() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitCompletion<?, String> outcome = wait(time, config(4, 10, 5), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> {
            int call = calls[0]++;
            if (call == 0) {
                return unsatisfied("not yet");
            }
            if (call == 1) {
                time.advanceNanos(5);
                return satisfied("acquired");
            }
            return satisfied(call == 2 ? "persisting" : "boundary");
        });

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 4L, 13L, 14L), starts);
        assertEquals(List.of(4L, 4L, 1L), time.parkRequests);
        assertEquals(14, completed(success.attempt()));
        assertEquals(4, success.attempt().number());
        assertEquals("boundary", result(success.attempt()));
    }

    @Test
    void treatsARegularSatisfiedObservationCompletingAfterBoundaryAsFinal() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitCompletion<?, String> outcome = wait(time, config(6, 20, 10), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> {
            if (calls[0]++ == 0) {
                return satisfied("acquired");
            }
            time.advanceNanos(5);
            return satisfied("late boundary");
        });

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 6L), starts);
        assertEquals(List.of(6L), time.parkRequests);
        assertEquals(11, completed(success.attempt()));
        assertEquals(2, success.attempt().number());
        assertEquals("late boundary", result(success.attempt()));
    }

    @Test
    void losesPersistenceImmediatelyOnTheFirstUnsatisfiedObservation() {
        var time = new FakeTime(100);
        var failingActual = new Object();
        var assertion = new AssertionError("lost");
        var calls = new int[1];

        WaitCompletion<?, Object> outcome = wait(time, config(5, 20, 15),
                () -> calls[0] == 0 ? new Object() : failingActual,
                actual -> calls[0]++ == 0
                        ? satisfied(new Object())
                        : unsatisfied("lost", assertion));

        var loss = assertInstanceOf(PersistenceFailure.class, outcome);
        var unsatisfied = loss.attempt();
        assertEquals(0, loss.acquiredAfterNanos());
        assertEquals(5, completed(unsatisfied));
        assertEquals(2, loss.attempt().number());
        assertSame(failingActual, observed(unsatisfied));
        assertEquals("lost", mismatch(unsatisfied));
        assertSame(assertion, assertion(unsatisfied));
        assertEquals(List.of(5L), time.parkRequests);
    }

    @Test
    void uncontrolledPersistenceObservationWinsAfterBothDeadlines() {
        var time = new FakeTime(0);
        var failure = new IllegalStateException("condition failed");
        var calls = new int[1];

        WaitCompletion<?, Object> outcome = wait(time, config(2, 3, 5),
                Object::new, actual -> {
                    if (calls[0]++ == 0) {
                        return satisfied(new Object());
                    }
                    time.advanceNanos(10);
                    throw failure;
                });

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class, uncontrolled.attempt().outcome());
        assertSame(failure, attempt.cause());
        assertEquals(2, uncontrolled.attempt().number());
    }

    private static <S, R> WaitCompletion<S, R> wait(
            FakeTime time,
            WaitConfiguration config,
            Callable<S> source,
            CheckedFunction<S, ConditionResult<R>> condition) {
        return wait(time, config, time, source, condition);
    }

    private static <S, R> WaitCompletion<S, R> wait(
            FakeTime time,
            WaitConfiguration config,
            LongConsumer parker,
            Callable<S> source,
            CheckedFunction<S, ConditionResult<R>> condition) {
        return new WaitEngine<S, R>(config.withTime(time, parker), source,
                new ConditionAwaitAttempt<>(ConditionSupport.condition("test condition", null, condition))).waitFor();
    }

    private static long completed(AttemptRecord<?, ?> attempt) {
        return attempt.outcome().timing().completionOffset().toNanos();
    }

    private static Object observed(AttemptRecord<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AttemptRecord.Outcome.Evaluated<?, ?> outcome -> outcome.observed();
            case AttemptRecord.Outcome.ConditionFailed<?, ?> outcome -> outcome.observed();
            case AttemptRecord.Outcome.SourceInterrupted<?, ?> outcome -> outcome.observed();
            default -> throw new IllegalArgumentException("attempt has no observed value");
        };
    }

    private static Object result(AttemptRecord<?, ?> attempt) {
        var evaluated = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, attempt.outcome());
        return assertInstanceOf(ConditionResult.Satisfied.class, evaluated.evaluation()).result();
    }

    private static String mismatch(AttemptRecord<?, ?> attempt) {
        var evaluated = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, attempt.outcome());
        return assertInstanceOf(ConditionResult.Unsatisfied.class, evaluated.evaluation()).mismatch();
    }

    private static Throwable assertion(AttemptRecord<?, ?> attempt) {
        var evaluated = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, attempt.outcome());
        return assertInstanceOf(ConditionResult.Unsatisfied.class, evaluated.evaluation()).cause();
    }
}

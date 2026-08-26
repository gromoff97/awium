package io.github.gromoff97.awium;

import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.await.AwaitAttempt;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;
import static io.github.gromoff97.awium.engine.WaitOutcome.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.engine.*;
import io.github.gromoff97.awium.exceptions.AwaitConfigurationConflictException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WaitEngineTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void rejectsConflictingConfigurationBeforeStartingTheEngine() {
        var clockCalls = new int[1];
        var sourceCalls = new int[1];
        var engine = new WaitEngine(config(2, 1, 0), () -> {
            clockCalls[0]++;
            return 0;
        }, ignored -> {});

        assertThrows(AwaitConfigurationConflictException.class,
                () -> engine.waitFor(() -> {
                    sourceCalls[0]++;
                    return "actual";
                }, actual -> satisfied(actual)));

        assertEquals(0, clockCalls[0]);
        assertEquals(0, sourceCalls[0]);
    }

    @Test
    void startsAttemptOneImmediatelyAndZeroPersistenceReturnsItsResult() {
        var time = new FakeTime(100);
        var starts = new ArrayList<Long>();
        var result = new Object();

        WaitOutcome<?, Object> outcome = wait(time, config(5, 20, 0), () -> {
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

        WaitOutcome<?, String> outcome = wait(time, config(5, 20, 0), () -> {
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

        WaitOutcome<?, String> outcome = wait(time, config(9, 10, 0), () -> {
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

        WaitOutcome<?, String> outcome = wait(time, config(4, 10, 0), () -> {
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
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var assertion = new AssertionError("third mismatch");
        var calls = new int[1];

        WaitOutcome<?, Object> outcome = wait(time, config(4, 10, 0), () -> {
            starts.add(time.getAsLong());
            return new Object();
        }, actual -> {
            int call = ++calls[0];
            return call == 3
                    ? assertionUnsatisfied("mismatch " + call, assertion)
                    : unsatisfied("mismatch " + call);
        });

        var timeout = assertInstanceOf(
                TimeoutBetweenObservations.class, outcome);
        var unsatisfied = timeout.attempt();
        assertEquals(List.of(0L, 4L, 8L), starts);
        assertEquals(List.of(4L, 4L, 2L), time.parkRequests);
        assertEquals(3, timeout.attempt().number());
        assertEquals(10, timeout.completedNanos());
        assertEquals(8, completed(unsatisfied));
        assertEquals("mismatch 3", mismatch(unsatisfied));
        assertSame(assertion, assertion(unsatisfied));
    }

    @Test
    void classifiesAnUnsatisfiedObservationCompletingAtTheDeadlineAsLate() {
        var time = new FakeTime(0);
        var actual = new Object();
        var assertion = new AssertionError("late mismatch");

        WaitOutcome<?, Object> outcome = wait(time, config(3, 10, 0),
                () -> actual, value -> {
                    time.advanceNanos(10);
                    return assertionUnsatisfied("late", assertion);
                });

        var timeout = assertInstanceOf(
                LateUnsatisfiedTimeout.class, outcome);
        var unsatisfied = timeout.attempt();
        assertEquals(1, timeout.attempt().number());
        assertEquals(10, completed(unsatisfied));
        assertSame(actual, observed(unsatisfied));
        assertEquals("late", mismatch(unsatisfied));
        assertSame(assertion, assertion(unsatisfied));
    }

    @Test
    void classifiesASatisfiedObservationCompletingAfterTheDeadlineAsLate() {
        var time = new FakeTime(0);
        var actual = new Object();
        var result = new Object();

        WaitOutcome<?, Object> outcome = wait(time, config(3, 10, 0),
                () -> actual, value -> {
                    time.advanceNanos(11);
                    return satisfied(result);
                });

        var timeout = assertInstanceOf(
                LateSatisfiedTimeout.class, outcome);
        var satisfied = timeout.attempt();
        assertEquals(1, timeout.attempt().number());
        assertEquals(11, completed(satisfied));
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

        WaitOutcome<?, String> outcome = wait(time, config(5, 20, 0), () -> {
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

        WaitOutcome<?, String> outcome = wait(time, config(4, 10, 0), () -> {
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

        WaitOutcome<?, Object> outcome = wait(time, config(3, 10, 0), () -> {
            time.advanceNanos(11);
            throw failure;
        }, Evaluation::satisfied);

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                AwaitAttempt.Outcome.SourceRetrievalFailed.class,
                uncontrolled.attempt().outcome());
        assertSame(failure, attempt.failure());
        assertEquals(1, uncontrolled.attempt().number());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 10})
    void classifiesAParkingFailureForTheNextAttempt(long persistence) {
        var time = new FakeTime(0);
        var failure = new IllegalStateException("park failed");

        WaitOutcome<?, String> outcome = wait(time, config(5, 20, persistence), nanos -> {
                    throw failure;
                }, () -> "actual",
                actual -> persistence == 0
                        ? unsatisfied("not yet")
                        : satisfied("ready"));

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                AwaitAttempt.Outcome.WaitingFailed.class,
                uncontrolled.attempt().outcome());
        assertEquals(2, uncontrolled.attempt().number());
        assertSame(failure, attempt.failure());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 10})
    void detectsAnInterruptRaisedWhileParkedForTheNextAttempt(long persistence) {
        var time = new FakeTime(0);
        var parkCalls = new int[1];

        WaitOutcome<?, String> outcome = wait(time, config(5, 20, persistence), nanos -> {
                    parkCalls[0]++;
                    currentThread().interrupt();
                }, () -> "actual",
                actual -> persistence == 0
                        ? unsatisfied("not yet")
                        : satisfied("ready"));

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                AwaitAttempt.Outcome.WaitingFailed.class,
                uncontrolled.attempt().outcome());
        assertEquals(2, uncontrolled.attempt().number());
        assertEquals(InterruptedException.class,
                attempt.failure().getClass());
        assertEquals(1, parkCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void restoresAnInterruptedExceptionThrownWhileParking() {
        var time = new FakeTime(0);
        var interruption = new InterruptedException("waiting stopped");

        WaitOutcome<?, String> outcome = wait(time, config(5, 20, 0), nanos ->
                        throwUnchecked(interruption), () -> "actual",
                actual -> unsatisfied("not yet"));

        var uncontrolled = assertInstanceOf(Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(AwaitAttempt.Outcome.WaitingFailed.class,
                uncontrolled.attempt().outcome());
        assertEquals(2, uncontrolled.attempt().number());
        assertSame(interruption, attempt.failure());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void fatalParkingSignalsEscapeUnchanged() {
        var time = new FakeTime(0);
        var fatal = new InternalError("fatal park");

        assertSame(fatal, assertThrows(InternalError.class,
                () -> wait(time, config(5, 20, 0), nanos -> {
                    throw fatal;
                }, () -> "actual",
                        actual -> unsatisfied("not yet"))));
    }

    @Test
    void detectsWaitingInterruptionBeforeTheFirstObservation() {
        var time = new FakeTime(0);
        currentThread().interrupt();

        WaitOutcome<?, Object> outcome = wait(time, config(3, 10, 0), () -> {
            throw new AssertionError("source must not be called");
        }, Evaluation::satisfied);

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                AwaitAttempt.Outcome.WaitingFailed.class,
                uncontrolled.attempt().outcome());
        assertEquals(1, uncontrolled.attempt().number());
        assertEquals(InterruptedException.class,
                attempt.failure().getClass());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void observesThePersistenceBoundaryAndReturnsItsChangingResult() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var results = List.of("acquired", "second", "third", "boundary");
        var calls = new int[1];

        WaitOutcome<?, String> outcome = wait(time, config(5, 20, 12), () -> {
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

        WaitOutcome<?, Long> outcome = wait(time, config(5, 20, 3), () -> {
                    starts.add(time.getAsLong());
                    return time.getAsLong();
                }, Evaluation::satisfied);

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

        WaitOutcome<?, Long> outcome = wait(time, config(4, 20, 6), () -> {
            starts.add(time.getAsLong());
            return time.getAsLong();
        }, Evaluation::satisfied);

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

        WaitOutcome<?, String> outcome = wait(time, config(4, 10, 5), () -> {
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

        WaitOutcome<?, String> outcome = wait(time, config(6, 20, 10), () -> {
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
        var time = new FakeTime(0);
        var failingActual = new Object();
        var assertion = new AssertionError("lost");
        var calls = new int[1];

        WaitOutcome<?, Object> outcome = wait(time, config(5, 20, 15),
                () -> calls[0] == 0 ? new Object() : failingActual,
                actual -> calls[0]++ == 0
                        ? satisfied(new Object())
                        : assertionUnsatisfied("lost", assertion));

        var loss = assertInstanceOf(PersistenceFailure.class, outcome);
        var unsatisfied = loss.attempt();
        assertEquals(0, loss.acquiredNanos());
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

        WaitOutcome<?, Object> outcome = wait(time, config(2, 3, 5),
                Object::new, actual -> {
                    if (calls[0]++ == 0) {
                        return satisfied(new Object());
                    }
                    time.advanceNanos(10);
                    throw failure;
                });

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                AwaitAttempt.Outcome.ConditionEvaluationFailed.class,
                uncontrolled.attempt().outcome());
        assertSame(failure, attempt.failure());
        assertEquals(2, uncontrolled.attempt().number());
    }

    private static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure)
            throws E {
        throw (E) failure;
    }

    private static <S, R> WaitOutcome<S, R> wait(
            FakeTime time,
            WaitConfiguration config,
            Source<S> source,
            Function<S, Evaluation<R>> condition) {
        return wait(time, config, time, source, condition);
    }

    private static <S, R> WaitOutcome<S, R> wait(
            FakeTime time,
            WaitConfiguration config,
            LongConsumer parker,
            Source<S> source,
            Function<S, Evaluation<R>> condition) {
        return new WaitEngine(config, time, parker).waitFor(source, condition);
    }

    private static long completed(AwaitAttempt<?, ?> attempt) {
        return attempt.outcome().timing().completionOffset().toNanos();
    }

    private static Object observed(AwaitAttempt<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Satisfied<?, ?> outcome -> outcome.observed();
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> outcome -> outcome.observed();
            case AwaitAttempt.Outcome.AssertionUnsatisfied<?, ?> outcome -> outcome.observed();
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> outcome -> outcome.observed();
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> outcome -> outcome.observed();
            default -> throw new IllegalArgumentException("attempt has no observed value");
        };
    }

    private static Object result(AwaitAttempt<?, ?> attempt) {
        return assertInstanceOf(AwaitAttempt.Outcome.Satisfied.class,
                attempt.outcome()).result();
    }

    private static String mismatch(AwaitAttempt<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> outcome -> outcome.mismatch();
            case AwaitAttempt.Outcome.AssertionUnsatisfied<?, ?> outcome -> outcome.mismatch();
            default -> throw new IllegalArgumentException("attempt is satisfied");
        };
    }

    private static AssertionError assertion(AwaitAttempt<?, ?> attempt) {
        return assertInstanceOf(AwaitAttempt.Outcome.AssertionUnsatisfied.class,
                attempt.outcome()).assertion();
    }
}

package io.github.gromoff97.awium;

import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;
import static io.github.gromoff97.awium.engine.Attempt.*;
import static io.github.gromoff97.awium.engine.Attempt.Origin.*;
import static io.github.gromoff97.awium.engine.Attempt.Uncontrolled.*;
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
                }, new RuntimeCondition<>(actual -> satisfied(actual),
                        () -> "condition", null)));

        assertEquals(0, clockCalls[0]);
        assertEquals(0, sourceCalls[0]);
    }

    @Test
    void startsAttemptOneImmediatelyAndZeroStabilityReturnsItsResult() {
        var time = new FakeTime(100);
        var starts = new ArrayList<Long>();
        var result = new Object();

        WaitOutcome<Object> outcome = wait(time, config(5, 20, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> satisfied(result));

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(100L), starts);
        assertEquals(List.of(), time.parkRequests);
        assertEquals(100, success.completedNanos());
        assertEquals(1, success.number());
        assertSame(result, success.result());
    }

    @Test
    void measuresEveryFromObservationCompletion() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(5, 20, 0), () -> {
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
        assertEquals("ready", success.result());
    }

    @Test
    void startsALaterAttemptOneNanosecondBeforeTheAcquisitionDeadline() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(9, 10, 0), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> calls[0]++ == 0
                ? unsatisfied("not yet")
                : satisfied("ready"));

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 9L), starts);
        assertEquals(List.of(9L), time.parkRequests);
        assertEquals(9, success.completedNanos());
        assertEquals(2, success.number());
        assertEquals("ready", success.result());
    }

    @Test
    void acceptsALaterSuccessCompletingOneNanosecondBeforeTheDeadline() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(4, 10, 0), () -> {
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
        assertEquals(9, success.completedNanos());
        assertEquals(2, success.number());
        assertEquals("ready", success.result());
    }

    @Test
    void timesOutWhileParkedWithoutStartingAtTheDeadline() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var assertion = new AssertionError("third mismatch");
        var calls = new int[1];

        WaitOutcome<Object> outcome = wait(time, config(4, 10, 0), () -> {
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
        assertEquals(8, unsatisfied.completedNanos());
        assertEquals("mismatch 3", unsatisfied.mismatch());
        assertSame(assertion, unsatisfied.assertionCause());
    }

    @Test
    void classifiesAnUnsatisfiedObservationCompletingAtTheDeadlineAsLate() {
        var time = new FakeTime(0);
        var actual = new Object();
        var assertion = new AssertionError("late mismatch");

        WaitOutcome<Object> outcome = wait(time, config(3, 10, 0),
                () -> actual, value -> {
                    time.advanceNanos(10);
                    return assertionUnsatisfied("late", assertion);
                });

        var timeout = assertInstanceOf(
                LateUnsatisfiedTimeout.class, outcome);
        var unsatisfied = timeout.attempt();
        assertEquals(1, timeout.attempt().number());
        assertEquals(10, unsatisfied.completedNanos());
        assertSame(actual, unsatisfied.actual());
        assertEquals("late", unsatisfied.mismatch());
        assertSame(assertion, unsatisfied.assertionCause());
    }

    @Test
    void classifiesASatisfiedObservationCompletingAfterTheDeadlineAsLate() {
        var time = new FakeTime(0);
        var actual = new Object();
        var result = new Object();

        WaitOutcome<Object> outcome = wait(time, config(3, 10, 0),
                () -> actual, value -> {
                    time.advanceNanos(11);
                    return satisfied(result);
                });

        var timeout = assertInstanceOf(
                LateSatisfiedTimeout.class, outcome);
        var satisfied = timeout.attempt();
        assertEquals(1, timeout.attempt().number());
        assertEquals(11, satisfied.completedNanos());
        assertSame(actual, satisfied.actual());
        assertSame(result, satisfied.result());
    }

    @Test
    void rechecksTheTargetAfterPrematureAndSpuriousWakeups() {
        var time = new FakeTime(0);
        time.wakeAfter(2);
        time.wakeAfter(0);
        time.wakeAfter(1);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(5, 20, 0), () -> {
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

        WaitOutcome<String> outcome = wait(time, config(4, 10, 0), () -> {
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

        WaitOutcome<Object> outcome = wait(time, config(3, 10, 0), () -> {
            time.advanceNanos(11);
            throw failure;
        }, Evaluation::satisfied);

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                BeforeObservation.class,
                uncontrolled.attempt());
        assertSame(failure, attempt.cause());
        assertEquals(SOURCE, attempt.origin());
        assertEquals(1, attempt.number());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 10})
    void classifiesAParkingFailureForTheNextAttempt(long stableFor) {
        var time = new FakeTime(0);
        var failure = new IllegalStateException("park failed");

        WaitOutcome<String> outcome = wait(time, config(5, 20, stableFor), nanos -> {
                    throw failure;
                }, () -> "actual",
                actual -> stableFor == 0
                        ? unsatisfied("not yet")
                        : satisfied("ready"));

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                BeforeObservation.class,
                uncontrolled.attempt());
        assertEquals(WAITING, attempt.origin());
        assertEquals(2, attempt.number());
        assertSame(failure, attempt.cause());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 10})
    void detectsAnInterruptRaisedWhileParkedForTheNextAttempt(long stableFor) {
        var time = new FakeTime(0);
        var parkCalls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(5, 20, stableFor), nanos -> {
                    parkCalls[0]++;
                    currentThread().interrupt();
                }, () -> "actual",
                actual -> stableFor == 0
                        ? unsatisfied("not yet")
                        : satisfied("ready"));

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                BeforeObservation.class,
                uncontrolled.attempt());
        assertEquals(WAITING, attempt.origin());
        assertEquals(2, attempt.number());
        assertEquals(InterruptedException.class,
                attempt.cause().getClass());
        assertEquals(1, parkCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void restoresAnInterruptedExceptionThrownWhileParking() {
        var time = new FakeTime(0);
        var interruption = new InterruptedException("waiting stopped");

        WaitOutcome<String> outcome = wait(time, config(5, 20, 0), nanos ->
                        throwUnchecked(interruption), () -> "actual",
                actual -> unsatisfied("not yet"));

        var uncontrolled = assertInstanceOf(
                BeforeObservation.class, outcome);
        assertEquals(WAITING, uncontrolled.origin());
        assertEquals(2, uncontrolled.number());
        assertSame(interruption, uncontrolled.cause());
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

        WaitOutcome<Object> outcome = wait(time, config(3, 10, 0), () -> {
            throw new AssertionError("source must not be called");
        }, Evaluation::satisfied);

        var uncontrolled = assertInstanceOf(
                Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                BeforeObservation.class,
                uncontrolled.attempt());
        assertEquals(WAITING, attempt.origin());
        assertEquals(1, attempt.number());
        assertEquals(InterruptedException.class,
                attempt.cause().getClass());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void observesTheStabilityBoundaryAndReturnsItsChangingResult() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var results = List.of("acquired", "second", "third", "boundary");
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(5, 20, 12), () -> {
            starts.add(time.getAsLong());
            return "actual";
        }, actual -> satisfied(results.get(calls[0]++)));

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 5L, 10L, 12L), starts);
        assertEquals(List.of(5L, 5L, 2L), time.parkRequests);
        assertEquals(12, success.completedNanos());
        assertEquals(4, success.number());
        assertEquals("boundary", success.result());
    }

    @Test
    void stabilityRemainderCapsThePollingInterval() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();

        WaitOutcome<Long> outcome = wait(time, config(5, 20, 3), () -> {
                    starts.add(time.getAsLong());
                    return time.getAsLong();
                }, Evaluation::satisfied);

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 3L), starts);
        assertEquals(List.of(3L), time.parkRequests);
        assertEquals(3, success.completedNanos());
        assertEquals(2, success.number());
        assertEquals(3L, success.result());
    }

    @Test
    void usesOverflowSafeDeadlinesDuringStability() {
        long started = Long.MAX_VALUE - 2;
        var time = new FakeTime(started);
        var starts = new ArrayList<Long>();

        WaitOutcome<Long> outcome = wait(time, config(4, 20, 6), () -> {
            starts.add(time.getAsLong());
            return time.getAsLong();
        }, Evaluation::satisfied);

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(
                started, Long.MIN_VALUE + 1, Long.MIN_VALUE + 3), starts);
        assertEquals(List.of(4L, 2L), time.parkRequests);
        assertEquals(Long.MIN_VALUE + 3, success.completedNanos());
        assertEquals(Long.MIN_VALUE + 3, success.result());
    }

    @Test
    void stabilizesBeyondUpToAfterAcquisitionAtTheLastValidNanosecond() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(4, 10, 5), () -> {
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
            return satisfied(call == 2 ? "stable" : "boundary");
        });

        var success = assertInstanceOf(Satisfied.class, outcome);
        assertEquals(List.of(0L, 4L, 13L, 14L), starts);
        assertEquals(List.of(4L, 4L, 1L), time.parkRequests);
        assertEquals(14, success.completedNanos());
        assertEquals(4, success.number());
        assertEquals("boundary", success.result());
    }

    @Test
    void treatsARegularSatisfiedObservationCompletingAfterBoundaryAsFinal() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(6, 20, 10), () -> {
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
        assertEquals(11, success.completedNanos());
        assertEquals(2, success.number());
        assertEquals("late boundary", success.result());
    }

    @Test
    void losesStabilityImmediatelyOnTheFirstUnsatisfiedObservation() {
        var time = new FakeTime(0);
        var failingActual = new Object();
        var assertion = new AssertionError("lost");
        var calls = new int[1];

        WaitOutcome<Object> outcome = wait(time, config(5, 20, 15),
                () -> calls[0] == 0 ? new Object() : failingActual,
                actual -> calls[0]++ == 0
                        ? satisfied(new Object())
                        : assertionUnsatisfied("lost", assertion));

        var loss = assertInstanceOf(StabilityLoss.class, outcome);
        var unsatisfied = loss.attempt();
        assertEquals(0, loss.acquiredNanos());
        assertEquals(5, unsatisfied.completedNanos());
        assertEquals(2, loss.attempt().number());
        assertSame(failingActual, unsatisfied.actual());
        assertEquals("lost", unsatisfied.mismatch());
        assertSame(assertion, unsatisfied.assertionCause());
        assertEquals(List.of(5L), time.parkRequests);
    }

    @Test
    void uncontrolledStabilityObservationWinsAfterBothDeadlines() {
        var time = new FakeTime(0);
        var failure = new IllegalStateException("condition failed");
        var calls = new int[1];

        WaitOutcome<Object> outcome = wait(time, config(2, 3, 5),
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
                AfterObservation.class,
                uncontrolled.attempt());
        assertSame(failure, attempt.cause());
        assertEquals(CONDITION, attempt.origin());
        assertEquals(2, attempt.number());
    }

    private static WaitConfiguration config(long every, long upTo, long stableFor) {
        return new WaitConfiguration(every, upTo, stableFor);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure)
            throws E {
        throw (E) failure;
    }

    private static <S, R> WaitOutcome<R> wait(
            FakeTime time,
            WaitConfiguration config,
            Source<S> source,
            CheckedFunction<S, Evaluation<R>> condition) {
        return wait(time, config, time, source, condition);
    }

    private static <S, R> WaitOutcome<R> wait(
            FakeTime time,
            WaitConfiguration config,
            LongConsumer parker,
            Source<S> source,
            CheckedFunction<S, Evaluation<R>> condition) {
        return new WaitEngine(config, time, parker).waitFor(source,
                new RuntimeCondition<>(condition,
                        () -> "test condition", null));
    }
}

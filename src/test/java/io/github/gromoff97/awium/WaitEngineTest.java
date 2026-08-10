package io.github.gromoff97.awium;

import io.github.gromoff97.awium.engine.Attempt;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import io.github.gromoff97.awium.engine.*;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("removal")
class WaitEngineTest {

    @Test
    void outcomeVariantsAcceptOnlyCompatibleAttempts() {
        var satisfied = new Attempt.Satisfied<>(
                "actual", "result", 1, 123);
        var unsatisfied = new Attempt.Unsatisfied<String>(
                "actual", "not ready", null, 1, 123);
        var uncontrolled =
                new Attempt.Uncontrolled.BeforeObservation<String>(
                Attempt.Origin.SOURCE,
                new IllegalStateException(), 1, 123);

        assertAll(
                () -> assertEquals("result", new WaitOutcome.Success<>(
                        100, 110, 123, satisfied).attempt().result()),
                () -> assertSame(unsatisfied,
                        new WaitOutcome.TimeoutBetweenObservations<>(
                                100, 123, unsatisfied).attempt()),
                () -> assertSame(uncontrolled,
                        new WaitOutcome.Uncontrolled<>(uncontrolled).attempt()));
    }

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void startsAttemptOneImmediatelyAndZeroStabilityReturnsItsResult() {
        var time = new FakeTime(100);
        var starts = new ArrayList<Long>();
        var result = new Object();

        WaitOutcome<Object> outcome = wait(time, config(5, 20, 0), () -> {
            starts.add(time.nanoTime());
            return "actual";
        }, actual -> satisfied(result));

        var success = assertInstanceOf(WaitOutcome.Success.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, success.attempt());
        assertEquals(List.of(100L), starts);
        assertEquals(List.of(), time.parkRequests());
        assertEquals(100, success.startedNanos());
        assertEquals(100, success.acquiredNanos());
        assertEquals(100, success.completedNanos());
        assertEquals(1, success.completedAttempts());
        assertSame(result, satisfied.result());
    }

    @Test
    void measuresEveryFromObservationCompletion() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(5, 20, 0), () -> {
            starts.add(time.nanoTime());
            return "actual";
        }, actual -> {
            if (calls[0]++ == 0) {
                time.advanceNanos(3);
                return unsatisfied("not yet");
            }
            return satisfied("ready");
        });

        var success = assertInstanceOf(WaitOutcome.Success.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, success.attempt());
        assertEquals(List.of(0L, 8L), starts);
        assertEquals(List.of(5L), time.parkRequests());
        assertEquals("ready", satisfied.result());
    }

    @Test
    void startsALaterAttemptOneNanosecondBeforeTheAcquisitionDeadline() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(9, 10, 0), () -> {
            starts.add(time.nanoTime());
            return "actual";
        }, actual -> calls[0]++ == 0
                ? unsatisfied("not yet")
                : satisfied("ready"));

        var success = assertInstanceOf(WaitOutcome.Success.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, success.attempt());
        assertEquals(List.of(0L, 9L), starts);
        assertEquals(List.of(9L), time.parkRequests());
        assertEquals(9, success.completedNanos());
        assertEquals(2, success.completedAttempts());
        assertEquals("ready", satisfied.result());
    }

    @Test
    void acceptsALaterSuccessCompletingOneNanosecondBeforeTheDeadline() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(4, 10, 0), () -> {
            starts.add(time.nanoTime());
            return "actual";
        }, actual -> {
            if (calls[0]++ == 0) {
                return unsatisfied("not yet");
            }
            time.advanceNanos(5);
            return satisfied("ready");
        });

        var success = assertInstanceOf(WaitOutcome.Success.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, success.attempt());
        assertEquals(List.of(0L, 4L), starts);
        assertEquals(List.of(4L), time.parkRequests());
        assertEquals(9, success.completedNanos());
        assertEquals(2, success.completedAttempts());
        assertEquals("ready", satisfied.result());
    }

    @Test
    void timesOutWhileParkedWithoutStartingAtTheDeadline() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var assertion = new AssertionError("third mismatch");
        var calls = new int[1];

        WaitOutcome<Object> outcome = wait(time, config(4, 10, 0), () -> {
            starts.add(time.nanoTime());
            return new Object();
        }, actual -> {
            int call = ++calls[0];
            return call == 3
                    ? assertionUnsatisfied("mismatch " + call, assertion)
                    : unsatisfied("mismatch " + call);
        });

        var timeout = assertInstanceOf(
                WaitOutcome.TimeoutBetweenObservations.class, outcome);
        var unsatisfied = assertInstanceOf(
                Attempt.Unsatisfied.class, timeout.attempt());
        assertEquals(List.of(0L, 4L, 8L), starts);
        assertEquals(List.of(4L, 4L, 2L), time.parkRequests());
        assertEquals(3, timeout.completedAttempts());
        assertEquals(10, timeout.completedNanos());
        assertEquals(3, unsatisfied.number());
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
                WaitOutcome.LateUnsatisfiedTimeout.class, outcome);
        var unsatisfied = assertInstanceOf(
                Attempt.Unsatisfied.class, timeout.attempt());
        assertEquals(1, timeout.completedAttempts());
        assertEquals(10, timeout.completedNanos());
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
                WaitOutcome.LateSatisfiedTimeout.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, timeout.attempt());
        assertEquals(1, timeout.completedAttempts());
        assertEquals(11, timeout.completedNanos());
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
            starts.add(time.nanoTime());
            return "actual";
        }, value -> calls[0]++ == 0
                ? unsatisfied("not ready")
                : satisfied("ready"));

        assertInstanceOf(WaitOutcome.Success.class, outcome);
        assertEquals(List.of(0L, 5L), starts);
        assertEquals(List.of(5L, 3L, 3L, 2L), time.parkRequests());
    }

    @Test
    void usesSubtractionBasedDeadlinesAcrossNanoTimeWraparound() {
        long started = Long.MAX_VALUE - 2;
        var time = new FakeTime(started);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(4, 10, 0), () -> {
            starts.add(time.nanoTime());
            return "actual";
        }, value -> calls[0]++ == 0
                ? unsatisfied("not ready")
                : satisfied("ready"));

        assertInstanceOf(WaitOutcome.Success.class, outcome);
        assertEquals(List.of(started, Long.MIN_VALUE + 1), starts);
        assertEquals(List.of(4L), time.parkRequests());
        assertEquals(2, outcome.completedAttempts());
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
                WaitOutcome.Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                Attempt.Uncontrolled.BeforeObservation.class,
                uncontrolled.attempt());
        assertSame(failure, attempt.cause());
        assertEquals(Attempt.Origin.SOURCE, attempt.origin());
        assertEquals(1, attempt.number());
    }

    @Test
    void classifiesAnAcquisitionParkingFailureForTheNextAttempt() {
        var time = new FakeTime(0);
        var failure = new IllegalStateException("park failed");

        WaitOutcome<String> outcome = wait(time, config(5, 20, 0), nanos -> {
                    throw failure;
                }, () -> "actual",
                actual -> unsatisfied("not yet"));

        var uncontrolled = assertInstanceOf(
                WaitOutcome.Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                Attempt.Uncontrolled.BeforeObservation.class,
                uncontrolled.attempt());
        assertEquals(Attempt.Origin.WAITING, attempt.origin());
        assertEquals(2, attempt.number());
        assertSame(failure, attempt.cause());
    }

    @Test
    void classifiesAStabilityParkingFailureForTheNextAttempt() {
        var time = new FakeTime(0);
        var failure = new IllegalStateException("stability park failed");

        WaitOutcome<String> outcome = wait(time, config(5, 20, 10), nanos -> {
                    throw failure;
                }, () -> "actual", actual -> satisfied("ready"));

        var uncontrolled = assertInstanceOf(
                WaitOutcome.Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                Attempt.Uncontrolled.BeforeObservation.class,
                uncontrolled.attempt());
        assertEquals(Attempt.Origin.WAITING, attempt.origin());
        assertEquals(2, attempt.number());
        assertSame(failure, attempt.cause());
    }

    @Test
    void detectsAnInterruptRaisedWhileParkedForTheNextAttempt() {
        var time = new FakeTime(0);
        var parkCalls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(5, 20, 0), nanos -> {
                    parkCalls[0]++;
                    currentThread().interrupt();
                }, () -> "actual",
                actual -> unsatisfied("not yet"));

        var uncontrolled = assertInstanceOf(
                WaitOutcome.Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                Attempt.Uncontrolled.BeforeObservation.class,
                uncontrolled.attempt());
        assertEquals(Attempt.Origin.WAITING, attempt.origin());
        assertEquals(2, attempt.number());
        assertEquals(InterruptedException.class,
                attempt.cause().getClass());
        assertEquals(1, parkCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void detectsAnInterruptRaisedWhileParkedForStability() {
        var time = new FakeTime(0);
        var parkCalls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(5, 20, 10), nanos -> {
                    parkCalls[0]++;
                    currentThread().interrupt();
                }, () -> "actual", actual -> satisfied("ready"));

        var uncontrolled = assertInstanceOf(
                WaitOutcome.Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                Attempt.Uncontrolled.BeforeObservation.class,
                uncontrolled.attempt());
        assertEquals(Attempt.Origin.WAITING, attempt.origin());
        assertEquals(2, attempt.number());
        assertEquals(InterruptedException.class,
                attempt.cause().getClass());
        assertEquals(1, parkCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void fatalParkingSignalsEscapeUnchanged() {
        var time = new FakeTime(0);
        var fatal = new ThrowableFixtures.Fatal("fatal park");

        ThrowableFixtures.Fatal thrown = assertThrows(
                ThrowableFixtures.Fatal.class,
                () -> wait(time, config(5, 20, 0), nanos -> {
                    throw fatal;
                }, () -> "actual",
                        actual -> unsatisfied("not yet")));

        assertSame(fatal, thrown);
    }

    @Test
    void detectsWaitingInterruptionBeforeTheFirstObservation() {
        var time = new FakeTime(0);
        var sourceCalls = new int[1];
        currentThread().interrupt();

        WaitOutcome<Object> outcome = wait(time, config(3, 10, 0), () -> {
            sourceCalls[0]++;
            return new Object();
        }, Evaluation::satisfied);

        var uncontrolled = assertInstanceOf(
                WaitOutcome.Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                Attempt.Uncontrolled.BeforeObservation.class,
                uncontrolled.attempt());
        assertEquals(0, sourceCalls[0]);
        assertEquals(Attempt.Origin.WAITING, attempt.origin());
        assertEquals(1, attempt.number());
        assertEquals(InterruptedException.class,
                attempt.cause().getClass());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void fatalSignalsEscapeEvenAfterTheAcquisitionDeadline() {
        var time = new FakeTime(0);
        var fatal = new ThrowableFixtures.Fatal("fatal");

        ThrowableFixtures.Fatal thrown = assertThrows(
                ThrowableFixtures.Fatal.class,
                () -> wait(time, config(3, 10, 0), () -> {
                    time.advanceNanos(11);
                    throw fatal;
                }, Evaluation::satisfied));

        assertSame(fatal, thrown);
    }

    @Test
    void observesTheStabilityBoundaryAndReturnsItsChangingResult() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var results = List.of("acquired", "second", "third", "boundary");
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(5, 20, 12), () -> {
            starts.add(time.nanoTime());
            return "actual";
        }, actual -> satisfied(results.get(calls[0]++)));

        var success = assertInstanceOf(WaitOutcome.Success.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, success.attempt());
        assertEquals(List.of(0L, 5L, 10L, 12L), starts);
        assertEquals(List.of(5L, 5L, 2L), time.parkRequests());
        assertEquals(0, success.acquiredNanos());
        assertEquals(12, success.completedNanos());
        assertEquals(4, success.completedAttempts());
        assertEquals("boundary", satisfied.result());
    }

    @ParameterizedTest
    @MethodSource("stabilityDurationRelations")
    void observesTheExactStabilityBoundaryForEveryDurationRelation(
            long every,
            long stableFor,
            List<Long> expectedStarts,
            List<Long> expectedParks) {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();

        WaitOutcome<Long> outcome = wait(
                time, config(every, 20, stableFor), () -> {
                    starts.add(time.nanoTime());
                    return time.nanoTime();
                }, Evaluation::satisfied);

        var success = assertInstanceOf(WaitOutcome.Success.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, success.attempt());
        assertEquals(expectedStarts, starts);
        assertEquals(expectedParks, time.parkRequests());
        assertEquals(stableFor, success.completedNanos());
        assertEquals(expectedStarts.size(), success.completedAttempts());
        assertEquals(stableFor, satisfied.result());
    }

    @Test
    void rechecksTheStabilityTargetAfterPrematureAndSpuriousWakeups() {
        var time = new FakeTime(0);
        time.wakeAfter(2);
        time.wakeAfter(0);
        time.wakeAfter(1);
        var starts = new ArrayList<Long>();

        WaitOutcome<Long> outcome = wait(time, config(5, 20, 5), () -> {
            starts.add(time.nanoTime());
            return time.nanoTime();
        }, Evaluation::satisfied);

        var success = assertInstanceOf(WaitOutcome.Success.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, success.attempt());
        assertEquals(List.of(0L, 5L), starts);
        assertEquals(List.of(5L, 3L, 3L, 2L), time.parkRequests());
        assertEquals(5L, satisfied.result());
    }

    @Test
    void usesOverflowSafeDeadlinesDuringStability() {
        long started = Long.MAX_VALUE - 2;
        var time = new FakeTime(started);
        var starts = new ArrayList<Long>();

        WaitOutcome<Long> outcome = wait(time, config(4, 20, 6), () -> {
            starts.add(time.nanoTime());
            return time.nanoTime();
        }, Evaluation::satisfied);

        var success = assertInstanceOf(WaitOutcome.Success.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, success.attempt());
        assertEquals(List.of(
                started, Long.MIN_VALUE + 1, Long.MIN_VALUE + 3), starts);
        assertEquals(List.of(4L, 2L), time.parkRequests());
        assertEquals(Long.MIN_VALUE + 3, success.completedNanos());
        assertEquals(Long.MIN_VALUE + 3, satisfied.result());
    }

    @Test
    void stabilizesBeyondUpToAfterAcquisitionAtTheLastValidNanosecond() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(4, 10, 5), () -> {
            starts.add(time.nanoTime());
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

        var success = assertInstanceOf(WaitOutcome.Success.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, success.attempt());
        assertEquals(List.of(0L, 4L, 13L, 14L), starts);
        assertEquals(List.of(4L, 4L, 1L), time.parkRequests());
        assertEquals(9, success.acquiredNanos());
        assertEquals(14, success.completedNanos());
        assertEquals(4, success.completedAttempts());
        assertEquals("boundary", satisfied.result());
    }

    @Test
    void treatsARegularSatisfiedObservationCompletingAfterBoundaryAsFinal() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();
        var calls = new int[1];

        WaitOutcome<String> outcome = wait(time, config(6, 20, 10), () -> {
            starts.add(time.nanoTime());
            return "actual";
        }, actual -> {
            if (calls[0]++ == 0) {
                return satisfied("acquired");
            }
            time.advanceNanos(5);
            return satisfied("late boundary");
        });

        var success = assertInstanceOf(WaitOutcome.Success.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, success.attempt());
        assertEquals(List.of(0L, 6L), starts);
        assertEquals(List.of(6L), time.parkRequests());
        assertEquals(11, success.completedNanos());
        assertEquals(2, success.completedAttempts());
        assertEquals("late boundary", satisfied.result());
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

        var loss = assertInstanceOf(WaitOutcome.StabilityLoss.class, outcome);
        var unsatisfied = assertInstanceOf(
                Attempt.Unsatisfied.class, loss.attempt());
        assertEquals(0, loss.acquiredNanos());
        assertEquals(5, loss.completedNanos());
        assertEquals(2, loss.completedAttempts());
        assertSame(failingActual, unsatisfied.actual());
        assertEquals("lost", unsatisfied.mismatch());
        assertSame(assertion, unsatisfied.assertionCause());
        assertEquals(List.of(5L), time.parkRequests());
    }

    @Test
    void ignoresTheAcquisitionDeadlineAfterFirstSuccess() {
        var time = new FakeTime(0);
        var starts = new ArrayList<Long>();

        WaitOutcome<Long> outcome = wait(time, config(2, 3, 8), () -> {
            starts.add(time.nanoTime());
            return time.nanoTime();
        }, Evaluation::satisfied);

        var success = assertInstanceOf(WaitOutcome.Success.class, outcome);
        var satisfied = assertInstanceOf(
                Attempt.Satisfied.class, success.attempt());
        assertEquals(List.of(0L, 2L, 4L, 6L, 8L), starts);
        assertEquals(8L, satisfied.result());
        assertEquals(5, success.completedAttempts());
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
                WaitOutcome.Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(
                Attempt.Uncontrolled.AfterObservation.class,
                uncontrolled.attempt());
        assertSame(failure, attempt.cause());
        assertEquals(Attempt.Origin.CONDITION, attempt.origin());
        assertEquals(2, attempt.number());
    }

    private static Stream<Arguments> stabilityDurationRelations() {
        return Stream.of(
                Arguments.of(5L, 3L, List.of(0L, 3L), List.of(3L)),
                Arguments.of(5L, 5L, List.of(0L, 5L), List.of(5L)),
                Arguments.of(4L, 12L,
                        List.of(0L, 4L, 8L, 12L),
                        List.of(4L, 4L, 4L)));
    }

    private static WaitConfiguration config(long every, long upTo, long stableFor) {
        return new WaitConfiguration(every, upTo, stableFor);
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

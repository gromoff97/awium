package io.github.gromoff97.awium;

import io.github.gromoff97.awium.engine.Attempt;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.engine.Attempt.*;
import static io.github.gromoff97.awium.engine.WaitOutcome.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import io.github.gromoff97.awium.engine.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void exposesOneValidatedWaitOutcomeRecord() {
        Attempt<String> attempt = satisfied(
                "actual", "result", 1, 123);
        WaitOutcome<String> outcome = success(100, 110, 123,
                attempt);

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(123, outcome.attempt().completedNanos());
        assertEquals("result", outcome.result());
        assertEquals(1, outcome.completedAttempts());
    }

    @Test
    void rejectsMissingWaitOutcomeState() {
        Attempt<String> attempt = satisfied(
                "actual", "result", 1, 123);

        assertThrows(NullPointerException.class,
                () -> new WaitOutcome<>(null, 100, 110, 123, attempt));
        assertThrows(NullPointerException.class,
                () -> success(100, 110, 123, null));
    }

    @Test
    void rejectsWaitOutcomeKindsThatDoNotMatchTheAttemptStatus() {
        Attempt<String> satisfied = satisfied(
                "actual", "result", 1, 123);
        Attempt<String> unsatisfied = unsatisfied(
                "actual", "not ready", null, 1, 123);
        Attempt<String> uncontrolled = uncontrolled(
                Attempt.Origin.SOURCE, false, null,
                new IllegalStateException(), 1, 123);

        assertThrows(IllegalArgumentException.class,
                () -> new WaitOutcome<>(WaitOutcome.Kind.SUCCESS,
                        100, 110, 123, unsatisfied));
        assertThrows(IllegalArgumentException.class,
                () -> new WaitOutcome<>(
                        WaitOutcome.Kind.TIMEOUT_BETWEEN_OBSERVATIONS,
                        100, 0, 123, satisfied));
        assertThrows(IllegalArgumentException.class,
                () -> new WaitOutcome<>(
                        WaitOutcome.Kind.LATE_UNSATISFIED_TIMEOUT,
                        100, 0, 123, satisfied));
        assertThrows(IllegalArgumentException.class,
                () -> new WaitOutcome<>(
                        WaitOutcome.Kind.LATE_SATISFIED_TIMEOUT,
                        100, 0, 123, unsatisfied));
        assertThrows(IllegalArgumentException.class,
                () -> new WaitOutcome<>(WaitOutcome.Kind.STABILITY_LOSS,
                        100, 110, 123, satisfied));
        assertThrows(IllegalArgumentException.class,
                () -> new WaitOutcome<>(WaitOutcome.Kind.UNCONTROLLED,
                        0, 0, 0, satisfied));
        assertThrows(IllegalArgumentException.class,
                () -> new WaitOutcome<>(WaitOutcome.Kind.SUCCESS,
                        100, 110, 123, uncontrolled));
    }

    @Test
    void waitOutcomeFactoriesCreateCompatibleKinds() {
        Attempt<String> satisfied = satisfied(
                "actual", "result", 1, 123);
        Attempt<String> unsatisfied = unsatisfied(
                "actual", "not ready", null, 1, 123);
        Attempt<String> uncontrolled = uncontrolled(
                Attempt.Origin.SOURCE, false, null,
                new IllegalStateException(), 1, 123);

        assertEquals(WaitOutcome.Kind.SUCCESS,
                success(100, 110, 123, satisfied).kind());
        assertEquals(WaitOutcome.Kind.TIMEOUT_BETWEEN_OBSERVATIONS,
                timeoutBetween(100, 123, unsatisfied).kind());
        assertEquals(WaitOutcome.Kind.LATE_UNSATISFIED_TIMEOUT,
                lateUnsatisfied(100, 123, unsatisfied).kind());
        assertEquals(WaitOutcome.Kind.LATE_SATISFIED_TIMEOUT,
                lateSatisfied(100, 123, satisfied).kind());
        assertEquals(WaitOutcome.Kind.STABILITY_LOSS,
                stabilityLoss(100, 110, 123, unsatisfied).kind());
        assertEquals(WaitOutcome.Kind.UNCONTROLLED,
                uncontrolled(uncontrolled).kind());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(List.of(100L), starts);
        assertEquals(List.of(), time.parkRequests());
        assertEquals(100, outcome.startedNanos());
        assertEquals(100, outcome.acquiredNanos());
        assertEquals(100, outcome.completedNanos());
        assertEquals(1, outcome.completedAttempts());
        assertSame(result, outcome.result());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(List.of(0L, 8L), starts);
        assertEquals(List.of(5L), time.parkRequests());
        assertEquals("ready", outcome.result());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(List.of(0L, 9L), starts);
        assertEquals(List.of(9L), time.parkRequests());
        assertEquals(9, outcome.completedNanos());
        assertEquals(2, outcome.completedAttempts());
        assertEquals("ready", outcome.result());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(List.of(0L, 4L), starts);
        assertEquals(List.of(4L), time.parkRequests());
        assertEquals(9, outcome.completedNanos());
        assertEquals(2, outcome.completedAttempts());
        assertEquals("ready", outcome.result());
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

        assertEquals(WaitOutcome.Kind.TIMEOUT_BETWEEN_OBSERVATIONS,
                outcome.kind());
        assertEquals(List.of(0L, 4L, 8L), starts);
        assertEquals(List.of(4L, 4L, 2L), time.parkRequests());
        assertEquals(3, outcome.completedAttempts());
        assertEquals(10, outcome.completedNanos());
        assertEquals(3, outcome.attempt().number());
        assertEquals(8, outcome.attempt().completedNanos());
        assertEquals("mismatch 3", outcome.attempt().mismatch());
        assertSame(assertion, outcome.attempt().assertionCause());
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

        assertEquals(WaitOutcome.Kind.LATE_UNSATISFIED_TIMEOUT, outcome.kind());
        assertEquals(1, outcome.completedAttempts());
        assertEquals(10, outcome.completedNanos());
        assertSame(actual, outcome.attempt().actual());
        assertEquals("late", outcome.attempt().mismatch());
        assertSame(assertion, outcome.attempt().assertionCause());
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

        assertEquals(WaitOutcome.Kind.LATE_SATISFIED_TIMEOUT, outcome.kind());
        assertEquals(1, outcome.completedAttempts());
        assertEquals(11, outcome.completedNanos());
        assertSame(actual, outcome.attempt().actual());
        assertSame(result, outcome.attempt().result());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
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

        assertEquals(WaitOutcome.Kind.UNCONTROLLED, outcome.kind());
        assertSame(failure, outcome.attempt().cause());
        assertEquals(Attempt.Origin.SOURCE,
                outcome.attempt().origin());
        assertEquals(1, outcome.attempt().number());
    }

    @Test
    void classifiesAnAcquisitionParkingFailureForTheNextAttempt() {
        var time = new FakeTime(0);
        var failure = new IllegalStateException("park failed");

        WaitOutcome<String> outcome = wait(time, config(5, 20, 0), nanos -> {
                    throw failure;
                }, () -> "actual",
                actual -> unsatisfied("not yet"));

        assertEquals(WaitOutcome.Kind.UNCONTROLLED, outcome.kind());
        assertEquals(Attempt.Origin.WAITING,
                outcome.attempt().origin());
        assertEquals(2, outcome.attempt().number());
        assertSame(failure, outcome.attempt().cause());
    }

    @Test
    void classifiesAStabilityParkingFailureForTheNextAttempt() {
        var time = new FakeTime(0);
        var failure = new IllegalStateException("stability park failed");

        WaitOutcome<String> outcome = wait(time, config(5, 20, 10), nanos -> {
                    throw failure;
                }, () -> "actual", actual -> satisfied("ready"));

        assertEquals(WaitOutcome.Kind.UNCONTROLLED, outcome.kind());
        assertEquals(Attempt.Origin.WAITING,
                outcome.attempt().origin());
        assertEquals(2, outcome.attempt().number());
        assertSame(failure, outcome.attempt().cause());
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

        assertEquals(WaitOutcome.Kind.UNCONTROLLED, outcome.kind());
        assertEquals(Attempt.Origin.WAITING,
                outcome.attempt().origin());
        assertEquals(2, outcome.attempt().number());
        assertEquals(InterruptedException.class,
                outcome.attempt().cause().getClass());
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

        assertEquals(WaitOutcome.Kind.UNCONTROLLED, outcome.kind());
        assertEquals(Attempt.Origin.WAITING,
                outcome.attempt().origin());
        assertEquals(2, outcome.attempt().number());
        assertEquals(InterruptedException.class,
                outcome.attempt().cause().getClass());
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

        assertEquals(WaitOutcome.Kind.UNCONTROLLED, outcome.kind());
        assertEquals(0, sourceCalls[0]);
        assertEquals(Attempt.Origin.WAITING,
                outcome.attempt().origin());
        assertEquals(1, outcome.attempt().number());
        assertEquals(InterruptedException.class,
                outcome.attempt().cause().getClass());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(List.of(0L, 5L, 10L, 12L), starts);
        assertEquals(List.of(5L, 5L, 2L), time.parkRequests());
        assertEquals(0, outcome.acquiredNanos());
        assertEquals(12, outcome.completedNanos());
        assertEquals(4, outcome.completedAttempts());
        assertEquals("boundary", outcome.result());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(expectedStarts, starts);
        assertEquals(expectedParks, time.parkRequests());
        assertEquals(stableFor, outcome.completedNanos());
        assertEquals(expectedStarts.size(), outcome.completedAttempts());
        assertEquals(stableFor, outcome.result());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(List.of(0L, 5L), starts);
        assertEquals(List.of(5L, 3L, 3L, 2L), time.parkRequests());
        assertEquals(5L, outcome.result());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(List.of(
                started, Long.MIN_VALUE + 1, Long.MIN_VALUE + 3), starts);
        assertEquals(List.of(4L, 2L), time.parkRequests());
        assertEquals(Long.MIN_VALUE + 3, outcome.completedNanos());
        assertEquals(Long.MIN_VALUE + 3, outcome.result());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(List.of(0L, 4L, 13L, 14L), starts);
        assertEquals(List.of(4L, 4L, 1L), time.parkRequests());
        assertEquals(9, outcome.acquiredNanos());
        assertEquals(14, outcome.completedNanos());
        assertEquals(4, outcome.completedAttempts());
        assertEquals("boundary", outcome.result());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(List.of(0L, 6L), starts);
        assertEquals(List.of(6L), time.parkRequests());
        assertEquals(11, outcome.completedNanos());
        assertEquals(2, outcome.completedAttempts());
        assertEquals("late boundary", outcome.result());
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

        assertEquals(WaitOutcome.Kind.STABILITY_LOSS, outcome.kind());
        assertEquals(0, outcome.acquiredNanos());
        assertEquals(5, outcome.completedNanos());
        assertEquals(2, outcome.completedAttempts());
        assertSame(failingActual, outcome.attempt().actual());
        assertEquals("lost", outcome.attempt().mismatch());
        assertSame(assertion, outcome.attempt().assertionCause());
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

        assertEquals(WaitOutcome.Kind.SUCCESS, outcome.kind());
        assertEquals(List.of(0L, 2L, 4L, 6L, 8L), starts);
        assertEquals(8L, outcome.result());
        assertEquals(5, outcome.completedAttempts());
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

        assertEquals(WaitOutcome.Kind.UNCONTROLLED, outcome.kind());
        assertSame(failure, outcome.attempt().cause());
        assertEquals(Attempt.Origin.CONDITION,
                outcome.attempt().origin());
        assertEquals(2, outcome.attempt().number());
        assertFalse(outcome.attempt().status() == Attempt.Status.SATISFIED);
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

package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.support.FakeTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.withTime;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.Attempt.Operation.SOURCE;
import static io.github.gromoff97.awium.result.Attempt.Operation.WAITING;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.SOURCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static io.github.gromoff97.awium.support.AwaitAssertions.attempts;
import static io.github.gromoff97.awium.support.AwaitAssertions.failure;
import static io.github.gromoff97.awium.support.AwaitAssertions.observation;
import static io.github.gromoff97.awium.support.AwaitAssertions.uncontrolled;
import static io.github.gromoff97.awium.support.TestConditions.check;
import static io.github.gromoff97.awium.support.TestConditions.checkPerWait;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.lang.Thread.sleep;
import static java.time.Duration.ofNanos;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@SuppressWarnings("removal")
class ObservationTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @ParameterizedTest
    @MethodSource("waitingInterruptions")
    void interruptionWhileWaitingRetainsItsOriginalCompletionTime(
            long persistence, AttemptResult<?> state, Runnable beforePark, long secondTick, int expectedParks) {
        int[] sources = {0};
        int[] parks = {0};
        long[] now = {0};
        LongSupplier clock = mock();
        var waiting = withTime(await(() -> { sources[0]++; return "ready"; }), clock, nanos -> {
            parks[0]++;
            now[0] = 1;
            currentThread().interrupt();
        }).every(ofNanos(5)).upTo(ofNanos(20)).persisting(ofNanos(persistence));
        var result = diagnostic(waiting).until(check("state", actual -> {
            doReturn(0L).doAnswer(ignored -> {
                now[0] = secondTick;
                beforePark.run();
                return now[0];
            }).doAnswer(ignored -> ++now[0]).when(clock).getAsLong();
            return state;
        }));
        Failed<?, ?> failure = assertInstanceOf(Failed.class, result);
        var attempt = attempts(failure).getLast();
        var outcome = uncontrolled(WAITING, attempt);
        assertInstanceOf(FailedBeforeObservation.class, attempt);
        assertEquals(secondTick + 1, now[0]);
        assertEquals(2, attempts(failure).size());
        assertEquals(1, sources[0]);
        assertEquals(expectedParks, parks[0]);
        assertInstanceOf(InterruptedException.class, outcome.cause());
        assertTrue(currentThread().isInterrupted());
    }

    private static List<Arguments> waitingInterruptions() {
        Runnable interrupt = () -> currentThread().interrupt();
        Runnable keepRunning = () -> {};
        return List.of(
                argumentSet("before park / acquisition", 0L, unsatisfied("waiting"), interrupt, 1L, 0),
                argumentSet("before park / persistence", 5L, satisfied(null), interrupt, 1L, 0),
                argumentSet("after park / acquisition", 0L, unsatisfied("waiting"), keepRunning, 1L, 1),
                argumentSet("after park / persistence", 5L, satisfied(null), keepRunning, 1L, 1),
                argumentSet("after interval / acquisition", 0L, unsatisfied("waiting"), interrupt, 5L, 0),
                argumentSet("after interval / persistence", 5L, satisfied(null), interrupt, 5L, 0));
    }

    @Test
    void recordsCompletionRelativeToExecutionStart() {
        var time = new FakeTime(100);
        var actual = new Object();
        var result = new Object();

        var execution = new ConditionRun<>(check("test condition", (Object value) -> {
            time.advanceNanos(3);
            return AttemptResult.satisfied(result);
        }));
        WaitCompletion terminal = new WaitEngine(withTime(config(1, 20, 0), time, time), () -> {
            time.advanceNanos(2);
            return actual;
        }, execution).waitFor();

        var step = terminal.step();
        var observation = step.attempt();
        assertInstanceOf(SatisfiedObservation.class, observation);
        assertEquals(ACQUISITION, step.phase());
        assertEquals(5, step.completedAfterNanos());
        assertSame(actual, observation(observation));
        assertSame(result, execution.finish(terminal, config(1, 20, 0)).orThrow());
    }

    @Test
    void satisfiedAttemptsRetainTheirObservation() {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> satisfied(value));

        assertSame(actual, observation(attempt));
        assertInstanceOf(SatisfiedObservation.class, attempt);
    }

    @ParameterizedTest
    @MethodSource("mismatches")
    void unsatisfiedAttemptsRetainTheirObservationAndDiagnosticDetails(AttemptResult.Unsatisfied<?> original) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> original);
        var status = assertInstanceOf(UnsatisfiedObservation.class, attempt);

        assertSame(actual, observation(attempt));
        assertEquals(original.mismatch(), status.mismatch());
        assertSame(original.cause(), attempt.cause());
    }

    private static Stream<Named<AttemptResult.Unsatisfied<?>>> mismatches() {
        return Stream.of(
                named("returned mismatch with cause", unsatisfied("not ready", new AssertionError("assertion"))),
                named("plain mismatch", unsatisfied("not ready")),
                named("assertion mismatch", unsatisfied("assertion did not pass", new AssertionError("failed"))));
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void classifiesEverySourceThrowableBeforeObservation(Throwable failure) {
        var time = new FakeTime(0);
        var waiting = withTime(await((Callable<Object>) () -> rethrow(failure)), time, time);
        var check = check("must not run", ObservationTest::failIfCalled);
        var result = diagnostic(waiting).until(check);
        var failed = failure(SOURCE_FAILED, result);
        assertSame(failure, failed.cause());
        assertSame(failure, assertThrows(AwaitExecutionException.class, () -> waiting.until(check)).getCause());
        assertFalse(currentThread().isInterrupted());

        var outcome = uncontrolled(SOURCE, attempts(result).getFirst());
        assertSame(failure, outcome.cause());
        assertSame(check, result.conditions().getFirst().condition());
        assertInstanceOf(FailedBeforeObservation.class, attempts(result).getFirst());

    }

    @ParameterizedTest
    @MethodSource("throwables")
    void classifiesEveryConditionThrowableAfterObservation(Throwable failure) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> { throw failure; });

        var observation = uncontrolled(CONDITION, attempt);
        assertSame(failure, observation.cause());
        assertSame(actual, observation(attempt));
        assertFalse(currentThread().isInterrupted());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}, {1}")
    @MethodSource("conditionFailures")
    void ordinaryAwaitPreservesCallbackAndFactoryFailures(Throwable cause, Condition<String, String> condition) {
        var waiting = await(() -> "actual");
        assertSame(cause, assertThrows(AwaitExecutionException.class, () -> waiting.until(condition)).getCause());
        assertFalse(currentThread().isInterrupted());
    }

    private static Stream<Arguments> conditionFailures() {
        return throwables().flatMap(cause -> Stream.of(
                arguments(named(cause.getClass().getSimpleName(), cause),
                        named("callback", check("callback", (String actual) -> { throw cause; }))),
                arguments(named(cause.getClass().getSimpleName(), cause),
                        named("factory", checkPerWait("factory", () -> rethrow(cause))))));
    }

    @Test
    void diagnosesNullEvaluationOnceAtConditionOrigin() {
        var actual = new Object();
        var conditionCalls = new int[1];
        var attempt = attempt(() -> actual, value -> {
            conditionCalls[0]++;
            return null;
        });

        var observation = uncontrolled(CONDITION, attempt);
        assertSame(actual, observation(attempt));
        assertEquals(NullPointerException.class, observation.cause().getClass());
        assertEquals("condition returned null AttemptResult", observation.cause().getMessage());
        assertEquals(1, conditionCalls[0]);
    }

    @Test
    void sourceFlagAfterNormalReturnRetainsObservationAndSkipsCondition() {
        var actual = new Object();
        var attempt = attempt(() -> {
            currentThread().interrupt();
            return actual;
        }, ObservationTest::failIfCalled);

        var outcome = uncontrolled(SOURCE, attempt);
        assertSame(actual, observation(attempt));
        assertEquals("caller thread interrupt flag was set", outcome.cause().getMessage());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("completionInterruptions")
    void completionClockInterruptionStopsNormalResultsWithoutReplacingConditionFailure(
            boolean returnResult, long persistence, int interruptedAttempt,
            AttemptResult<?> state, Consumer<Throwable> assertCause) {
        var actual = new Object();
        var time = new FakeTime(0);
        int[] evaluations = {0};
        var waiting = withTime(await(() -> actual), () -> {
            if (evaluations[0] == interruptedAttempt) currentThread().interrupt();
            return time.getAsLong();
        }, time).every(ofNanos(1)).upTo(ofNanos(10)).persisting(ofNanos(persistence));
        var check = check("completion boundary", value ->
                ++evaluations[0] == interruptedAttempt ? state : satisfied(actual));
        AwaitFailure failure;
        if (returnResult) {
            var result = diagnostic(waiting).until(check);
            failure = failure(result);
            assertEquals(interruptedAttempt, attempts(result).size());
            uncontrolled(CONDITION, attempts(result).getLast());
        } else {
            failure = assertThrows(AwaitExecutionException.class, () -> waiting.until(check)).failure();
        }
        assertEquals(INTERRUPTED, failure.reason());
        assertCause.accept(failure.cause());
        assertEquals(interruptedAttempt, evaluations[0]);
        assertTrue(currentThread().isInterrupted());
    }

    private static Stream<Arguments> completionInterruptions() {
        Consumer<Throwable> interruption = cause -> assertInstanceOf(InterruptedException.class, cause);
        Consumer<Throwable> missingResult = cause -> {
            assertInstanceOf(NullPointerException.class, cause);
            assertEquals("condition returned null AttemptResult", cause.getMessage());
        };
        return Stream.of(false, true).flatMap(returnResult -> Stream.of(Map.entry(0L, 1), Map.entry(2L, 2)).flatMap(timing -> {
            String label = (returnResult ? "tryAwait" : "await") + ", persistence=" + timing.getKey() + " / ";
            return Stream.of(
                    argumentSet(label + "satisfied", returnResult, timing.getKey(), timing.getValue(), satisfied(null), interruption),
                    argumentSet(label + "unsatisfied", returnResult, timing.getKey(), timing.getValue(), unsatisfied("not ready"), interruption),
                    argumentSet(label + "null", returnResult, timing.getKey(), timing.getValue(), null, missingResult));
        }));
    }

    @Test
    void conditionFlagAfterNormalReturnWinsBeforeInterpretation() {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return satisfied(value);
        });

        var observation = uncontrolled(CONDITION, attempt);
        assertSame(actual, observation(attempt));
        assertEquals(InterruptedException.class, observation.cause().getClass());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void abruptSourceThrowableWinsOverCallbackSetFlag(Throwable failure) {
        var attempt = attempt(() -> {
            currentThread().interrupt();
            return rethrow(failure);
        }, ObservationTest::failIfCalled);

        var outcome = uncontrolled(SOURCE, attempt);
        assertSame(failure, outcome.cause());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void abruptConditionThrowableWinsOverCallbackSetFlag(Throwable failure) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            throw failure;
        });

        var observation = uncontrolled(CONDITION, attempt);
        assertSame(failure, observation.cause());
        assertSame(actual, observation(attempt));
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void returnedMismatchCauseSurvivesTheInterruptFlag(Throwable failure) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return unsatisfied("not ready", failure);
        });

        var outcome = uncontrolled(CONDITION, attempt);
        assertSame(failure, outcome.cause());
        assertSame(actual, observation(attempt));
        assertTrue(currentThread().isInterrupted());
    }

    private static Stream<Throwable> throwables() {
        return Stream.of(new Throwable("direct"), new Exception("checked"), new InterruptedException("interrupted"),
                new RuntimeException("runtime"),
                new AssertionError("assertion"), new LinkageError("linkage"),
                new InternalError("fatal"), new ThreadDeath());
    }

    private static AttemptResult<Object> failIfCalled(Object ignored) {
        throw new AssertionError("condition must not be called");
    }

    private static Attempt<?> attempt(Callable<Object> source, AwaitAttempt<Object, Object> condition) {
        var time = new FakeTime(0);
        return new WaitEngine(withTime(config(1, 2, 0), time, time), source, new ConditionRun<>(check("test condition", actual -> {
            AttemptResult<?> evaluation = condition.probe(actual);
            time.advanceNanos(2);
            return evaluation;
        }))).waitFor().step().attempt();
    }

    @Test
    void interruptedSleepBecomesAnOrdinaryFailureWithoutRestoringTheFlag() {
        var result = tryAwait(() -> "ready").until(check("sleep", value -> {
            currentThread().interrupt();
            sleep(0);
            return satisfied(value);
        }));
        var failure = failure(CONDITION_FAILED, result);
        assertInstanceOf(InterruptedException.class, failure.cause());
        assertFalse(currentThread().isInterrupted());
    }
}

package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.internal.SequenceExecution;
import io.github.gromoff97.awium.internal.WaitCompletion;
import io.github.gromoff97.awium.internal.WaitEngine;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.AwaitTestAccess.uncontrolled;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.ConditionSupport.bind;
import static io.github.gromoff97.awium.ConditionSupport.condition;
import static io.github.gromoff97.awium.ConditionSupport.preserving;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.internal.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.Attempt.Operation.SOURCE;
import static io.github.gromoff97.awium.result.Attempt.Operation.WAITING;
import static io.github.gromoff97.awium.result.Attempt.Satisfied;
import static io.github.gromoff97.awium.result.Attempt.Unsatisfied;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.SOURCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static java.util.Collections.nCopies;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            long persistence, ConditionResult state, Runnable beforePark, long secondTick, int expectedParks) {
        int[] sources = {0};
        int[] parks = {0};
        long[] now = {0};
        LongSupplier clock = mock();
        var waiting = await(() -> { sources[0]++; return "ready"; }).usingTime(() -> {
            interrupted();
            return clock.getAsLong();
        }, nanos -> {
            parks[0]++;
            now[0] = 1;
            currentThread().interrupt();
        }).every(ofNanos(5)).upTo(ofNanos(20)).persisting(ofNanos(persistence));
        var result = diagnostic(waiting).until(condition("state", actual -> {
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
                argumentSet("before park / persistence", 5L, satisfied(), interrupt, 1L, 0),
                argumentSet("after park / acquisition", 0L, unsatisfied("waiting"), keepRunning, 1L, 1),
                argumentSet("after park / persistence", 5L, satisfied(), keepRunning, 1L, 1),
                argumentSet("after interval / acquisition", 0L, unsatisfied("waiting"), interrupt, 5L, 0),
                argumentSet("after interval / persistence", 5L, satisfied(), interrupt, 5L, 0));
    }

    @Test
    void recordsCompletionRelativeToExecutionStart() {
        var time = new FakeTime(100);
        var actual = new Object();
        var result = new Object();

        var execution = new SequenceExecution<>(bind(condition("test condition", null, (Object value) -> {
            time.advanceNanos(3);
            return AttemptResult.satisfied(result);
        })));
        WaitCompletion terminal = new WaitEngine<>(config(1, 20, 0).withTime(time, time), () -> {
            time.advanceNanos(2);
            return actual;
        }, execution).waitFor();

        var step = terminal.step();
        var observation = step.attempt();
        assertInstanceOf(Satisfied.class, observation);
        assertEquals(ACQUISITION, step.phase());
        assertEquals(5, step.completedAfterNanos());
        assertSame(actual, observation(observation));
        assertSame(result, execution.values().getFirst());
    }

    @Test
    void satisfiedAttemptsRetainTheirObservation() {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> satisfied());

        assertSame(actual, observation(attempt));
        assertInstanceOf(Satisfied.class, attempt);
    }

    @ParameterizedTest
    @MethodSource("mismatches")
    void unsatisfiedAttemptsRetainTheirObservationAndDiagnosticDetails(ConditionResult.Unsatisfied original) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> original);
        var status = assertInstanceOf(Unsatisfied.class, attempt);

        assertSame(actual, observation(attempt));
        assertEquals(original.mismatch(), status.mismatch());
        assertSame(original.cause(), attempt.cause());
    }

    private static Stream<Named<ConditionResult.Unsatisfied>> mismatches() {
        return Stream.of(
                named("returned mismatch with cause", unsatisfied("not ready", new AssertionError("assertion"))),
                named("plain mismatch", unsatisfied("not ready")),
                named("assertion mismatch", unsatisfied("assertion did not pass", new AssertionError("failed"))));
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void classifiesEverySourceThrowableBeforeObservation(Throwable failure) {
        var time = new FakeTime(0);
        var waiting = await((Callable<Object>) () -> rethrow(failure)).usingTime(time, time);
        var check = condition("must not run", ObservationTest::failIfCalled);
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

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("ordinaryConditionFailures")
    void ordinaryAwaitRetainsConditionThrowables(Throwable cause, PreservingCondition<String> check) {
        var time = new FakeTime(0);
        var waiting = await(() -> "actual").usingTime(time, time).every(ofNanos(1));

        assertSame(cause, assertThrows(AwaitExecutionException.class, () -> waiting.until(check)).getCause());
        assertFalse(currentThread().isInterrupted());
    }

    @ParameterizedTest(name = "{0}, {1}, {2}")
    @MethodSource("composedConditionFailures")
    void conditionThrowablesStayUncontrolledThroughTerminalsAndComposition(
            Throwable cause, PreservingCondition<String> check,
            BiFunction<PreservingCondition<String>, FakeTime, AwaitResult<?, ?>> wait) {
        Failed<?, ?> result = assertInstanceOf(Failed.class, wait.apply(check, new FakeTime(0)));

        assertEquals(CONDITION_FAILED, result.failure().reason());
        assertSame(cause, result.failure().cause());
        var attempt = uncontrolled(CONDITION, attempts(result).getLast());
        assertSame(cause, attempt.cause());
        assertFalse(currentThread().isInterrupted());
    }

    private static Stream<Arguments> ordinaryConditionFailures() {
        return throwables().flatMap(cause -> throwingConditions(cause)
                .map(check -> arguments(named(cause.getClass().getSimpleName(), cause), check)));
    }

    private static Stream<Arguments> composedConditionFailures() {
        var waits = List.<Named<BiFunction<PreservingCondition<String>, FakeTime, AwaitResult<?, ?>>>>of(
                named("value", (check, time) -> diagnostic(await(() -> "actual").usingTime(time, time).every(ofNanos(1))).until(check)),
                named("optional", (check, time) -> tryAwait(() -> Optional.of("actual")).usingTime(time, time).until(hasValue(check))),
                named("collection", (check, time) -> tryAwait(() -> List.of("actual")).usingTime(time, time).until(singleElement(check))),
                named("captured", (check, time) -> diagnostic(await(() -> "actual").usingTime(time, time).every(ofNanos(1)))
                        .until(captured(isNotNull, check))));
        return throwables().flatMap(cause -> throwingConditions(cause).flatMap(check -> waits.stream()
                .map(wait -> arguments(named(cause.getClass().getSimpleName(), cause), check, wait))));
    }

    private static Stream<Named<PreservingCondition<String>>> throwingConditions(Throwable cause) {
        return Stream.of(named("callback", condition("callback", actual -> { throw cause; })),
                named("factory", conditionPerWait("factory", () -> rethrow(cause))));
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
        assertEquals("condition returned null ConditionResult", observation.cause().getMessage());
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

    @Test
    void observationClockCannotEraseTheSourceInterruption() {
        var actual = new Object();
        int[] sourceCalls = {0};
        int[] conditionCalls = {0};
        var outcome = new WaitEngine<Object>(config(1, 20, 0).withTime(() -> {
            interrupted();
            return 0;
        }, nanos -> { throw new AssertionError("parker must not be called"); }), () -> {
            sourceCalls[0]++;
            currentThread().interrupt();
            return actual;
        }, new SequenceExecution<>(bind(condition("ready", value -> {
            conditionCalls[0]++;
            return satisfied();
        })))).waitFor();

        var failure = assertInstanceOf(Uncontrolled.class, outcome);
        uncontrolled(SOURCE, failure.step().attempt());
        assertSame(actual, observation(failure.step().attempt()));
        assertEquals(1, sourceCalls[0]);
        assertEquals(0, conditionCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("clockFailurePhases")
    void clockThrowableDoesNotSetTheFlagAtAnyExecutionPhase(String phase, BinaryOperator<Executable> terminal, int[] expectedCalls) {
        var interruption = new InterruptedException("clock stopped");
        LongSupplier[] clock = {() -> 0};
        Consumer<String> boundary = mock();
        doAnswer(ignored -> { clock[0] = () -> rethrow(interruption); return null; }).when(boundary).accept(phase);
        boundary.accept("initial");
        int[] calls = {0, 0, 0};
        var waiting = await(() -> {
            calls[0]++;
            boundary.accept("source");
            return new Object();
        }).usingTime(() -> clock[0].getAsLong(), nanos -> {
            calls[2]++;
            boundary.accept("waiting");
        }).every(ofNanos(1)).upTo(ofNanos(20));
        var condition = condition("state", (Object actual) -> {
            calls[1]++;
            boundary.accept("condition");
            return unsatisfied("not ready");
        });

        assertSame(interruption, assertThrows(InterruptedException.class, terminal.apply(
                () -> waiting.until(condition), () -> diagnostic(waiting).until(condition))));
        assertFalse(currentThread().isInterrupted(), phase);
        assertArrayEquals(expectedCalls, calls, "source, condition, waiting calls");
    }

    private static Stream<Arguments> clockFailurePhases() {
        return terminals().stream().flatMap(terminal -> Stream.of(
                argumentSet(terminal.getName() + " / initial", "initial", terminal, new int[]{0, 0, 0}),
                argumentSet(terminal.getName() + " / source", "source", terminal, new int[]{1, 0, 0}),
                argumentSet(terminal.getName() + " / condition", "condition", terminal, new int[]{1, 1, 0}),
                argumentSet(terminal.getName() + " / waiting", "waiting", terminal, new int[]{1, 1, 1})));
    }

    @ParameterizedTest(name = "phase={0}")
    @ValueSource(strings = {"initial", "source", "condition", "waiting"})
    void clockFailuresRetainTheInterruptFlagAtEveryExecutionPhase(String phase) {
        var failure = new IllegalStateException("clock failed");
        LongSupplier clock = mock();
        Consumer<String> boundary = mock();
        doAnswer(ignored -> {
            doAnswer(read -> { interrupted(); throw failure; }).when(clock).getAsLong();
            currentThread().interrupt();
            return null;
        }).when(boundary).accept(phase);
        boundary.accept("initial");

        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                new WaitEngine<Object>(config(1, 20, 0).withTime(clock, nanos -> boundary.accept("waiting")), () -> {
                    boundary.accept("source");
                    return new Object();
                }, new SequenceExecution<>(bind(condition("ready", value -> {
                    boundary.accept("condition");
                    return unsatisfied("not yet");
                })))).waitFor()));
        assertTrue(currentThread().isInterrupted(), phase);
    }

    @ParameterizedTest
    @MethodSource("completionInterruptions")
    void completionClockInterruptionStopsNormalResultsWithoutReplacingConditionFailure(
            BinaryOperator<Executable> terminal, long persistence, int interruptedAttempt,
            ConditionResult state, Consumer<Throwable> assertCause) throws Throwable {
        var actual = new Object();
        var time = new FakeTime(0);
        int[] evaluations = {0};
        LongSupplier[] clock = {time::getAsLong};
        List<Supplier<ConditionResult>> steps = new ArrayList<>(nCopies(interruptedAttempt - 1, ConditionResult::satisfied));
        steps.add(() -> {
            clock[0] = () -> { currentThread().interrupt(); return time.getAsLong(); };
            return state;
        });
        var checks = steps.iterator();
        var waiting = await(() -> actual).usingTime(() -> clock[0].getAsLong(), time).every(ofNanos(1))
                .upTo(ofNanos(10)).persisting(ofNanos(persistence));
        var check = condition("completion boundary", value -> {
            evaluations[0]++;
            return checks.next().get();
        });
        Consumer<AwaitFailure> verifyFailure = failure -> {
            assertEquals(INTERRUPTED, failure.reason());
            assertCause.accept(failure.cause());
        };
        terminal.apply(
                () -> verifyFailure.accept(assertThrows(AwaitExecutionException.class, () -> waiting.until(check)).failure()),
                () -> {
                    var result = diagnostic(waiting).until(check);
                    verifyFailure.accept(failure(result));
                    assertEquals(interruptedAttempt, attempts(result).size());
                    uncontrolled(CONDITION, attempts(result).getLast());
                }).execute();
        assertEquals(interruptedAttempt, evaluations[0]);
        assertTrue(currentThread().isInterrupted());
    }

    private static Stream<Arguments> completionInterruptions() {
        Consumer<Throwable> interruption = cause -> assertInstanceOf(InterruptedException.class, cause);
        Consumer<Throwable> missingResult = cause -> {
            assertInstanceOf(NullPointerException.class, cause);
            assertEquals("condition returned null ConditionResult", cause.getMessage());
        };
        return terminals().stream().flatMap(terminal -> Stream.of(Map.entry(0L, 1), Map.entry(2L, 2)).flatMap(timing -> {
            String label = terminal.getName() + ", persistence=" + timing.getKey() + " / ";
            return Stream.of(
                    argumentSet(label + "satisfied", terminal, timing.getKey(), timing.getValue(), satisfied(), interruption),
                    argumentSet(label + "unsatisfied", terminal, timing.getKey(), timing.getValue(), unsatisfied("not ready"), interruption),
                    argumentSet(label + "null", terminal, timing.getKey(), timing.getValue(), null, missingResult));
        }));
    }

    @Test
    void conditionFlagAfterNormalReturnWinsBeforeInterpretation() {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return satisfied();
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

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("conditionClockFailures")
    void conditionThrowableDoesNotSetTheFlagBeforeALaterClockFailure(
            BinaryOperator<Executable> terminal, BiFunction<Runnable, InterruptedException, PreservingCondition<Object>> conditionFactory) {
        var interruption = new InterruptedException("condition stopped");
        var clockFailure = new IllegalStateException("completion clock failed");
        LongSupplier clock = mock();
        Runnable failClock = () -> doAnswer(ignored -> {
            assertFalse(currentThread().isInterrupted());
            throw clockFailure;
        }).when(clock).getAsLong();
        var signal = conditionFactory.apply(failClock, interruption);
        var waiting = await(Object::new).usingTime(clock, ignored -> {});

        assertSame(clockFailure, assertThrows(IllegalStateException.class, terminal.apply(
                () -> waiting.until(signal), () -> diagnostic(waiting).until(signal))));
        assertFalse(currentThread().isInterrupted());
    }

    private static Stream<Arguments> conditionClockFailures() {
        return Stream.<Named<BiFunction<Runnable, InterruptedException, PreservingCondition<Object>>>>of(
                named("thrown", (arm, cause) -> condition("interruption", actual -> { arm.run(); throw cause; })),
                named("factory", (arm, cause) -> conditionPerWait("interruption", () -> { arm.run(); throw cause; })))
                .flatMap(factory -> terminals().stream().map(terminal -> arguments(terminal, factory)));
    }

    private static List<Named<BinaryOperator<Executable>>> terminals() {
        return List.of(named("await", (thrown, captured) -> thrown), named("tryAwait", (thrown, captured) -> captured));
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

    private static ConditionResult failIfCalled(Object ignored) {
        throw new AssertionError("condition must not be called");
    }

    private static Attempt<?> attempt(Callable<Object> source, CheckedFunction<Object, ConditionResult> condition) {
        var time = new FakeTime(0);
        return new WaitEngine<Object>(config(1, 2, 0).withTime(time, time), source, new SequenceExecution<>(bind(preserving("test condition", actual -> {
            ConditionResult evaluation = condition.apply(actual);
            time.advanceNanos(2);
            return evaluation;
        })))).waitFor().step().attempt();
    }
}

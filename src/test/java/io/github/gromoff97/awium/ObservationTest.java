package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AttemptRecord.Phase.ACQUISITION;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.ConditionTestRuntime.throwUnchecked;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static java.time.Duration.ofNanos;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("removal")
class ObservationTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void interruptionWhileWaitingRetainsItsOriginalCompletionTime() {
        for (String boundary : List.of("beforePark", "afterPark", "afterInterval")) {
            for (long persistence : new long[] {0, 5}) {
                boolean[] evaluated = {false};
                int[] reads = {0};
                int[] sources = {0};
                int[] parks = {0};
                long[] now = {0};
                LongSupplier clock = () -> {
                    interrupted();
                    if (!evaluated[0]) return 0;
                    int read = ++reads[0];
                    if (read == 1) return 0;
                    now[0]++;
                    if (boundary.equals("beforePark") && read == 2) currentThread().interrupt();
                    if (boundary.equals("afterInterval") && read == 2) {
                        now[0] = 5;
                        currentThread().interrupt();
                    }
                    return now[0];
                };
                try {
                    var waiting = await(() -> { sources[0]++; return "ready"; }).usingTime(clock, nanos -> {
                        parks[0]++;
                        now[0] = 1;
                        currentThread().interrupt();
                    }).every(ofNanos(5)).upTo(ofNanos(20)).persisting(ofNanos(persistence));
                    var result = waiting.tryUntil(condition("state", actual -> {
                        evaluated[0] = true;
                        return persistence == 0 ? unsatisfied("waiting") : satisfied(actual);
                    }));
                    AwaitResult.Failed<?, ?> failure = assertInstanceOf(AwaitResult.Failed.class, result);
                    var attempt = failure.attempts().getLast();
                    var outcome = assertInstanceOf(AttemptRecord.Outcome.WaitingFailed.class, attempt.outcome());
                    assertNull(outcome.timing().retrievalOffset());
                    assertNull(outcome.timing().observationOffset());
                    assertEquals(boundary.equals("afterInterval") ? 6 : 2, outcome.timing().completionOffset().toNanos());
                    assertEquals(persistence == 0 ? ACQUISITION : AttemptRecord.Phase.PERSISTENCE, attempt.phase());
                    assertEquals(2, attempt.number());
                    assertEquals(1, sources[0]);
                    assertEquals(boundary.equals("afterPark") ? 1 : 0, parks[0]);
                    assertInstanceOf(InterruptedException.class, outcome.cause());
                    assertTrue(currentThread().isInterrupted());
                } finally {
                    interrupted();
                }
            }
        }
    }

    @Test
    void recordsEveryCompletedStageRelativeToExecutionStart() {
        var time = new FakeTime(100);
        var actual = new Object();
        var result = new Object();

        WaitCompletion<Object, Object> terminal = new WaitEngine<Object, Object>(config(1, 20, 0).withTime(time, time), () -> {
            time.advanceNanos(2);
            return actual;
        }, new ConditionAwaitAttempt<>(ConditionSupport.condition("test condition", null, value -> {
            time.advanceNanos(3);
            return satisfied(result);
        }))).waitFor();

        AttemptRecord<Object, Object> attempt = terminal.attempt();
        var observation = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, attempt.outcome());
        var outcome = assertInstanceOf(ConditionResult.Satisfied.class,
                observation.evaluation());
        var timing = observation.timing();
        assertEquals(1, attempt.number());
        assertEquals(ACQUISITION, attempt.phase());
        assertEquals(Duration.ZERO, timing.startOffset());
        assertEquals(Duration.ZERO, timing.retrievalOffset());
        assertEquals(Duration.ofNanos(2), timing.observationOffset());
        assertEquals(Duration.ofNanos(5), timing.completionOffset());
        assertSame(actual, observation.observed());
        assertSame(result, outcome.result());
    }

    @Test
    void evaluatedAttemptsRetainTheExactConditionResult() {
        var actual = new Object();
        for (ConditionResult<Object> original : List.of(satisfied(null),
                unsatisfied("not ready", new AssertionError("assertion")))) {
            var attempt = attempt(() -> actual, value -> original);
            var observation = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, attempt.outcome());
            assertSame(actual, observation.observed());
            assertSame(original, observation.evaluation());
        }
    }

    @Test
    void retainsOptionalAssertionCauseOnUnsatisfiedAttempts() {
        var actual = new Object();
        var assertion = new AssertionError("failed");

        var ordinary = attempt(() -> actual, value -> ConditionResult.unsatisfied("not ready"));
        var asserted = attempt(() -> actual, value -> unsatisfied("assertion did not pass", assertion));

        var ordinaryObservation = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, ordinary.outcome());
        var ordinaryOutcome = assertInstanceOf(ConditionResult.Unsatisfied.class,
                ordinaryObservation.evaluation());
        var assertedObservation = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, asserted.outcome());
        var assertedOutcome = assertInstanceOf(ConditionResult.Unsatisfied.class,
                assertedObservation.evaluation());
        assertSame(actual, ordinaryObservation.observed());
        assertEquals("not ready", ordinaryOutcome.mismatch());
        assertNull(ordinary.outcome().cause());
        assertSame(actual, assertedObservation.observed());
        assertEquals("assertion did not pass", assertedOutcome.mismatch());
        assertSame(assertion, asserted.outcome().cause());
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void classifiesEverySourceThrowableBeforeObservation(Throwable failure) {
        var time = new FakeTime(0);
        var waiting = await((Callable<Object>) () -> throwUnchecked(failure)).usingTime(time, time);
        var check = condition("must not run", ObservationTest::failIfCalled);
        var result = waiting.tryUntil(check);
        var failed = assertInstanceOf(AwaitResult.Failed.class, result);
        assertEquals(AwaitFailure.Reason.SOURCE_FAILED, failed.failure().reason());
        assertSame(failure, failed.failure().cause());
        assertSame(failure, assertThrows(AwaitExecutionException.class, () -> waiting.until(check)).getCause());
        assertFalse(currentThread().isInterrupted());

        var outcome = assertInstanceOf(AttemptRecord.Outcome.SourceRetrievalFailed.class,
                result.attempts().getFirst().outcome());
        assertSame(failure, outcome.cause());
        assertEquals(Duration.ZERO, outcome.timing().startOffset());
        assertEquals(Duration.ZERO, outcome.timing().retrievalOffset());
        assertNull(outcome.timing().observationOffset());
        assertEquals(Duration.ZERO, outcome.timing().completionOffset());
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void classifiesEveryConditionThrowableAfterObservation(Throwable failure) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> { throw failure; });

        var observation = assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class, attempt.outcome());
        assertSame(failure, observation.cause());
        assertSame(actual, observation.observed());
        assertFalse(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void conditionThrowablesStayUncontrolledThroughTerminalsAndComposition(Throwable cause) {
        PreservingCondition<String> callback = condition("callback", actual -> { throw cause; });
        PreservingCondition<String> factory = conditionPerWait("factory", () -> throwUnchecked(cause));
        for (var check : List.of(callback, factory)) {
            var time = new FakeTime(0);
            var waiting = await(() -> "actual").usingTime(time, time).every(ofNanos(1));
            assertSame(cause, assertThrows(AwaitExecutionException.class, () -> waiting.until(check)).getCause());
            List<Supplier<AwaitResult<?, ?>>> waits = List.of(
                    () -> waiting.tryUntil(check),
                    () -> await(() -> Optional.of("actual")).usingTime(time, time)
                            .tryUntil(OptionalConditions.hasValue(check)),
                    () -> await(() -> List.of("actual", "later")).usingTime(time, time)
                            .tryUntil(CollectionConditions.single(check)),
                    () -> waiting.tryUntil(Conditions.isNotNull, check));
            for (var wait : waits) {
                AwaitResult.Failed<?, ?> result = assertInstanceOf(AwaitResult.Failed.class, wait.get());
                assertEquals(AwaitFailure.Reason.CONDITION_FAILED, result.failure().reason());
                assertSame(cause, result.failure().cause());
                var attempt = assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class,
                        result.attempts().getLast().outcome());
                assertSame(cause, attempt.cause());
            }
        }
    }

    @Test
    void diagnosesNullEvaluationOnceAtConditionOrigin() {
        var actual = new Object();
        var conditionCalls = new int[1];
        var attempt = attempt(() -> actual, value -> {
            conditionCalls[0]++;
            return null;
        });

        var observation = assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class, attempt.outcome());
        assertSame(actual, observation.observed());
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

        var outcome = assertInstanceOf(AttemptRecord.Outcome.SourceInterrupted.class, attempt.outcome());
        assertSame(actual, outcome.observed());
        assertEquals("caller thread interrupt flag was set", outcome.cause().getMessage());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void observationClockCannotEraseTheSourceInterruption() {
        var actual = new Object();
        int[] sourceCalls = {0};
        int[] conditionCalls = {0};
        var outcome = new WaitEngine<Object, Object>(config(1, 20, 0).withTime(() -> {
            interrupted();
            return 0;
        }, nanos -> { throw new AssertionError("parker must not be called"); }), () -> {
            sourceCalls[0]++;
            currentThread().interrupt();
            return actual;
        }, ConditionAwaitAttempt.bind(Conditions.condition("ready", value -> {
            conditionCalls[0]++;
            return satisfied(value);
        }).definition)).waitFor();

        var failure = assertInstanceOf(WaitCompletion.Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(AttemptRecord.Outcome.SourceInterrupted.class, failure.attempt().outcome());
        assertSame(actual, attempt.observed());
        assertEquals(1, sourceCalls[0]);
        assertEquals(0, conditionCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void clockThrowableDoesNotSetTheFlagAtAnyExecutionPhase() {
        for (String phase : List.of("initial", "source", "condition", "waiting")) {
            for (boolean capture : List.of(false, true)) {
                var interruption = new InterruptedException("clock stopped");
                boolean[] armed = {phase.equals("initial")};
                int[] calls = {0, 0, 0};
                var waiting = await(() -> {
                    calls[0]++;
                    if (phase.equals("source")) armed[0] = true;
                    return new Object();
                }).usingTime(() -> {
                    if (armed[0]) return throwUnchecked(interruption);
                    return 0;
                }, nanos -> {
                    calls[2]++;
                    armed[0] = true;
                }).every(ofNanos(1)).upTo(ofNanos(20));
                var condition = condition("state", (Object actual) -> {
                    calls[1]++;
                    if (phase.equals("condition")) armed[0] = true;
                    return unsatisfied("not ready");
                });
                try {
                    assertSame(interruption, assertThrows(InterruptedException.class, () -> {
                        if (capture) waiting.tryUntil(condition);
                        else waiting.until(condition);
                    }));
                    assertFalse(currentThread().isInterrupted(), phase);
                    assertEquals(phase.equals("initial") ? 0 : 1, calls[0]);
                    assertEquals(phase.equals("condition") || phase.equals("waiting") ? 1 : 0, calls[1]);
                    assertEquals(phase.equals("waiting") ? 1 : 0, calls[2]);
                } finally {
                    interrupted();
                }
            }
        }
    }

    @Test
    void clockFailuresRetainTheInterruptFlagAtEveryExecutionPhase() {
        for (String phase : List.of("initial", "source", "condition", "waiting")) {
            var failure = new IllegalStateException("clock failed");
            boolean[] armed = {phase.equals("initial")};
            try {
                if (armed[0]) currentThread().interrupt();
                assertSame(failure, assertThrows(IllegalStateException.class, () ->
                        new WaitEngine<Object, Object>(config(1, 20, 0).withTime(() -> {
                            if (armed[0]) {
                                interrupted();
                                throw failure;
                            }
                            return 0;
                        }, nanos -> {
                            armed[0] = true;
                            currentThread().interrupt();
                        }), () -> {
                            if (phase.equals("source")) {
                                armed[0] = true;
                                currentThread().interrupt();
                            }
                            return new Object();
                        }, ConditionAwaitAttempt.bind(Conditions.condition("ready", value -> {
                            if (phase.equals("condition")) {
                                armed[0] = true;
                                currentThread().interrupt();
                            }
                            return ConditionResult.unsatisfied("not yet");
                        }).definition)).waitFor()));
                assertTrue(currentThread().isInterrupted(), phase);
            } finally {
                interrupted();
            }
        }
    }

    @Test
    void completionClockInterruptionStopsNormalResultsWithoutReplacingConditionFailure() {
        var actual = new Object();
        for (boolean capture : List.of(false, true)) {
            for (long persistence : new long[] {0, 2}) {
                for (String form : List.of("satisfied", "unsatisfied", "null")) {
                    var time = new FakeTime(0);
                    int[] evaluations = {0};
                    int interruptedAttempt = persistence == 0 ? 1 : 2;
                    var waiting = await(() -> actual).usingTime(() -> {
                        if (evaluations[0] == interruptedAttempt) currentThread().interrupt();
                        return time.getAsLong();
                    }, time).every(Duration.ofNanos(1)).upTo(Duration.ofNanos(10))
                            .persisting(Duration.ofNanos(persistence));
                    var check = condition("completion boundary", value -> {
                        if (++evaluations[0] < interruptedAttempt) return satisfied(value);
                        return switch (form) {
                            case "satisfied" -> satisfied(value);
                            case "unsatisfied" -> ConditionResult.unsatisfied("not ready");
                            default -> null;
                        };
                    });
                    try {
                        AwaitFailure failure;
                        if (capture) {
                            var result = waiting.tryUntil(check);
                            failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                            assertEquals(interruptedAttempt, result.totalAttempts());
                            assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class, result.attempts().getLast().outcome());
                        } else {
                            failure = assertThrows(AwaitExecutionException.class, () -> waiting.until(check)).failure();
                        }
                        if (form.equals("null")) {
                            assertEquals(AwaitFailure.Reason.INTERRUPTED, failure.reason());
                            assertInstanceOf(NullPointerException.class, failure.cause());
                            assertEquals("condition returned null ConditionResult", failure.cause().getMessage());
                        } else {
                            assertEquals(AwaitFailure.Reason.INTERRUPTED, failure.reason(), form);
                            assertInstanceOf(InterruptedException.class, failure.cause());
                        }
                        assertEquals(interruptedAttempt, evaluations[0]);
                        assertTrue(currentThread().isInterrupted());
                    } finally {
                        interrupted();
                    }
                }
            }
        }
    }

    @Test
    void conditionFlagAfterNormalReturnWinsBeforeInterpretation() {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return satisfied(new Object());
        });

        var observation = assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class, attempt.outcome());
        assertSame(actual, observation.observed());
        assertEquals(InterruptedException.class, observation.cause().getClass());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void abruptSourceThrowableWinsOverCallbackSetFlag(Throwable failure) {
        var attempt = attempt(() -> {
            currentThread().interrupt();
            return throwUnchecked(failure);
        }, ObservationTest::failIfCalled);

        var outcome = assertInstanceOf(AttemptRecord.Outcome.SourceRetrievalFailed.class, attempt.outcome());
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

        var observation = assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class, attempt.outcome());
        assertSame(failure, observation.cause());
        assertSame(actual, observation.observed());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void conditionThrowableDoesNotSetTheFlagBeforeALaterClockFailure() {
        for (boolean capture : List.of(false, true)) {
            for (String form : List.of("thrown", "factory")) {
                var interruption = new InterruptedException("condition stopped");
                var clockFailure = new IllegalStateException("completion clock failed");
                boolean[] failed = {false};
                PreservingCondition<Object> signal = switch (form) {
                    case "thrown" -> condition("interruption", actual -> {
                        failed[0] = true;
                        throw interruption;
                    });
                    default -> conditionPerWait("interruption", () -> {
                        failed[0] = true;
                        throw interruption;
                    });
                };
                var waiting = await(Object::new).usingTime(() -> {
                    if (failed[0]) {
                        assertFalse(currentThread().isInterrupted(), form);
                        throw clockFailure;
                    }
                    return 0;
                }, ignored -> {});
                try {
                    assertSame(clockFailure, assertThrows(IllegalStateException.class, () -> {
                        if (capture) waiting.tryUntil(signal);
                        else waiting.until(signal);
                    }));
                    assertFalse(currentThread().isInterrupted());
                } finally {
                    interrupted();
                }
            }
        }
    }

    private static Stream<Throwable> throwables() {
        return Stream.of(new Throwable("direct"), new Exception("checked"), new InterruptedException("interrupted"),
                new RuntimeException("runtime"),
                new AssertionError("assertion"), new LinkageError("linkage"),
                new InternalError("fatal"), new ThreadDeath());
    }

    private static <R> ConditionResult<R> failIfCalled(Object ignored) {
        throw new AssertionError("condition must not be called");
    }

    private static <R> AttemptRecord<Object, R> attempt(Callable<Object> source,
            CheckedFunction<Object, ConditionResult<R>> condition) {
        var time = new FakeTime(0);
        return new WaitEngine<Object, R>(config(1, 2, 0).withTime(time, time), source, new ConditionAwaitAttempt<>(ConditionSupport.condition("test condition", null, actual -> {
            ConditionResult<R> evaluation = condition.apply(actual);
            time.advanceNanos(2);
            return evaluation;
        }))).waitFor().attempt();
    }
}

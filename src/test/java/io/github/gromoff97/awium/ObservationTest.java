package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.internal.SequenceExecution;
import io.github.gromoff97.awium.internal.WaitCompletion;
import io.github.gromoff97.awium.internal.WaitEngine;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.uncontrolled;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.ACQUISITION;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
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

    @ParameterizedTest(name = "boundary={0}, persistence={1}")
    @CsvSource({"beforePark,0", "beforePark,5", "afterPark,0", "afterPark,5",
            "afterInterval,0", "afterInterval,5"})
    void interruptionWhileWaitingRetainsItsOriginalCompletionTime(String boundary, long persistence) {
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
        var waiting = await(() -> { sources[0]++; return "ready"; }).usingTime(clock, nanos -> {
            parks[0]++;
            now[0] = 1;
            currentThread().interrupt();
        }).every(ofNanos(5)).upTo(ofNanos(20)).persisting(ofNanos(persistence));
        var result = diagnostic(waiting).until(condition("state", actual -> {
            evaluated[0] = true;
            return persistence == 0 ? unsatisfied("waiting") : satisfied();
        }));
        AwaitResult.Failed<?, ?> failure = assertInstanceOf(AwaitResult.Failed.class, result);
        var attempt = attempts(failure).getLast();
        var outcome = uncontrolled(Attempt.Operation.WAITING, attempt);
        assertInstanceOf(Attempt.FailedBeforeObservation.class, attempt);
        assertEquals(boundary.equals("afterInterval") ? 6 : 2, now[0]);
        assertEquals(2, attempts(failure).size());
        assertEquals(1, sources[0]);
        assertEquals(boundary.equals("afterPark") ? 1 : 0, parks[0]);
        assertInstanceOf(InterruptedException.class, outcome.cause());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void recordsCompletionRelativeToExecutionStart() {
        var time = new FakeTime(100);
        var actual = new Object();
        var result = new Object();

        var execution = new SequenceExecution<>(ConditionSupport.bind(ConditionSupport.condition("test condition", null, (Object value) -> {
            time.advanceNanos(3);
            return AttemptResult.satisfied(result);
        })));
        WaitCompletion terminal = new WaitEngine<>(config(1, 20, 0).withTime(time, time), () -> {
            time.advanceNanos(2);
            return actual;
        }, execution).waitFor();

        var step = terminal.step();
        var observation = step.attempt();
        assertInstanceOf(Attempt.Satisfied.class, observation);
        assertEquals(ACQUISITION, step.phase());
        assertEquals(5, step.completedAfterNanos());
        assertSame(actual, observation(observation));
        assertSame(result, execution.values().getFirst());
    }

    @Test
    void evaluatedAttemptsRetainStatusAndDiagnosticDetails() {
        var actual = new Object();
        for (ConditionResult original : List.of(satisfied(),
                unsatisfied("not ready", new AssertionError("assertion")))) {
            var attempt = attempt(() -> actual, value -> original);
            assertSame(actual, observation(attempt));
            switch (original) {
                case ConditionResult.Satisfied ignored -> assertInstanceOf(Attempt.Satisfied.class, attempt);
                case ConditionResult.Unsatisfied mismatch -> {
                    var status = assertInstanceOf(Attempt.Unsatisfied.class, attempt);
                    assertEquals(mismatch.mismatch(), status.mismatch());
                    assertSame(mismatch.cause(), status.cause());
                }
            }
        }
    }

    @Test
    void retainsOptionalAssertionCauseOnUnsatisfiedAttempts() {
        var actual = new Object();
        var assertion = new AssertionError("failed");

        var ordinary = attempt(() -> actual, value -> ConditionResult.unsatisfied("not ready"));
        var asserted = attempt(() -> actual, value -> unsatisfied("assertion did not pass", assertion));

        var ordinaryOutcome = assertInstanceOf(Attempt.Unsatisfied.class, ordinary);
        var assertedOutcome = assertInstanceOf(Attempt.Unsatisfied.class, asserted);
        assertSame(actual, observation(ordinary));
        assertEquals("not ready", ordinaryOutcome.mismatch());
        assertNull(ordinary.cause());
        assertSame(actual, observation(asserted));
        assertEquals("assertion did not pass", assertedOutcome.mismatch());
        assertSame(assertion, asserted.cause());
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void classifiesEverySourceThrowableBeforeObservation(Throwable failure) {
        var time = new FakeTime(0);
        var waiting = await((Callable<Object>) () -> rethrow(failure)).usingTime(time, time);
        var check = condition("must not run", ObservationTest::failIfCalled);
        var result = diagnostic(waiting).until(check);
        var failed = failure(result);
        assertEquals(AwaitFailure.Reason.SOURCE_FAILED, failed.reason());
        assertSame(failure, failed.cause());
        assertSame(failure, assertThrows(AwaitExecutionException.class, () -> waiting.until(check)).getCause());
        assertFalse(currentThread().isInterrupted());

        var outcome = uncontrolled(Attempt.Operation.SOURCE, attempts(result).getFirst());
        assertSame(failure, outcome.cause());
        assertSame(check, result.conditions().getFirst().condition());
        assertInstanceOf(Attempt.FailedBeforeObservation.class, attempts(result).getFirst());

    }

    @ParameterizedTest
    @MethodSource("throwables")
    void classifiesEveryConditionThrowableAfterObservation(Throwable failure) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> { throw failure; });

        var observation = uncontrolled(Attempt.Operation.CONDITION, attempt);
        assertSame(failure, observation.cause());
        assertSame(actual, observation(attempt));
        assertFalse(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void conditionThrowablesStayUncontrolledThroughTerminalsAndComposition(Throwable cause) {
        PreservingCondition<String> callback = condition("callback", actual -> { throw cause; });
        PreservingCondition<String> factory = conditionPerWait("factory", () -> rethrow(cause));
        for (var check : List.of(callback, factory)) {
            var time = new FakeTime(0);
            var waiting = await(() -> "actual").usingTime(time, time).every(ofNanos(1));
            assertSame(cause, assertThrows(AwaitExecutionException.class, () -> waiting.until(check)).getCause());
            List<Supplier<AwaitResult<?, ?>>> waits = List.of(
                    () -> diagnostic(waiting).until(check),
                    () -> tryAwait(() -> Optional.of("actual")).usingTime(time, time)
                            .until(OptionalConditions.hasValue(check)),
                    () -> tryAwait(() -> List.of("actual")).usingTime(time, time)
                            .until(CollectionConditions.singleElement(check)),
                    () -> diagnostic(waiting).until(captured(Conditions.isNotNull, check)));
            for (var wait : waits) {
                AwaitResult.Failed<?, ?> result = assertInstanceOf(AwaitResult.Failed.class, wait.get());
                assertEquals(AwaitFailure.Reason.CONDITION_FAILED, result.failure().reason());
                assertSame(cause, result.failure().cause());
                var attempt = uncontrolled(Attempt.Operation.CONDITION, attempts(result).getLast());
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

        var observation = uncontrolled(Attempt.Operation.CONDITION, attempt);
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

        var outcome = uncontrolled(Attempt.Operation.SOURCE, attempt);
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
        }, new SequenceExecution<>(ConditionSupport.bind(Conditions.condition("ready", value -> {
            conditionCalls[0]++;
            return satisfied();
        })))).waitFor();

        var failure = assertInstanceOf(WaitCompletion.Uncontrolled.class, outcome);
        uncontrolled(Attempt.Operation.SOURCE, failure.step().attempt());
        assertSame(actual, observation(failure.step().attempt()));
        assertEquals(1, sourceCalls[0]);
        assertEquals(0, conditionCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest(name = "phase={0}, tryAwait={1}")
    @CsvSource({"initial,false", "initial,true", "source,false", "source,true",
            "condition,false", "condition,true", "waiting,false", "waiting,true"})
    void clockThrowableDoesNotSetTheFlagAtAnyExecutionPhase(String phase, boolean capture) {
        var interruption = new InterruptedException("clock stopped");
        boolean[] armed = {phase.equals("initial")};
        int[] calls = {0, 0, 0};
        var waiting = await(() -> {
            calls[0]++;
            if (phase.equals("source")) armed[0] = true;
            return new Object();
        }).usingTime(() -> {
            if (armed[0]) return rethrow(interruption);
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
        assertSame(interruption, assertThrows(InterruptedException.class, () -> {
            if (capture) diagnostic(waiting).until(condition);
            else waiting.until(condition);
        }));
        assertFalse(currentThread().isInterrupted(), phase);
        assertEquals(phase.equals("initial") ? 0 : 1, calls[0]);
        assertEquals(phase.equals("condition") || phase.equals("waiting") ? 1 : 0, calls[1]);
        assertEquals(phase.equals("waiting") ? 1 : 0, calls[2]);
    }

    @ParameterizedTest(name = "phase={0}")
    @ValueSource(strings = {"initial", "source", "condition", "waiting"})
    void clockFailuresRetainTheInterruptFlagAtEveryExecutionPhase(String phase) {
        var failure = new IllegalStateException("clock failed");
        boolean[] armed = {phase.equals("initial")};
        if (armed[0]) currentThread().interrupt();
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                new WaitEngine<Object>(config(1, 20, 0).withTime(() -> {
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
                }, new SequenceExecution<>(ConditionSupport.bind(Conditions.condition("ready", value -> {
                    if (phase.equals("condition")) {
                        armed[0] = true;
                        currentThread().interrupt();
                    }
                    return ConditionResult.unsatisfied("not yet");
                })))).waitFor()));
        assertTrue(currentThread().isInterrupted(), phase);
    }

    @ParameterizedTest(name = "tryAwait={0}, persistence={1}, result={2}")
    @CsvSource({"false,0,satisfied", "false,0,unsatisfied", "false,0,null",
            "false,2,satisfied", "false,2,unsatisfied", "false,2,null",
            "true,0,satisfied", "true,0,unsatisfied", "true,0,null",
            "true,2,satisfied", "true,2,unsatisfied", "true,2,null"})
    void completionClockInterruptionStopsNormalResultsWithoutReplacingConditionFailure(boolean capture, long persistence, String form) {
        var actual = new Object();
        var time = new FakeTime(0);
        int[] evaluations = {0};
        int interruptedAttempt = persistence == 0 ? 1 : 2;
        var waiting = await(() -> actual).usingTime(() -> {
            if (evaluations[0] == interruptedAttempt) currentThread().interrupt();
            return time.getAsLong();
        }, time).every(ofNanos(1)).upTo(ofNanos(10))
                .persisting(ofNanos(persistence));
        var check = condition("completion boundary", value -> {
            if (++evaluations[0] < interruptedAttempt) return satisfied();
            return switch (form) {
                case "satisfied" -> satisfied();
                case "unsatisfied" -> ConditionResult.unsatisfied("not ready");
                default -> null;
            };
        });
        AwaitFailure failure;
        if (capture) {
            var result = diagnostic(waiting).until(check);
            failure = failure(result);
            assertEquals(interruptedAttempt, attempts(result).size());
            uncontrolled(Attempt.Operation.CONDITION, attempts(result).getLast());
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
    }

    @Test
    void conditionFlagAfterNormalReturnWinsBeforeInterpretation() {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return satisfied();
        });

        var observation = uncontrolled(Attempt.Operation.CONDITION, attempt);
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

        var outcome = uncontrolled(Attempt.Operation.SOURCE, attempt);
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

        var observation = uncontrolled(Attempt.Operation.CONDITION, attempt);
        assertSame(failure, observation.cause());
        assertSame(actual, observation(attempt));
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest(name = "tryAwait={0}, callback={1}")
    @CsvSource({"false,thrown", "false,factory", "true,thrown", "true,factory"})
    void conditionThrowableDoesNotSetTheFlagBeforeALaterClockFailure(boolean capture, String form) {
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
        assertSame(clockFailure, assertThrows(IllegalStateException.class, () -> {
            if (capture) diagnostic(waiting).until(signal);
            else waiting.until(signal);
        }));
        assertFalse(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("throwables")
    void returnedMismatchCauseSurvivesTheInterruptFlag(Throwable failure) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return unsatisfied("not ready", failure);
        });

        var outcome = uncontrolled(Attempt.Operation.CONDITION, attempt);
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
        return new WaitEngine<Object>(config(1, 2, 0).withTime(time, time), source, new SequenceExecution<>(ConditionSupport.bind(ConditionSupport.preserving("test condition", actual -> {
            ConditionResult evaluation = condition.apply(actual);
            time.advanceNanos(2);
            return evaluation;
        })))).waitFor().step().attempt();
    }
}

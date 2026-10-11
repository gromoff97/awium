package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitAttempt.Phase.ACQUISITION;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.ConditionResult.assertionUnsatisfied;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.uncontrolled;
import static io.github.gromoff97.awium.Conditions.condition;
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
    void recordsEveryCompletedStageRelativeToExecutionStart() {
        var time = new FakeTime(100);
        var actual = new Object();
        var result = new Object();

        WaitCompletion<Object, Object> terminal = new WaitEngine<Object, Object>(config(1, 20, 0), time, time, () -> {
            time.advanceNanos(2);
            return actual;
        }, Conditions.<Object, Object>condition("test condition", value -> {
            time.advanceNanos(3);
            return satisfied(result);
        }).newSession()).waitFor();

        AwaitAttempt<Object, Object> attempt = terminal.attempt();
        var observation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, attempt.outcome());
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
    void evaluatedAttemptsRetainTheExactConditionResultAndContext() {
        var actual = new Object();
        var context = new ConditionResult.Context.Sequence(0, 2, 1,
                "first stage", "business reason", new ConditionResult.Reference<>("Expected", actual));
        for (ConditionResult<Object> evaluation : List.of(satisfied(null),
                assertionUnsatisfied("not ready", new AssertionError("assertion")),
                uncontrolled(new IllegalStateException("failed")))) {
            var original = evaluation.withContext(context);
            var attempt = attempt(() -> actual, value -> original);
            var observation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, attempt.outcome());
            assertSame(actual, observation.observed());
            assertSame(original, observation.evaluation());
            assertSame(context, observation.evaluation().context());
        }
    }

    @Test
    void retainsOptionalAssertionCauseOnUnsatisfiedAttempts() {
        var actual = new Object();
        var assertion = new AssertionError("failed");

        var ordinary = attempt(() -> actual, value -> ConditionResult.unsatisfied("not ready"));
        var asserted = attempt(() -> actual, value -> assertionUnsatisfied("assertion did not pass", assertion));

        var ordinaryObservation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, ordinary.outcome());
        var ordinaryOutcome = assertInstanceOf(ConditionResult.Unsatisfied.class,
                ordinaryObservation.evaluation());
        var assertedObservation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, asserted.outcome());
        var assertedOutcome = assertInstanceOf(ConditionResult.Unsatisfied.class,
                assertedObservation.evaluation());
        assertSame(actual, ordinaryObservation.observed());
        assertEquals("not ready", ordinaryOutcome.mismatch());
        assertNull(ordinaryOutcome.assertion());
        assertSame(actual, assertedObservation.observed());
        assertEquals("assertion did not pass", assertedOutcome.mismatch());
        assertSame(assertion, assertedOutcome.assertion());
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void classifiesEveryOtherSourceThrowableBeforeObservation(Throwable failure) {
        var attempt = attempt(() -> throwFailure(failure), ObservationTest::failIfCalled);

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.SourceRetrievalFailed.class, attempt.outcome());
        assertSame(failure, outcome.failure());
        assertEquals(Duration.ZERO, outcome.timing().startOffset());
        assertEquals(Duration.ZERO, outcome.timing().retrievalOffset());
        assertEquals(Duration.ZERO, outcome.timing().completionOffset());
    }

    @ParameterizedTest
    @MethodSource("uncheckedNonFatalThrowables")
    void classifiesEveryOtherConditionThrowableAfterObservation(Throwable failure) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> throwUnchecked(failure));

        var observation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, attempt.outcome());
        var outcome = assertInstanceOf(ConditionResult.Uncontrolled.class,
                observation.evaluation());
        assertSame(failure, outcome.cause());
        assertSame(actual, observation.observed());
    }

    @Test
    void classifiesAnEvaluationOwnedUncontrolledCauseAsConditionFailure() {
        var actual = new Object();
        var failure = new IllegalStateException("built-in failed");
        var attempt = attempt(() -> actual, value -> uncontrolled(failure));

        var observation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, attempt.outcome());
        var outcome = assertInstanceOf(ConditionResult.Uncontrolled.class,
                observation.evaluation());
        assertSame(failure, outcome.cause());
        assertSame(actual, observation.observed());
    }

    @Test
    void evaluationOwnedInterruptionIsPreservedAndRestored() {
        var actual = new Object();
        var interruption = new InterruptedException("condition stopped");
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return uncontrolled(interruption);
        });

        var observation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, attempt.outcome());
        var outcome = assertInstanceOf(ConditionResult.Uncontrolled.class,
                observation.evaluation());
        assertSame(interruption, outcome.cause());
        assertSame(actual, observation.observed());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void diagnosesNullEvaluationOnceAtConditionOrigin() {
        var actual = new Object();
        var conditionCalls = new int[1];
        var attempt = attempt(() -> actual, value -> {
            conditionCalls[0]++;
            return null;
        });

        var observation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, attempt.outcome());
        var outcome = assertInstanceOf(ConditionResult.Uncontrolled.class,
                observation.evaluation());
        assertSame(actual, observation.observed());
        assertEquals(NullPointerException.class, outcome.cause().getClass());
        assertEquals("condition returned null ConditionResult", outcome.cause().getMessage());
        assertEquals(1, conditionCalls[0]);
    }

    @Test
    void thrownSourceInterruptionIsPreservedAndRestored() {
        var interruption = new InterruptedException("source stopped");
        var attempt = attempt(() -> {
            throw interruption;
        }, ObservationTest::failIfCalled);

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.SourceRetrievalFailed.class, attempt.outcome());
        assertSame(interruption, outcome.failure());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void thrownConditionInterruptionIsPreservedWithActualAndRestored() {
        var actual = new Object();
        var interruption = new InterruptedException("condition stopped");
        var attempt = attempt(() -> actual, value -> throwUnchecked(interruption));

        var observation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, attempt.outcome());
        var outcome = assertInstanceOf(ConditionResult.Uncontrolled.class,
                observation.evaluation());
        assertSame(interruption, outcome.cause());
        assertSame(actual, observation.observed());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void sourceFlagAfterNormalReturnRetainsObservationAndSkipsCondition() {
        var actual = new Object();
        var attempt = attempt(() -> {
            currentThread().interrupt();
            return actual;
        }, ObservationTest::failIfCalled);

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.SourceInterrupted.class, attempt.outcome());
        assertSame(actual, outcome.observed());
        assertEquals("caller thread interrupt flag was set", outcome.failure().getMessage());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void observationClockCannotEraseTheSourceInterruption() {
        var actual = new Object();
        int[] sourceCalls = {0};
        int[] conditionCalls = {0};
        var outcome = new WaitEngine<Object, Object>(config(1, 20, 0), () -> {
            interrupted();
            return 0;
        }, nanos -> { throw new AssertionError("parker must not be called"); }, () -> {
            sourceCalls[0]++;
            currentThread().interrupt();
            return actual;
        }, Conditions.<Object, Object>condition("ready", value -> {
            conditionCalls[0]++;
            return satisfied(value);
        }).newSession()).waitFor();

        var failure = assertInstanceOf(WaitCompletion.Uncontrolled.class, outcome);
        var attempt = assertInstanceOf(AwaitAttempt.Outcome.SourceInterrupted.class, failure.attempt().outcome());
        assertSame(actual, attempt.observed());
        assertEquals(1, sourceCalls[0]);
        assertEquals(0, conditionCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void clockFailuresRetainTheInterruptFlagAtEveryExecutionPhase() {
        for (String phase : List.of("initial", "source", "condition", "waiting")) {
            var failure = new IllegalStateException("clock failed");
            boolean[] armed = {phase.equals("initial")};
            try {
                if (armed[0]) currentThread().interrupt();
                assertSame(failure, assertThrows(IllegalStateException.class, () ->
                        new WaitEngine<Object, Object>(config(1, 20, 0), () -> {
                            if (armed[0]) {
                                interrupted();
                                throw failure;
                            }
                            return 0;
                        }, nanos -> {
                            armed[0] = true;
                            currentThread().interrupt();
                        }, () -> {
                            if (phase.equals("source")) {
                                armed[0] = true;
                                currentThread().interrupt();
                            }
                            return new Object();
                        }, Conditions.<Object, Object>condition("ready", value -> {
                            if (phase.equals("condition")) {
                                armed[0] = true;
                                currentThread().interrupt();
                            }
                            return ConditionResult.unsatisfied("not yet");
                        }).newSession()).waitFor()));
                assertTrue(currentThread().isInterrupted(), phase);
            } finally {
                interrupted();
            }
        }
    }

    @Test
    void completionClockInterruptionWinsBeforeNormalResultIsInterpreted() {
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
                            assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, result.attempts().getLast().outcome());
                        } else {
                            failure = assertThrows(AwaitExecutionException.class, () -> waiting.until(check)).failure();
                        }
                        assertEquals(AwaitFailure.Reason.INTERRUPTED, failure.reason(), form);
                        assertInstanceOf(InterruptedException.class, failure.cause());
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

        var observation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, attempt.outcome());
        var outcome = assertInstanceOf(ConditionResult.Uncontrolled.class,
                observation.evaluation());
        assertSame(actual, observation.observed());
        assertEquals(InterruptedException.class, outcome.cause().getClass());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("nonFatalThrowables")
    void abruptSourceThrowableWinsOverCallbackSetFlag(Throwable failure) {
        var attempt = attempt(() -> {
            currentThread().interrupt();
            return throwFailure(failure);
        }, ObservationTest::failIfCalled);

        var outcome = assertInstanceOf(AwaitAttempt.Outcome.SourceRetrievalFailed.class, attempt.outcome());
        assertSame(failure, outcome.failure());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("uncheckedNonFatalThrowables")
    void abruptConditionThrowableWinsOverCallbackSetFlag(Throwable failure) {
        var actual = new Object();
        var attempt = attempt(() -> actual, value -> {
            currentThread().interrupt();
            return throwUnchecked(failure);
        });

        var observation = assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, attempt.outcome());
        var outcome = assertInstanceOf(ConditionResult.Uncontrolled.class,
                observation.evaluation());
        assertSame(failure, outcome.cause());
        assertSame(actual, observation.observed());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("fatalSignals")
    void fatalSignalsFromSourceEscapeRawAndSkipCondition(Error fatal) {
        assertSame(fatal, assertThrows(fatal.getClass(),
                () -> attempt(() -> throwFailure(fatal), ObservationTest::failIfCalled)));
    }

    @ParameterizedTest
    @MethodSource("fatalSignals")
    void fatalSignalsFromConditionEscapeRaw(Error fatal) {
        assertSame(fatal, assertThrows(fatal.getClass(),
                () -> attempt(Object::new, value -> throwUnchecked(fatal))));
    }

    @ParameterizedTest
    @MethodSource("fatalSignals")
    void evaluationOwnedFatalSignalsEscapeRaw(Error fatal) {
        assertSame(fatal, assertThrows(fatal.getClass(), () -> attempt(Object::new, value -> {
            currentThread().interrupt();
            return uncontrolled(fatal);
        })));
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void conditionInterruptionIsRestoredBeforeAnyLaterClockFailure() {
        for (boolean capture : List.of(false, true)) {
            for (String form : List.of("thrown", "returned", "factory")) {
                var interruption = new InterruptedException("condition stopped");
                var clockFailure = new IllegalStateException("completion clock failed");
                boolean[] failed = {false};
                Condition<Object, Object> signal = switch (form) {
                    case "thrown" -> condition("interruption", actual -> {
                        failed[0] = true;
                        throw interruption;
                    });
                    case "returned" -> condition("interruption", actual -> {
                        failed[0] = true;
                        return uncontrolled(interruption);
                    });
                    default -> condition("interruption", () -> {
                        failed[0] = true;
                        throw interruption;
                    });
                };
                var waiting = await(Object::new).usingTime(() -> {
                    if (failed[0]) {
                        assertTrue(currentThread().isInterrupted(), form);
                        throw clockFailure;
                    }
                    return 0;
                }, ignored -> {});
                try {
                    assertSame(clockFailure, assertThrows(IllegalStateException.class, () -> {
                        if (capture) waiting.tryUntil(signal);
                        else waiting.until(signal);
                    }));
                    assertTrue(currentThread().isInterrupted());
                } finally {
                    interrupted();
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("fatalSignals")
    void returnedFatalSignalsEscapeBeforeAnotherClockRead(Error fatal) {
        for (boolean capture : List.of(false, true)) {
            boolean[] evaluated = {false};
            var signal = condition("fatal", actual -> {
                evaluated[0] = true;
                return uncontrolled(fatal);
            });
            var waiting = await(Object::new).usingTime(() -> {
                if (evaluated[0]) throw new IllegalStateException("completion clock failed");
                return 0;
            }, ignored -> {});
            assertSame(fatal, assertThrows(fatal.getClass(), () -> {
                if (capture) waiting.tryUntil(signal);
                else waiting.until(signal);
            }));
        }
    }

    @Test
    void abruptFatalThrowableWinsOverCallbackSetFlag() {
        var sourceFatal = new InternalError("source fatal");
        assertSame(sourceFatal, assertThrows(InternalError.class, () -> attempt(() -> {
            currentThread().interrupt();
            throw sourceFatal;
        }, ObservationTest::failIfCalled)));
        assertTrue(currentThread().isInterrupted());
        interrupted();

        var conditionFatal = new InternalError("condition fatal");
        assertSame(conditionFatal, assertThrows(InternalError.class, () -> attempt(Object::new, actual -> {
            currentThread().interrupt();
            throw conditionFatal;
        })));
        assertTrue(currentThread().isInterrupted());
    }

    private static Stream<Throwable> nonFatalThrowables() {
        return Stream.of(new Exception("checked"), new AssertionError("assertion"));
    }

    private static Stream<Throwable> uncheckedNonFatalThrowables() {
        return Stream.of(new RuntimeException("runtime"), new AssertionError("assertion"));
    }

    private static Stream<Error> fatalSignals() {
        return Stream.of(new InternalError("fatal"), new ThreadDeath());
    }

    private static <T> T throwFailure(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        throw (Error) failure;
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T throwUnchecked(Throwable failure) throws E {
        throw (E) failure;
    }

    private static <T, R> ConditionResult<R> failIfCalled(T ignored) {
        throw new AssertionError("condition must not be called");
    }

    private static <R> AwaitAttempt<Object, R> attempt(Source<Object> source,
            Function<Object, ConditionResult<R>> condition) {
        var time = new FakeTime(0);
        return new WaitEngine<Object, R>(config(1, 2, 0), time, time, source, Conditions.<Object, R>condition("test condition", actual -> {
            ConditionResult<R> evaluation = condition.apply(actual);
            time.advanceNanos(2);
            return evaluation;
        }).newSession()).waitFor().attempt();
    }
}

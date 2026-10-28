package io.github.gromoff97.awium;


import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.evaluated;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.uncontrolled;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.asserted;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryUntilFailureTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void singlePredicateAssertionStopsBothTerminalsWithTheOriginalCause() {
        var assertion = new AssertionError("predicate failed");
        int[] calls = {0};
        Predicate<String> predicate = actual -> {
            calls[0]++;
            throw assertion;
        };

        var result = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time, time),
                () -> matches(predicate::test));

        assertEquals(CONDITION_FAILED, result.failure().reason());
        assertSame(assertion, result.failure().cause());
        assertEquals(1, result.totalAttempts());
        assertEquals(2, calls[0]);
    }

    @Test
    void capturesAcquisitionTimeoutAndPersistenceFailureWithoutChangingThem() {
        var timeout = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time, time),
                () -> matches(actual -> false));
        var persistence = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 3, 2), time, time),
                TryUntilFailureTest::unstableCondition);

        assertInstanceOf(AttemptResult.Unsatisfied.class,
                evaluated(timeout.attempts().getLast()).result());
        assertInstanceOf(AttemptResult.Unsatisfied.class,
                evaluated(persistence.attempts().getLast()).result());
    }

    @Test
    void capturesSourceAndConditionFailuresWithoutChangingThem() {
        var source = assertParity(time -> timedAwait((Callable<Object>) () -> {
                    throw new IllegalStateException("source failed");
                }, config(1, 2, 0), time, time),
                () -> isNotNull);
        var sourceAssertion = assertParity(time -> timedAwait((Callable<Object>) () -> {
                    throw new AssertionError("source assertion");
                }, config(1, 2, 0), time, time),
                () -> isNotNull);
        var condition = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> condition("condition succeeds", actual -> {
                    throw new IllegalStateException("condition failed");
                }));
        var conditionAssertion = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> condition("condition succeeds", actual -> {
                    throw new AssertionError("condition assertion");
                }));

        uncontrolled(AttemptResult.Operation.SOURCE, source.attempts().getLast().result());
        uncontrolled(AttemptResult.Operation.SOURCE, sourceAssertion.attempts().getLast().result());
        uncontrolled(AttemptResult.Operation.CONDITION, condition.attempts().getLast().result());
        uncontrolled(AttemptResult.Operation.CONDITION, conditionAssertion.attempts().getLast().result());
    }

    @Test
    void capturesConditionFactoryFailuresInsideTheFirstAttempt() {
        var failure = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> conditionPerWait("condition succeeds", () -> {
                    throw new IllegalStateException("condition factory failed");
                }));
        var nullEvaluator = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> conditionPerWait("condition succeeds", () -> null));

        assertEquals(1, failure.totalAttempts());
        assertEquals(1, failure.attempts().getFirst().number());
        uncontrolled(AttemptResult.Operation.CONDITION, failure.attempts().getFirst().result());
        assertEquals("evaluator must not be null", nullEvaluator.failure().cause().getMessage());
    }

    @Test
    void assertedFailuresRemainControlled() {
        var timeout = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> asserted(actual -> { throw new AssertionError("assertion failed"); }));
        var persistence = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 3, 2), time, time),
                TryUntilFailureTest::unstableAssertion);

        assertInstanceOf(AttemptResult.Unsatisfied.class,
                evaluated(timeout.attempts().getLast()).result());
        assertInstanceOf(AttemptResult.Unsatisfied.class,
                evaluated(persistence.attempts().getLast()).result());
    }

    @Test
    void capturesParkingAndEveryInterruptionBoundary() {
        var acquisitionParking = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time,
                        nanos -> { throw new IllegalStateException("park failed"); }),
                () -> condition("never", actual -> unsatisfied("not ready")));
        var persistenceParking = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 2), time,
                        nanos -> { throw new IllegalStateException("park failed"); }),
                () -> isNotNull);

        assertEquals(2, acquisitionParking.totalAttempts());
        assertEquals(2, persistenceParking.totalAttempts());
        uncontrolled(AttemptResult.Operation.WAITING, acquisitionParking.attempts().getLast().result());
        uncontrolled(AttemptResult.Operation.WAITING, persistenceParking.attempts().getLast().result());
        var beforeSource = assertParity(time -> {
                    currentThread().interrupt();
                    return timedAwait(() -> "actual", config(1, 2, 0), time, time);
                }, () -> isNotNull);
        assertTrue(beforeSource.failure().message().startsWith("Caller thread was interrupted while waiting"));
        var thrownSourceInterruption = assertParity(time -> timedAwait((Callable<Object>) () -> {
                    throw new InterruptedException("source interrupted");
                }, config(1, 2, 0), time, time),
                () -> isNotNull);
        assertEquals(SOURCE_FAILED, thrownSourceInterruption.failure().reason());
        assertTrue(thrownSourceInterruption.failure().message().startsWith("Source retrieval failed"));
        var sourceFlag = assertParity(time -> timedAwait((Callable<String>) () -> {
                    currentThread().interrupt();
                    return "actual";
                }, config(1, 2, 0), time, time),
                () -> isNotNull);
        assertTrue(sourceFlag.failure().message().startsWith("Caller thread was interrupted during source retrieval"));
        assertTrue(sourceFlag.failure().message().contains("Actual: actual"));
        var condition = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time, time),
                () -> matches(actual -> {
                    currentThread().interrupt();
                    return true;
                }));

        uncontrolled(AttemptResult.Operation.CONDITION, condition.attempts().getLast().result());
        assertTrue(condition.failure().message().startsWith("Caller thread was interrupted during condition evaluation"));
        var interruptTime = new FakeTime(0);
        failed(timedAwait(() -> "actual", config(1, 2, 0),
                interruptTime, interruptTime).tryUntil(condition("condition succeeds", actual -> {
                    currentThread().interrupt();
                    return satisfied();
                })));
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void capturesNullEvaluationAndDiagnosticRenderingFailure() {
        var nullEvaluation = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time),
                () -> condition("condition succeeds", actual -> null));
        var diagnostics = assertParity(time -> timedAwait(
                        TryUntilFailureTest::brokenDiagnosticActual,
                        config(1, 2, 0), time, time),
                TryUntilFailureTest::brokenDiagnostics);

        uncontrolled(AttemptResult.Operation.CONDITION, nullEvaluation.attempts().getLast().result());
        assertEquals(1, diagnostics.failure().suppressed().size());
        assertInstanceOf(AssertionError.class, diagnostics.failure().cause());
        assertInstanceOf(IllegalStateException.class, diagnostics.failure().suppressed().getFirst());
    }

    @Test
    void nullEvaluationHasTheSamePriorityAcrossConditionFamiliesAndComposition() {
        for (boolean interrupt : List.of(false, true)) {
            CheckedFunction<String, ConditionResult> evaluation = actual -> {
                if (interrupt) currentThread().interrupt();
                return null;
            };
            var preserved = Conditions.condition("broken", evaluation);
            List<Supplier<AwaitResult<?, ?>>> paths = List.of(
                    () -> await(() -> "actual").tryUntil(preserved),
                    () -> await(() -> "actual").tryUntil(captured(preserved, preserved)),
                    () -> await(() -> Optional.of("actual")).tryUntil(OptionalConditions.hasValue(preserved)),
                    () -> await(() -> List.of("actual")).tryUntil(CollectionConditions.singleElement(preserved)),
                    () -> await(() -> Map.of("key", "actual")).tryUntil(MapConditions.valueFor("key", preserved)));
            for (var path : paths) {
                try {
                    var result = failed(path.get());
                    assertEquals(interrupt ? INTERRUPTED : CONDITION_FAILED, result.failure().reason());
                    assertInstanceOf(NullPointerException.class, result.failure().cause());
                    assertEquals("condition returned null ConditionResult", result.failure().cause().getMessage());
                    assertEquals(1, result.totalAttempts());
                    assertEquals(interrupt, currentThread().isInterrupted());
                } finally {
                    interrupted();
                }
            }
        }
    }

    @Test
    void capturedNullEvaluationRetainsCauseAndProgressWhenInterrupted() {
        var time = new FakeTime(0);

        var result = failed(timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(captured(condition("first stage", actual -> {
                    currentThread().interrupt();
                    return null;
                }),
                condition("second stage", actual -> satisfied()))));
        var observation = uncontrolled(AttemptResult.Operation.CONDITION, result.attempts().getFirst().result());
        var progress = result.attempts().getFirst().progress();

        assertEquals(INTERRUPTED, result.failure().reason());
        assertEquals(NullPointerException.class, observation.cause().getClass());
        assertEquals("condition returned null ConditionResult", observation.cause().getMessage());
        assertEquals(1, progress.stageNumber());
        assertEquals("first stage", result.attempts().getLast().condition().description());
    }

    @Test
    void capturedInterruptRetainsSequenceProgress() {
        var time = new FakeTime(0);

        var result = failed(timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(captured(condition("first stage", actual -> {
                    currentThread().interrupt();
                    return satisfied();
                }),
                condition("second stage", actual -> satisfied()))));
        uncontrolled(AttemptResult.Operation.CONDITION, result.attempts().getFirst().result());
        var progress = result.attempts().getFirst().progress();

        assertEquals(INTERRUPTED, result.failure().reason());
        assertEquals(0, progress.capturedStages());
        assertEquals(1, progress.stageNumber());
        assertEquals("first stage", result.attempts().getLast().condition().description());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void sourceWaitingAndDiagnosticErrorsAreCapturedUniformly() {
        var pollingTime = new FakeTime(0);
        var sourceFatal = new InternalError("fatal source");
        var parkerFatal = new InternalError("fatal parker");
        var diagnosticsFatal = new InternalError("fatal diagnostics");

        var source = failed(await((Callable<Object>) () -> { throw sourceFatal; })
                .usingTime(pollingTime, pollingTime).tryUntil(isNotNull));
        assertSame(sourceFatal, source.failure().cause());
        assertEquals(SOURCE_FAILED, source.failure().reason());
        var time = new FakeTime(0);
        var parking = failed(timedAwait(Object::new, config(1, 2, 0), time,
                nanos -> { throw parkerFatal; }).tryUntil(condition("never", actual -> unsatisfied("not ready"))));
        assertSame(parkerFatal, parking.failure().cause());
        assertEquals(WAIT_FAILED, parking.failure().reason());
        var diagnosticTime = new FakeTime(0);
        Object actual = new Object() {
            @Override
            public String toString() { throw diagnosticsFatal; }
        };
        var diagnostics = failed(timedAwait(() -> actual, config(1, 2, 0),
                diagnosticTime, diagnosticTime).tryUntil(brokenDiagnostics()));
        assertSame(diagnosticsFatal, diagnostics.failure().suppressed().getFirst());
    }

    private static <VALUE> AwaitResult.Failed<?, ?> assertParity(
            Function<FakeTime, ValueAwait<VALUE>> waiting,
            Supplier<PreservingCondition<? super VALUE>> condition) {
        Throwable thrown;
        try {
            thrown = assertThrows(Throwable.class,
                    () -> waiting.apply(new FakeTime(0)).until(condition.get()));
        } finally {
            interrupted();
        }

        try {
            var captured = failed(waiting.apply(new FakeTime(0)).tryUntil(condition.get()));
            assertSameFailure(thrown, captured.failure());
            return captured;
        } finally {
            interrupted();
        }
    }

    private static void assertSameFailure(Throwable thrown, AwaitFailure captured) {
        AwaitFailure attached = switch (thrown) {
            case AwaitAssertionError assertion -> assertion.failure();
            case AwaitExecutionException execution -> execution.failure();
            default -> throw new AssertionError("unexpected failure", thrown);
        };
        assertEquals(attached.reason(), captured.reason());
        assertEquals(thrown.getMessage(), captured.message());
        if (thrown.getCause() == null) {
            assertNull(captured.cause());
        } else {
            assertEquals(thrown.getCause().getClass(), captured.cause().getClass());
            assertEquals(thrown.getCause().getMessage(), captured.cause().getMessage());
        }
        assertEquals(thrown.getSuppressed().length, captured.suppressed().size());
        for (int i = 0; i < thrown.getSuppressed().length; i++) {
            assertEquals(thrown.getSuppressed()[i].getClass(), captured.suppressed().get(i).getClass());
            assertEquals(thrown.getSuppressed()[i].getMessage(), captured.suppressed().get(i).getMessage());
        }
    }

    private static AwaitResult.Failed<?, ?> failed(AwaitResult<?, ?> result) {
        return assertInstanceOf(AwaitResult.Failed.class, result);
    }

    private static PreservingCondition<Object> unstableCondition() {
        int[] calls = {0};
        return condition("value remains ready", actual -> calls[0]++ == 0
                ? satisfied() : unsatisfied("not ready"));
    }

    private static Condition.PreservingCondition<Object> unstableAssertion() {
        int[] calls = {0};
        return asserted(actual -> {
            if (calls[0]++ > 0) {
                throw new AssertionError("persistence assertion failed");
            }
        });
    }

    private static Object brokenDiagnosticActual() {
        return new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("description failed");
            }
        };
    }

    private static PreservingCondition<Object> brokenDiagnostics() {
        return condition("condition", actual ->
                unsatisfied("not ready", new AssertionError("engine")));
    }
}

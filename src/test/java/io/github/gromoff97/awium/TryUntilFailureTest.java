package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionResult.assertionUnsatisfied;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.uncontrolled;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.asserted;

import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.conditionFactory;
import static io.github.gromoff97.awium.Conditions.isNotNull;
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
    void capturesAcquisitionTimeoutAndPersistenceFailureWithoutChangingThem() {
        var timeout = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(condition("value is ready", actual -> unsatisfied("not ready"))),
                time -> timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(condition(
                        "value is ready", actual -> unsatisfied("not ready"))));
        var persistence = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 3, 2), time, time).until(unstableCondition()),
                time -> timedAwait(() -> "actual", config(1, 3, 2), time, time).tryUntil(unstableCondition()));

        assertInstanceOf(ConditionResult.Unsatisfied.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, timeout.attempts().getLast().outcome()).evaluation());
        assertInstanceOf(ConditionResult.Unsatisfied.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, persistence.attempts().getLast().outcome()).evaluation());
    }

    @Test
    void capturesSourceAndConditionFailuresWithoutChangingThem() {
        var source = assertParity(time -> timedAwait((Source<Object>) () -> {
                    throw new IllegalStateException("source failed");
                }, config(1, 2, 0), time, time).until(isNotNull),
                time -> timedAwait((Source<Object>) () -> {
                    throw new IllegalStateException("source failed");
                }, config(1, 2, 0), time, time).tryUntil(isNotNull));
        var sourceAssertion = assertParity(time -> timedAwait((Source<Object>) () -> {
                    throw new AssertionError("source assertion");
                }, config(1, 2, 0), time, time).until(isNotNull),
                time -> timedAwait((Source<Object>) () -> {
                    throw new AssertionError("source assertion");
                }, config(1, 2, 0), time, time).tryUntil(isNotNull));
        var condition = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(throwingCondition()),
                time -> timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(throwingCondition()));
        var conditionAssertion = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(assertionThrowingCondition()),
                time -> timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(assertionThrowingCondition()));

        assertInstanceOf(AwaitAttempt.Outcome.SourceRetrievalFailed.class,
                source.attempts().getLast().outcome());
        assertInstanceOf(AwaitAttempt.Outcome.SourceRetrievalFailed.class,
                sourceAssertion.attempts().getLast().outcome());
        assertInstanceOf(ConditionResult.Uncontrolled.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, condition.attempts().getLast().outcome()).evaluation());
        assertInstanceOf(ConditionResult.Uncontrolled.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, conditionAssertion.attempts().getLast().outcome()).evaluation());
    }

    @Test
    void capturesConditionFactoryFailuresInsideTheFirstAttempt() {
        var failure = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(failingFactory()),
                time -> timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(failingFactory()));
        var nullEvaluator = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(nullFactory()),
                time -> timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(nullFactory()));

        assertEquals(1, failure.totalAttempts());
        assertEquals(1, failure.attempts().getFirst().number());
        assertInstanceOf(ConditionResult.Uncontrolled.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, failure.attempts().getFirst().outcome()).evaluation());
        assertEquals("evaluator must not be null", nullEvaluator.failure().cause().getMessage());
    }

    @Test
    void assertedFailuresRemainControlled() {
        var timeout = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(failingAssertion()),
                time -> timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(failingAssertion()));
        var persistence = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 3, 2), time, time).until(unstableAssertion()),
                time -> timedAwait(() -> "actual", config(1, 3, 2), time, time).tryUntil(unstableAssertion()));

        assertInstanceOf(ConditionResult.Unsatisfied.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, timeout.attempts().getLast().outcome()).evaluation());
        assertInstanceOf(ConditionResult.Unsatisfied.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, persistence.attempts().getLast().outcome()).evaluation());
    }

    @Test
    void capturesParkingAndEveryInterruptionBoundary() {
        var acquisitionParking = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time,
                        nanos -> { throw new IllegalStateException("park failed"); }).until(neverReady()),
                time -> timedAwait(() -> "actual", config(1, 2, 0), time,
                        nanos -> { throw new IllegalStateException("park failed"); }).tryUntil(neverReady()));
        var persistenceParking = assertParity(time -> timedAwait(() -> "actual", config(1, 2, 2), time,
                        nanos -> { throw new IllegalStateException("park failed"); }).until(isNotNull),
                time -> timedAwait(() -> "actual", config(1, 2, 2), time,
                        nanos -> { throw new IllegalStateException("park failed"); }).tryUntil(isNotNull));

        assertEquals(2, acquisitionParking.totalAttempts());
        assertEquals(2, persistenceParking.totalAttempts());
        assertInstanceOf(AwaitAttempt.Outcome.WaitingFailed.class,
                acquisitionParking.attempts().getLast().outcome());
        assertInstanceOf(AwaitAttempt.Outcome.WaitingFailed.class,
                persistenceParking.attempts().getLast().outcome());
        assertParity(time -> {
                    currentThread().interrupt();
                    timedAwait(() -> "actual", config(1, 2, 0), time, time).until(isNotNull);
                }, time -> {
                    currentThread().interrupt();
                    return timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(isNotNull);
                });
        assertParity(time -> timedAwait((Source<Object>) () -> {
                    throw new InterruptedException("source interrupted");
                }, config(1, 2, 0), time, time).until(isNotNull),
                time -> timedAwait((Source<Object>) () -> {
                    throw new InterruptedException("source interrupted");
                }, config(1, 2, 0), time, time).tryUntil(isNotNull));
        assertParity(time -> timedAwait((Source<String>) () -> {
                    currentThread().interrupt();
                    return "actual";
                }, config(1, 2, 0), time, time).until(isNotNull),
                time -> timedAwait((Source<String>) () -> {
                    currentThread().interrupt();
                    return "actual";
                }, config(1, 2, 0), time, time).tryUntil(isNotNull));
        var condition = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(interruptingCondition()),
                time -> timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(interruptingCondition()));

        assertInstanceOf(ConditionResult.Uncontrolled.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, condition.attempts().getLast().outcome()).evaluation());
        var interruptTime = new FakeTime(0);
        failed(timedAwait(() -> "actual", config(1, 2, 0),
                interruptTime, interruptTime).tryUntil(interruptingCondition()));
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void capturesNullEvaluationAndDiagnosticRenderingFailure() {
        var nullEvaluation = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(nullEvaluation()),
                time -> timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(nullEvaluation()));
        var diagnostics = assertParity(time -> timedAwait(
                        TryUntilFailureTest::brokenDiagnosticActual,
                        config(1, 2, 0), time, time).until(brokenDiagnostics()),
                time -> timedAwait(TryUntilFailureTest::brokenDiagnosticActual,
                        config(1, 2, 0), time, time).tryUntil(brokenDiagnostics()));

        assertInstanceOf(ConditionResult.Uncontrolled.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, nullEvaluation.attempts().getLast().outcome()).evaluation());
        assertEquals(1, diagnostics.failure().suppressed().size());
        assertInstanceOf(AssertionError.class, diagnostics.failure().suppressed().getFirst());
    }

    @Test
    void capturedNullEvaluationWinsOverInterruptWithSequenceContext() {
        var time = new FakeTime(0);

        var result = failed(timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(condition("first stage", actual -> {
                    currentThread().interrupt();
                    return null;
                }),
                condition("second stage", actual -> satisfied(actual))));
        var outcome = assertInstanceOf(ConditionResult.Uncontrolled.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, result.attempts().getFirst().outcome()).evaluation());
        var context = assertInstanceOf(ConditionResult.Context.Sequence.class, outcome.context());

        assertEquals(CONDITION_FAILED, result.failure().reason());
        assertEquals(NullPointerException.class, outcome.cause().getClass());
        assertEquals("condition returned null ConditionResult", outcome.cause().getMessage());
        assertEquals(1, context.evaluatedStageNumber());
        assertEquals("first stage", context.expectation());
    }

    @Test
    void capturedInterruptRetainsSequenceContext() {
        var time = new FakeTime(0);

        var result = failed(timedAwait(() -> "actual", config(1, 2, 0), time, time).tryUntil(condition("first stage", actual -> {
                    currentThread().interrupt();
                    return satisfied(actual);
                }),
                condition("second stage", actual -> satisfied(actual))));
        var outcome = assertInstanceOf(ConditionResult.Uncontrolled.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, result.attempts().getFirst().outcome()).evaluation());
        var context = assertInstanceOf(ConditionResult.Context.Sequence.class, outcome.context());

        assertEquals(INTERRUPTED, result.failure().reason());
        assertEquals(1, context.evaluatedStageNumber());
        assertEquals("second stage", context.expectation());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    @SuppressWarnings("removal")
    void fatalSignalsEscapeDiagnosticExecutionUnchanged() {
        var pollingTime = new FakeTime(0);
        var sourceFatal = new InternalError("fatal source");
        var conditionFatal = new ThreadDeath();
        var parkerFatal = new InternalError("fatal parker");
        var evaluationFatal = new InternalError("fatal evaluation");
        var diagnosticsFatal = new InternalError("fatal diagnostics");

        assertSame(sourceFatal, assertThrows(InternalError.class,
                () -> await((Source<Object>) () -> { throw sourceFatal; }).usingTime(pollingTime, pollingTime).tryUntil(isNotNull)));
        assertSame(conditionFatal, assertThrows(ThreadDeath.class,
                () -> await((Source<Object>) Object::new).usingTime(pollingTime, pollingTime).tryUntil(condition("fatal", actual -> { throw conditionFatal; }))));
        var time = new FakeTime(0);
        assertSame(parkerFatal, assertThrows(InternalError.class,
                () -> timedAwait(Object::new, config(1, 2, 0), time,
                        nanos -> { throw parkerFatal; }).tryUntil(condition("never", actual -> unsatisfied("not ready")))));
        assertSame(evaluationFatal, assertThrows(InternalError.class,
                () -> await((Source<Object>) Object::new).usingTime(pollingTime, pollingTime).tryUntil(condition("fatal", actual -> uncontrolled(evaluationFatal)))));
        assertSame(diagnosticsFatal, assertThrows(InternalError.class, () -> {
            var diagnosticTime = new FakeTime(0);
            Object actual = new Object() {
                    @Override
                    public String toString() {
                        throw diagnosticsFatal;
                    }
                };
            timedAwait(() -> actual, config(1, 2, 0),
                    diagnosticTime, diagnosticTime).tryUntil(brokenDiagnostics());
        }));
    }

    private static AwaitResult.Failed<?, ?> assertParity(
            Ordinary ordinary, Diagnostic diagnostic) {
        Throwable thrown;
        try {
            thrown = assertThrows(Throwable.class, () -> ordinary.run(new FakeTime(0)));
        } finally {
            interrupted();
        }

        try {
            var captured = failed(diagnostic.run(new FakeTime(0)));
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
        return (AwaitResult.Failed<?, ?>) assertInstanceOf(AwaitResult.Failed.class, result);
    }

    private static Condition<Object, Object> unstableCondition() {
        int[] calls = {0};
        return condition("value remains ready", actual -> calls[0]++ == 0
                ? satisfied(actual) : unsatisfied("not ready"));
    }

    private static Condition<Object, Object> throwingCondition() {
        return condition("condition succeeds", actual -> {
            throw new IllegalStateException("condition failed");
        });
    }

    private static Condition<Object, Object> neverReady() {
        return condition("never", actual -> unsatisfied("not ready"));
    }

    private static Condition<Object, Object> failingFactory() {
        return conditionFactory("condition succeeds", () -> {
            throw new IllegalStateException("condition factory failed");
        });
    }

    private static Condition<Object, Object> nullFactory() {
        return conditionFactory("condition succeeds", () -> null);
    }

    private static Condition<Object, Object> assertionThrowingCondition() {
        return condition("condition succeeds", actual -> {
            throw new AssertionError("condition assertion");
        });
    }

    private static Condition.PreservingCondition<Object> failingAssertion() {
        return asserted(actual -> { throw new AssertionError("assertion failed"); });
    }

    private static Condition.PreservingCondition<Object> unstableAssertion() {
        int[] calls = {0};
        return asserted(actual -> {
            if (calls[0]++ > 0) {
                throw new AssertionError("persistence assertion failed");
            }
        });
    }

    private static Condition<Object, Object> interruptingCondition() {
        return condition("condition succeeds", actual -> {
            currentThread().interrupt();
            return satisfied(actual);
        });
    }

    private static Condition<Object, Object> nullEvaluation() {
        return condition("condition succeeds", actual -> null);
    }

    private static Object brokenDiagnosticActual() {
        return new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("description failed");
            }
        };
    }

    private static Condition<Object, Object> brokenDiagnostics() {
        return condition("condition", actual ->
                assertionUnsatisfied("not ready", new AssertionError("engine")));
    }

    private static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence);
    }

    @FunctionalInterface
    private interface Ordinary {
        void run(FakeTime time);
    }

    @FunctionalInterface
    private interface Diagnostic {
        AwaitResult<?, ?> run(FakeTime time);
    }
}

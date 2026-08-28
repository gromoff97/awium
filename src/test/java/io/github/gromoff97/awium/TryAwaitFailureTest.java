package io.github.gromoff97.awium;

import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.results.AwaitResult;
import io.github.gromoff97.awium.fluent.Condition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.fluent.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.fluent.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.fluent.Await.tryAwait;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.uncontrolled;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.fluent.Conditions.asserted;
import static io.github.gromoff97.awium.fluent.Conditions.condition;
import static io.github.gromoff97.awium.fluent.Conditions.isNotNull;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryAwaitFailureTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void capturesAcquisitionTimeoutAndPersistenceFailureWithoutChangingThem() {
        var timeout = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time)
                        .until(condition("value is ready", actual -> unsatisfied("not ready"))),
                time -> timedTryAwait(() -> "actual", config(1, 2, 0), time, time)
                        .until(condition("value is ready", actual -> unsatisfied("not ready"))));
        var persistence = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 3, 2), time, time).until(unstableCondition()),
                time -> timedTryAwait(() -> "actual", config(1, 3, 2), time, time)
                        .until(unstableCondition()));

        assertInstanceOf(AwaitAttempt.Outcome.Unsatisfied.class,
                timeout.attempts().getLast().outcome());
        assertInstanceOf(AwaitAttempt.Outcome.Unsatisfied.class,
                persistence.attempts().getLast().outcome());
    }

    @Test
    void capturesSourceAndConditionFailuresWithoutChangingThem() {
        var source = assertParity(time -> timedAwait((Source<Object>) () -> {
                    throw new IllegalStateException("source failed");
                }, config(1, 2, 0), time, time).until(isNotNull),
                time -> timedTryAwait((Source<Object>) () -> {
                    throw new IllegalStateException("source failed");
                }, config(1, 2, 0), time, time).until(isNotNull));
        var sourceAssertion = assertParity(time -> timedAwait((Source<Object>) () -> {
                    throw new AssertionError("source assertion");
                }, config(1, 2, 0), time, time).until(isNotNull),
                time -> timedTryAwait((Source<Object>) () -> {
                    throw new AssertionError("source assertion");
                }, config(1, 2, 0), time, time).until(isNotNull));
        var condition = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(throwingCondition()),
                time -> timedTryAwait(() -> "actual", config(1, 2, 0), time, time)
                        .until(throwingCondition()));
        var conditionAssertion = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(assertionThrowingCondition()),
                time -> timedTryAwait(() -> "actual", config(1, 2, 0), time, time)
                        .until(assertionThrowingCondition()));

        assertInstanceOf(AwaitAttempt.Outcome.SourceRetrievalFailed.class,
                source.attempts().getLast().outcome());
        assertInstanceOf(AwaitAttempt.Outcome.SourceRetrievalFailed.class,
                sourceAssertion.attempts().getLast().outcome());
        assertInstanceOf(AwaitAttempt.Outcome.ConditionEvaluationFailed.class,
                condition.attempts().getLast().outcome());
        assertInstanceOf(AwaitAttempt.Outcome.ConditionEvaluationFailed.class,
                conditionAssertion.attempts().getLast().outcome());
    }

    @Test
    void assertedFailuresRemainControlled() {
        var timeout = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(failingAssertion()),
                time -> timedTryAwait(() -> "actual", config(1, 2, 0), time, time)
                        .until(failingAssertion()));
        var persistence = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 3, 2), time, time).until(unstableAssertion()),
                time -> timedTryAwait(() -> "actual", config(1, 3, 2), time, time)
                        .until(unstableAssertion()));

        assertInstanceOf(AwaitAttempt.Outcome.Unsatisfied.class,
                timeout.attempts().getLast().outcome());
        assertInstanceOf(AwaitAttempt.Outcome.Unsatisfied.class,
                persistence.attempts().getLast().outcome());
    }

    @Test
    void capturesParkingAndEveryInterruptionBoundary() {
        assertParity(time -> timedAwait(() -> "actual", config(1, 2, 0), time,
                        nanos -> { throw new IllegalStateException("park failed"); })
                        .until(condition("never", actual -> unsatisfied("not ready"))),
                time -> timedTryAwait(() -> "actual", config(1, 2, 0), time,
                        nanos -> { throw new IllegalStateException("park failed"); })
                        .until(condition("never", actual -> unsatisfied("not ready"))));
        assertParity(time -> {
                    currentThread().interrupt();
                    timedAwait(() -> "actual", config(1, 2, 0), time, time).until(isNotNull);
                }, time -> {
                    currentThread().interrupt();
                    return timedTryAwait(() -> "actual", config(1, 2, 0), time, time)
                            .until(isNotNull);
                });
        assertParity(time -> timedAwait((Source<Object>) () -> {
                    throw new InterruptedException("source interrupted");
                }, config(1, 2, 0), time, time).until(isNotNull),
                time -> timedTryAwait((Source<Object>) () -> {
                    throw new InterruptedException("source interrupted");
                }, config(1, 2, 0), time, time).until(isNotNull));
        assertParity(time -> timedAwait((Source<String>) () -> {
                    currentThread().interrupt();
                    return "actual";
                }, config(1, 2, 0), time, time).until(isNotNull),
                time -> timedTryAwait((Source<String>) () -> {
                    currentThread().interrupt();
                    return "actual";
                }, config(1, 2, 0), time, time).until(isNotNull));
        var condition = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(interruptingCondition()),
                time -> timedTryAwait(() -> "actual", config(1, 2, 0), time, time)
                        .until(interruptingCondition()));

        assertInstanceOf(AwaitAttempt.Outcome.ConditionEvaluationFailed.class,
                condition.attempts().getLast().outcome());
        var interruptTime = new FakeTime(0);
        failed(timedTryAwait(() -> "actual", config(1, 2, 0),
                interruptTime, interruptTime).until(interruptingCondition()));
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void capturesNullEvaluationAndDiagnosticRenderingFailure() {
        var nullEvaluation = assertParity(time -> timedAwait(() -> "actual",
                        config(1, 2, 0), time, time).until(nullEvaluation()),
                time -> timedTryAwait(() -> "actual", config(1, 2, 0), time, time)
                        .until(nullEvaluation()));
        var diagnostics = assertParity(time -> timedAwait(
                        TryAwaitFailureTest::brokenDiagnosticActual,
                        config(1, 2, 0), time, time).until(brokenDiagnostics()),
                time -> timedTryAwait(TryAwaitFailureTest::brokenDiagnosticActual,
                        config(1, 2, 0), time, time).until(brokenDiagnostics()));

        assertInstanceOf(AwaitAttempt.Outcome.ConditionEvaluationFailed.class,
                nullEvaluation.attempts().getLast().outcome());
        assertEquals(1, diagnostics.failure().getSuppressed().length);
        assertInstanceOf(AssertionError.class, diagnostics.failure().getSuppressed()[0]);
    }

    @Test
    @SuppressWarnings("removal")
    void fatalSignalsEscapeDiagnosticExecutionUnchanged() {
        var sourceFatal = new InternalError("fatal source");
        var conditionFatal = new ThreadDeath();
        var parkerFatal = new InternalError("fatal parker");
        var evaluationFatal = new InternalError("fatal evaluation");
        var diagnosticsFatal = new InternalError("fatal diagnostics");

        assertSame(sourceFatal, assertThrows(InternalError.class,
                () -> tryAwait((Source<Object>) () -> { throw sourceFatal; }).until(isNotNull)));
        assertSame(conditionFatal, assertThrows(ThreadDeath.class,
                () -> tryAwait((Source<Object>) Object::new)
                        .until(condition("fatal", actual -> { throw conditionFatal; }))));
        var time = new FakeTime(0);
        assertSame(parkerFatal, assertThrows(InternalError.class,
                () -> timedTryAwait(Object::new, config(1, 2, 0), time,
                        nanos -> { throw parkerFatal; })
                        .until(condition("never", actual -> unsatisfied("not ready")))));
        assertSame(evaluationFatal, assertThrows(InternalError.class,
                () -> tryAwait((Source<Object>) Object::new)
                        .until(condition("fatal", actual -> uncontrolled(evaluationFatal)))));
        assertSame(diagnosticsFatal, assertThrows(InternalError.class, () -> {
            var diagnosticTime = new FakeTime(0);
            Object actual = new Object() {
                    @Override
                    public String toString() {
                        throw diagnosticsFatal;
                    }
                };
            timedTryAwait(() -> actual, config(1, 2, 0),
                    diagnosticTime, diagnosticTime).until(brokenDiagnostics());
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

    private static void assertSameFailure(Throwable thrown, Throwable captured) {
        assertEquals(thrown.getClass(), captured.getClass());
        assertEquals(thrown.getMessage(), captured.getMessage());
        if (thrown.getCause() == null) {
            assertNull(captured.getCause());
            return;
        }
        assertEquals(thrown.getCause().getClass(), captured.getCause().getClass());
        assertEquals(thrown.getCause().getMessage(), captured.getCause().getMessage());
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

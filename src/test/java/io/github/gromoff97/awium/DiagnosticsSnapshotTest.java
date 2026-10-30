package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.internal.WaitCompletion;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.internal.ExecutionStep;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import java.io.IOException;
import java.time.Duration;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.notEqualTo;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.internal.WaitCompletion.*;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosticsSnapshotTest {

    private final FakeTime time = new FakeTime(0);

    private static final Condition.Typed<Object, Object> CONDITION = diagnosticCondition("condition", null);
    private static final long SECOND = 1_000_000_000L;
    private static final long MILLISECOND = 1_000_000L;

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void builtInOperandIsRetainedInDiagnostics() {
        var condition = equalTo("expected");

        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> "actual", config(1, 2, 0), time, time).until(condition));

        assertThat(failure).hasMessageContainingAll("Condition: value equals expected",
                "Expected: \"expected\"");
    }

    @Test
    void capturedBuiltInOperandIdentifiesTheCurrentStage() {
        var condition = captured(equalTo("first"), equalTo("second"));

        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> "actual", config(1, 2, 0), time, time).until(condition));

        assertThat(failure).hasMessageContainingAll("Sequence: captured 0/2",
                "stage 1/2",
                "Condition: value equals expected",
                "Expected: \"first\"");
    }

    @Test
    void capturedAcquisitionTimeoutRendersTheCurrentStage() {
        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> "created", config(1, 2, 0), time, time).until(lifecycle(pending(), completed())));

        assertEquals("""
                Wait timed out
                Attempt: 2 (ACQUISITION)
                Sequence: captured 1/3; stage 2/3

                Condition: payment status is pending
                    Actual: "created"
                    Mismatch: payment status was created

                Timing: elapsed 2 ns; timeout 2 ns; interval 1 ns
                Last completed after: 1 ns""", failure.getMessage());
    }

    @Test
    void timeoutAfterIntermediateSuccessPointsToTheNextCondition() {
        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> "created", config(1, 1, 0), time, time)
                        .until(captured(equalTo("created"), equalTo("pending"), equalTo("completed"))));

        assertThat(failure).hasMessageContainingAll("Sequence: captured 1/3",
                "stage 1/3",
                "Expected: \"created\"",
                "Next stage 2/3:",
                "Expected: \"pending\"");
    }

    @Test
    void capturedPersistenceFailureKeepsTheFinalStage() {
        String[] statuses = {"created", "pending", "completed", "created"};
        int[] next = {0};

        var failure = assertFailure(PERSISTENCE_FAILED,
                () -> timedAwait(() -> statuses[next[0]++],
                        config(1, 10, 5), time, time).until(lifecycle(pending(), completed())));

        assertEquals("""
                Condition did not stay satisfied
                Attempt: 4 (PERSISTENCE)
                Sequence: captured 2/3; stage 3/3

                Condition: payment status is completed
                    Actual: "created"
                    Mismatch: payment status was created

                Timing: elapsed 3 ns; timeout 10 ns; interval 1 ns
                Persistence: failed after 1 ns; required 5 ns; acquired after 2 ns""", failure.getMessage());
    }

    @Test
    void capturedNestedAssertionRetainsCauseAndStack() {
        var assertion = new AssertionError("pending assertion failed");
        var frame = new StackTraceElement("Payments", "verifyPending",
                "Payments.java", 42);
        assertion.setStackTrace(new StackTraceElement[]{frame});
        PreservingCondition<String> assertedPending = Conditions.<String>condition(
                "payment status is pending", value -> unsatisfied(
                        "payment status was created", assertion));

        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> "created", config(1, 2, 0), time, time).until(lifecycle(assertedPending, completed())));

        assertEquals("""
                Wait timed out
                Attempt: 2 (ACQUISITION)
                Sequence: captured 1/3; stage 2/3

                Condition: payment status is pending
                    Actual: "created"
                    Mismatch: payment status was created

                Timing: elapsed 2 ns; timeout 2 ns; interval 1 ns
                Last completed after: 1 ns
                Cause: java.lang.AssertionError: pending assertion failed""", failure.getMessage());
        assertSame(assertion, failure.getCause());
        assertArrayEquals(new StackTraceElement[]{frame}, failure.getCause().getStackTrace());
    }

    @Test
    void capturedNestedUncontrolledRetainsCauseAndStage() {
        var cause = new IllegalStateException("status callback failed");
        PreservingCondition<String> brokenPending = Conditions.<String>condition(
                "payment status is pending", value -> {
                    throw cause;
                });

        var failure = assertFailure(CONDITION_FAILED,
                () -> timedAwait(() -> "created", config(1, 10, 0), time, time).until(lifecycle(brokenPending, completed())));

        assertEquals("""
                Condition evaluation failed
                Attempt: 2 (ACQUISITION)
                Sequence: captured 1/3; stage 2/3

                Condition: payment status is pending
                    Actual: "created"

                Timing: elapsed 1 ns; timeout 10 ns; interval 1 ns
                Cause: java.lang.IllegalStateException: status callback failed""", failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    @Test
    void controlledFailuresRetainTheirSemanticContext() {
        var betweenAssertion = new AssertionError("collection was empty");
        var persistenceAssertion = new AssertionError("optional was empty");
        WaitCompletion between = new Timeout(
                10 * SECOND, evaluatedAttempt(diagnosticCondition("collection to be non-empty", null),
                        null, unsatisfied("collection was empty",
                        betweenAssertion), 9 * SECOND));
        WaitCompletion lateUnsatisfied = new Timeout(
                evaluatedAttempt(diagnosticCondition("status equals COMPLETED", null), "Payment[PENDING]", unsatisfied("status was PENDING"), 10 * SECOND + 200 * MILLISECOND));
        WaitCompletion lateSatisfied = new Timeout(
                evaluatedAttempt(diagnosticCondition("status equals COMPLETED", null), "Payment[COMPLETED]", satisfied(),
                        10 * SECOND + 200 * MILLISECOND));
        var persistedAttempt = evaluatedAttempt(diagnosticCondition("optional to remain present", null),
                "Optional.empty", unsatisfied("optional was empty",
                persistenceAssertion), 9 * SECOND + 100 * MILLISECOND);
        var persistence = new PersistenceFailure(7 * SECOND,
                new ExecutionStep<>(persistedAttempt.definition(), 1, 0, ExecutionStep.Phase.PERSISTENCE,
                        persistedAttempt.completedAfterNanos(), persistedAttempt.attempt()));

        var betweenFailure = assertFailure(TIMEOUT,
                () -> AwaitSupport.complete(between, config(3 * SECOND, 10 * SECOND, 0), 4));
        var lateUnsatisfiedFailure = assertFailure(TIMEOUT,
                () -> AwaitSupport.complete(lateUnsatisfied, config(100 * MILLISECOND, 10 * SECOND, 0), 100));
        var lateSatisfiedFailure = assertFailure(TIMEOUT,
                () -> AwaitSupport.complete(lateSatisfied, config(100 * MILLISECOND, 10 * SECOND, 0), 100));
        var persistenceFailure = assertFailure(PERSISTENCE_FAILED,
                () -> AwaitSupport.complete(persistence, config(100 * MILLISECOND, 10 * SECOND, 5 * SECOND), 71));

        assertEquals("""
                Wait timed out
                Attempt: 4 (ACQUISITION)

                Condition: collection to be non-empty
                    Actual: null
                    Mismatch: collection was empty

                Timing: elapsed 10 s; timeout 10 s; interval 3 s
                Last completed after: 9 s
                Cause: java.lang.AssertionError: collection was empty""", betweenFailure.getMessage());
        assertEquals("""
                Wait timed out
                Attempt: 100 (ACQUISITION)

                Condition: status equals COMPLETED
                    Actual: "Payment[PENDING]"
                    Mismatch: status was PENDING

                Timing: elapsed 10 s 200 ms; timeout 10 s; interval 100 ms""",
                lateUnsatisfiedFailure.getMessage());
        assertEquals("""
                Wait timed out
                Attempt: 100 (ACQUISITION)

                Condition: status equals COMPLETED
                    Actual: "Payment[COMPLETED]"

                Timing: elapsed 10 s 200 ms; timeout 10 s; interval 100 ms""",
                lateSatisfiedFailure.getMessage());
        assertEquals("""
                Condition did not stay satisfied
                Attempt: 71 (PERSISTENCE)

                Condition: optional to remain present
                    Actual: "Optional.empty"
                    Mismatch: optional was empty

                Timing: elapsed 9 s 100 ms; timeout 10 s; interval 100 ms
                Persistence: failed after 2 s 100 ms; required 5 s; acquired after 7 s
                Cause: java.lang.AssertionError: optional was empty""", persistenceFailure.getMessage());
        assertSame(betweenAssertion, betweenFailure.getCause());
        assertSame(persistenceAssertion, persistenceFailure.getCause());
    }

    @Test
    void uncontrolledFailuresKeepCategoryCauseAttemptAndObservation() {
        var sourceCause = new IllegalStateException("source failed");
        var conditionCause = new IllegalArgumentException("condition failed");
        var waitingCause = new IllegalStateException("waiting failed");
        var interruption = new InterruptedException("stopped");

        var sourceFailure = assertFailure(SOURCE_FAILED,
                () -> AwaitSupport.complete(sourceFailure(diagnosticCondition("condition", null), sourceCause, SECOND),
                        config(SECOND, 10 * SECOND, 0), 2));
        var conditionFailure = assertFailure(CONDITION_FAILED,
                () -> AwaitSupport.complete(conditionFailure(diagnosticCondition("condition", null),
                        "actual", conditionCause, SECOND), config(SECOND, 10 * SECOND, 0), 3));
        var waitingFailure = assertFailure(WAIT_FAILED,
                () -> AwaitSupport.complete(waitingFailure(waitingCause, SECOND), config(SECOND, 10 * SECOND, 0), 4));
        var interruptedFailure = assertFailure(WAIT_FAILED,
                () -> AwaitSupport.complete(waitingFailure(interruption, SECOND), config(SECOND, 10 * SECOND, 0), 5));

        assertSame(sourceCause, sourceFailure.getCause());
        assertSame(conditionCause, conditionFailure.getCause());
        assertSame(waitingCause, waitingFailure.getCause());
        assertSame(interruption, interruptedFailure.getCause());
        assertEquals("""
                Source retrieval failed
                Attempt: 2 (ACQUISITION)

                Condition: condition

                Timing: elapsed 1 s; timeout 10 s; interval 1 s
                Cause: java.lang.IllegalStateException: source failed""", sourceFailure.getMessage());
        assertEquals("""
                Condition evaluation failed
                Attempt: 3 (ACQUISITION)

                Condition: condition
                    Actual: "actual"

                Timing: elapsed 1 s; timeout 10 s; interval 1 s
                Cause: java.lang.IllegalArgumentException: condition failed""", conditionFailure.getMessage());
        assertEquals("""
                Waiting failed
                Attempt: 4 (ACQUISITION)

                Condition: condition

                Timing: elapsed 1 s; timeout 10 s; interval 1 s
                Cause: java.lang.IllegalStateException: waiting failed""", waitingFailure.getMessage());
        assertEquals("""
                Waiting failed
                Attempt: 5 (ACQUISITION)

                Condition: condition

                Timing: elapsed 1 s; timeout 10 s; interval 1 s
                Cause: java.lang.InterruptedException: stopped""", interruptedFailure.getMessage());
    }

    @Test
    void thrownConditionFailureRetainsInterruptFlagAfterDiagnosticsClearIt() {
        var cause = new IllegalStateException("condition failed") {
            @Override
            public String getMessage() {
                assertTrue(interrupted());
                return super.getMessage();
            }
        };
        PreservingCondition<Object> condition = Conditions.condition("condition", actual -> {
            currentThread().interrupt();
            throw cause;
        });

        var failure = assertFailure(INTERRUPTED,
                () -> await(Object::new).usingTime(time, time).until(condition));

        assertSame(cause, failure.getCause());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void assertionCauseIsRetainedAndItsMessageIsReadOnce() {
        var assertion = new CountingAssertion("assertion failed");
        WaitCompletion outcome = new Timeout(
                evaluatedAttempt(CONDITION, "actual", unsatisfied("business mismatch", assertion), 3 * SECOND));

        var failure = assertFailure(TIMEOUT,
                () -> AwaitSupport.complete(outcome, config(SECOND, 2 * SECOND, 0), 2));

        assertSame(assertion, failure.getCause());
        assertEquals("""
                Wait timed out
                Attempt: 2 (ACQUISITION)

                Condition: condition
                    Actual: "actual"
                    Mismatch: business mismatch

                Timing: elapsed 3 s; timeout 2 s; interval 1 s
                Cause: io.github.gromoff97.awium.DiagnosticsSnapshotTest$CountingAssertion: assertion failed""", failure.getMessage());
        assertEquals(1, assertion.calls);
    }

    @Test
    void causeMessagesRemainCompleteInBothTerminals() {
        for (String message : List.of("x".repeat(1024), "detailed assertion 😀 ".repeat(32))) {
            var cause = new IllegalStateException(message);
            Callable<String> source = () -> { throw cause; };
            var waiting = await(source);

            var captured = failure(diagnostic(waiting).until(Conditions.isNotNull));
            var thrown = assertThrows(AwaitExecutionException.class,
                    () -> waiting.until(Conditions.isNotNull));

            assertThat(captured.message()).contains("java.lang.IllegalStateException: " + message);
            assertThat(thrown.getMessage()).contains("java.lang.IllegalStateException: " + message);
            assertSame(cause, captured.cause());
            assertSame(cause, thrown.getCause());
            assertEquals(message, cause.getMessage());
        }
    }

    @Test
    void blankCauseMessagesAreOmittedAndReadOnlyOnce() {
        for (boolean capture : List.of(false, true)) {
            for (String message : new String[]{null, "", " \r\n\t"}) {
                int[] reads = {0};
                var cause = new IllegalStateException(message) {
                    @Override
                    public String getMessage() {
                        reads[0]++;
                        return super.getMessage();
                    }
                };
                var time = new FakeTime(0);
                Callable<Object> source = () -> { throw cause; };
                var waiting = await(source).usingTime(time, time);
                AwaitFailure failure = capture
                        ? failure(diagnostic(waiting).until(Conditions.isNotNull))
                        : assertThrows(AwaitExecutionException.class, () -> waiting.until(Conditions.isNotNull)).failure();

                assertEquals(SOURCE_FAILED, failure.reason());
                assertSame(cause, failure.cause());
                assertThat(failure.message()).endsWith("Cause: " + cause.getClass().getName());
                assertEquals(1, reads[0]);
            }
        }
    }

    @Test
    void diagnosticRenderingFailuresPreserveTheTimeoutWithoutACause() {
        var valueCause = new IllegalArgumentException("toString failed");
        var actual = new ThrowingValue(valueCause);
        WaitCompletion outcome = new Timeout(
                evaluatedAttempt(diagnosticCondition("condition", null), actual, unsatisfied("not ready"), 2));

        var valueFailure = assertFailure(TIMEOUT,
                () -> AwaitSupport.complete(outcome, config(1, 2, 0), 1));

        assertNull(valueFailure.getCause());
        assertArrayEquals(new Throwable[]{valueCause}, valueFailure.getSuppressed());
        assertEquals("""
                Wait timed out
                Attempt: 1 (ACQUISITION)

                Condition: condition
                    Actual: <unprintable>
                    Mismatch: not ready

                Timing: elapsed 2 ns; timeout 2 ns; interval 1 ns
                Rendering failure: java.lang.IllegalArgumentException""", valueFailure.getMessage());
        assertEquals(1, actual.calls);
    }

    @Test
    void diagnosticThrowableDoesNotSetTheInterruptFlag() {
        var interruption = new InterruptedException("diagnostics interrupted");
        Object actual = new Object() {
            @Override
            public String toString() {
                return rethrow(interruption);
            }
        };

        var failure = assertFailure(TIMEOUT,
                () -> AwaitSupport.complete(new Timeout(evaluatedAttempt(CONDITION, actual, unsatisfied("not ready"), 2)), config(1, 2, 0), 1));

        assertNull(failure.getCause());
        assertArrayEquals(new Throwable[]{interruption}, failure.getSuppressed());
        assertFalse(currentThread().isInterrupted());
    }

    @Test
    void expectedOperandRenderingFailureKeepsItsOriginalCause() {
        var cause = new IllegalArgumentException("expected toString failed");
        var expected = new ThrowingValue(cause);

        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(Object::new, config(1, 2, 0), time, time).until(equalTo(expected)));

        assertNull(failure.getCause());
        assertArrayEquals(new Throwable[]{cause}, failure.getSuppressed());
        assertThat(failure).hasMessageContainingAll("Condition: value equals expected",
                "Expected: <unprintable>",
                "Actual: java.lang.Object@");
        assertEquals(1, expected.calls);
    }

    @Test
    void diagnosticFailureRetainsSequenceContext() {
        var cause = new IllegalArgumentException("actual toString failed");
        var stage = diagnosticCondition("inner second", null);
        var attempt = new ExecutionStep<>(captured(CONDITION, stage), 2, 1, ACQUISITION, 2,
                new Attempt.Unsatisfied<>(new ThrowingValue(cause), "inner mismatch", null));

        var failure = assertFailure(TIMEOUT,
                () -> AwaitSupport.complete(new Timeout(attempt), config(1, 2, 0), 2));

        assertThat(failure).hasMessageContainingAll("Sequence: captured 1/2",
                "Condition: inner second",
                "Mismatch: inner mismatch");
    }

    @Test
    void sequenceRenderingFailureDoesNotRepeatActualRendering() {
        int[] calls = {0};
        Object actual = mock();
        when(actual.toString()).thenAnswer(ignored -> {
            calls[0]++;
            return "observed value";
        });
        var cause = new IllegalStateException("expected toString failed");
        var expected = new ThrowingValue(cause);
        var stage = diagnosticCondition("second stage", new Condition.Reference<>("Expected", expected));
        var attempt = new ExecutionStep<>(captured(CONDITION, stage), 2, 1, ACQUISITION, 2,
                new Attempt.Unsatisfied<>(actual, "not ready", null));

        var failure = assertFailure(TIMEOUT,
                () -> AwaitSupport.complete(new Timeout(attempt), config(1, 2, 0), 2));

        assertNull(failure.getCause());
        assertArrayEquals(new Throwable[]{cause}, failure.getSuppressed());
        assertThat(failure).hasMessageContainingAll("Actual: observed value", "Sequence: captured 1/2",
                "Expected: <unprintable>", "Mismatch: not ready");
        assertEquals(1, calls[0]);
        assertEquals(1, expected.calls);
    }

    @ParameterizedTest(name = "tryAwait={0}")
    @ValueSource(booleans = {false, true})
    void causeMessageFailureDoesNotRepeatValueRendering(boolean capture) {
        int[] renders = {0, 0, 0};
        Object[] values = new Object[3];
        List<String> labels = List.of("root operand", "observed value", "stage operand");
        for (int index = 0; index < values.length; index++) {
            int position = index;
            values[index] = mock();
            when(values[index].toString()).thenAnswer(ignored -> {
                renders[position]++;
                return labels.get(position);
            });
        }
        int[] messageCalls = {0};
        var diagnostic = new IllegalStateException("cause message failed");
        var original = new AssertionError("not ready") {
            @Override
            public String getMessage() {
                messageCalls[0]++;
                throw diagnostic;
            }
        };
        var first = diagnosticCondition("first stage", new Condition.Reference<>("Expected", values[0]));
        var second = new Condition.Typed<Object, Object>("second stage",
                new Condition.Reference<>("Expected", values[2]),
                () -> actual -> AttemptResult.of(unsatisfied("not ready", original), null));
        var check = captured(first, second);
        var waiting = timedAwait(() -> {
            time.advanceNanos(2);
            return values[1];
        }, config(1, 6, 0), time, time);

        AwaitFailure failure = capture
                ? failure(diagnostic(waiting).until(check))
                : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();
        assertEquals(TIMEOUT, failure.reason());
        assertSame(original, failure.cause());
        assertEquals(List.of(diagnostic), failure.suppressed());
        assertThat(failure.message()).contains(
                "Sequence: captured 1/2; stage 2/2",
                "Actual: observed value",
                "Condition: second stage\n    Expected: stage operand");
        assertArrayEquals(new int[]{0, 1, 1}, renders);
        assertEquals(1, messageCalls[0]);
    }

    @ParameterizedTest(name = "tryAwait={0}")
    @ValueSource(booleans = {false, true})
    void failuresInSeveralFieldsDoNotHideLaterFieldsOrTheOriginalCause(boolean capture) {
        var expectedFailure = new IllegalStateException("expected rendering failed");
        var actualFailure = new InternalError("actual rendering failed");
        var causeFailure = new IllegalArgumentException("cause message failed");
        var expected = new ThrowingValue(expectedFailure);
        var actual = new ThrowingValue(actualFailure);
        int[] messageCalls = {0};
        var original = new AssertionError() {
            @Override
            public String getMessage() {
                messageCalls[0]++;
                throw causeFailure;
            }
        };
        var second = new Condition.Typed<Object, Object>("second stage",
                new Condition.Reference<>("Expected", expected),
                () -> value -> AttemptResult.of(unsatisfied("not ready", original), null));
        var check = captured(CONDITION, second);
        var waiting = timedAwait(() -> actual, config(1, 2, 0), time, time);
        AwaitFailure failure = capture
                ? failure(diagnostic(waiting).until(check))
                : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();

        assertSame(original, failure.cause());
        assertEquals(List.of(expectedFailure, actualFailure, causeFailure), failure.suppressed());
        assertThat(failure.message()).contains(
                "Condition: second stage\n    Expected: <unprintable>",
                "Mismatch: not ready",
                ": <unprintable>");
        assertEquals(1, expected.calls);
        assertEquals(1, actual.calls);
        assertEquals(1, messageCalls[0]);
    }

    @Test
    void diagnosticRenderingFailureRetainsTheEngineCauseAsPrimary() {
        for (Throwable diagnosticCause : List.of(new IllegalStateException("toString failed"),
                new InternalError("value fatal"))) {
            var engineCause = new AssertionError("assertion failed");
            WaitCompletion outcome = new Timeout(
                    evaluatedAttempt(CONDITION, new ThrowingValue(diagnosticCause),
                            unsatisfied("not ready", engineCause), 2));

            var failure = assertFailure(TIMEOUT,
                    () -> AwaitSupport.complete(outcome, config(1, 2, 0), 1));

            assertSame(engineCause, failure.getCause());
            assertThat(failure.getSuppressed()).singleElement().isSameAs(diagnosticCause);
        }
    }

    @Test
    void persistenceDiagnosticsDistinguishRequiredDurationFromDetectionTime() {
        int[] reads = {0};
        var failure = assertFailure(PERSISTENCE_FAILED,
                () -> await(() -> {
                    if (reads[0]++ == 0) return "ready";
                    time.advanceNanos(10);
                    return "gone";
                }).usingTime(time, time).every(Duration.ofNanos(1)).upTo(Duration.ofNanos(10))
                        .persisting(Duration.ofNanos(2)).until(equalTo("ready")));

        assertThat(failure).hasMessageContainingAll("Attempt: 2 (PERSISTENCE)", "elapsed 11 ns",
                "required 2 ns",
                "failed after 11 ns");
    }

    @Test
    void sourceFailureDuringPersistenceIncludesItsPhaseAndElapsedTime() {
        var cause = new IOException("source failed after acquisition");
        int[] reads = {0};

        var failure = assertFailure(SOURCE_FAILED,
                () -> await(() -> {
                    if (++reads[0] == 3) {
                        time.advanceNanos(3);
                        throw cause;
                    }
                    return "ready";
                }).usingTime(time, time).every(Duration.ofNanos(1)).upTo(Duration.ofNanos(10))
                        .persisting(Duration.ofNanos(5)).until(equalTo("ready")));

        assertSame(cause, failure.getCause());
        assertThat(failure).hasMessageContainingAll("Attempt: 3 (PERSISTENCE)", "elapsed 5 ns");
    }

    @ParameterizedTest(name = "array={0}")
    @ValueSource(booleans = {false, true})
    void nullFromToStringUsesStandardArrayFormatting(boolean array) {
        Object value = mock();
        when(value.toString()).thenReturn(null);
        Object actual = array ? new Object[] {value} : value;
        WaitCompletion rendered = new Timeout(
                evaluatedAttempt(CONDITION, actual, unsatisfied("not ready"), 2));

        var valueFailure = assertFailure(TIMEOUT,
                () -> AwaitSupport.complete(rendered, config(1, 2, 0), 1));

        assertNull(valueFailure.getCause());
        assertEquals(0, valueFailure.getSuppressed().length);
        assertThat(valueFailure).hasMessageContainingAll(array ? "Actual: [null]" : "Actual: null", "Timing:");
    }

    @ParameterizedTest(name = "tryAwait={0}, negative={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void expectedListFormattingUsesTheFinalInterruptFlagOfTheField(boolean capture, boolean negative) {
        int[] renders = {0, 0};
        Object signalling = mock();
        when(signalling.toString()).thenAnswer(ignored -> {
            renders[0]++;
            currentThread().interrupt();
            return "signalling";
        });
        Object clearing = mock();
        when(clearing.toString()).thenAnswer(ignored -> {
            renders[1]++;
            interrupted();
            return "clearing";
        });
        Object actual = new Object() {
            @Override
            public boolean equals(Object other) { return negative && other == signalling; }

            @Override
            public int hashCode() { throw new AssertionError("hashCode must not be called"); }

            @Override
            public String toString() { return "actual"; }
        };
        var check = negative ? Conditions.notIn(List.of(signalling, clearing)) : Conditions.in(List.of(signalling, clearing));
        var waiting = timedAwait(() -> {
            time.advanceNanos(2);
            return actual;
        }, config(1, 2, 0), time, time);
        AwaitFailure failure = capture
                ? failure(diagnostic(waiting).until(check))
                : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();
        assertEquals(TIMEOUT, failure.reason());
        assertThat(failure.message()).contains("[signalling, clearing]");
        assertEquals(1, renders[0]);
        assertEquals(1, renders[1]);
        assertFalse(currentThread().isInterrupted());
    }

    @ParameterizedTest(name = "tryAwait={0}, negative={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void expectedEntryFormattingUsesTheFinalInterruptFlagOfTheField(boolean capture, boolean negative) {
        int[] renders = {0, 0};
        Object key = mock();
        when(key.toString()).thenAnswer(ignored -> {
            renders[0]++;
            currentThread().interrupt();
            return "key";
        });
        Object value = mock();
        when(value.toString()).thenAnswer(ignored -> {
            renders[1]++;
            interrupted();
            return "value";
        });
        var expected = new IdentityHashMap<Object, Object>();
        expected.put(key, value);
        Map<Object, Object> actual = negative
                ? new ProbeContainers.EntryMap<>(List.of(new SimpleImmutableEntry<>(key, value)))
                : Map.of("other", "other");
        List<? extends PreservingCondition<? super Map<Object, Object>>> checks = negative
                ? List.of(MapConditions.doesNotContainAnyEntries(expected), MapConditions.doesNotContainAllEntries(expected),
                        MapConditions.doesNotContainExactlyEntries(expected))
                : List.of(MapConditions.containsAllEntries(expected),
                        MapConditions.containsAnyEntries(expected), MapConditions.containsExactlyEntries(expected));
        for (var check : checks) {
            renders[0] = renders[1] = 0;
            var time = new FakeTime(0);
            var waiting = await(() -> {
                time.advanceNanos(2);
                return actual;
            }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
            AwaitFailure failure = capture
                    ? failure(diagnostic(waiting).until(check))
                    : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();
            assertEquals(TIMEOUT, failure.reason());
            assertThat(failure.message()).contains("[key=value]");
            assertEquals(1, renders[0]);
            assertEquals(1, renders[1]);
            assertFalse(currentThread().isInterrupted());
        }
    }

    @ParameterizedTest(name = "tryAwait={0}, array={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void renderingPreservesInterruptionBetweenFieldsButUsesTheFinalFlagWithinAnArray(boolean capture, boolean array) {
        int[] renders = {0, 0, 0};
        Object signalling = mock();
        when(signalling.toString()).thenAnswer(ignored -> {
            renders[0]++;
            currentThread().interrupt();
            return "signalling";
        });
        Object clearing = mock();
        when(clearing.toString()).thenAnswer(ignored -> {
            renders[1]++;
            interrupted();
            return "clearing";
        });
        var original = new AssertionError("not ready") {
            @Override
            public String getMessage() {
                renders[2]++;
                interrupted();
                return super.getMessage();
            }
        };
        Object expected = array ? new Object[] {signalling, clearing} : signalling;
        var check = ConditionSupport.preserving("ready",
                new Condition.Reference<>("Expected", expected),
                value -> unsatisfied("not ready", original));
        var waiting = timedAwait(() -> clearing, config(1, 2, 0), time, time);
        AwaitFailure failure = capture
                ? failure(diagnostic(waiting).until(check))
                : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();

        assertEquals(TIMEOUT, failure.reason());
        assertSame(original, failure.cause());
        assertEquals(1, renders[0]);
        assertEquals(array ? 2 : 1, renders[1]);
        assertEquals(1, renders[2]);
        assertEquals(!array, currentThread().isInterrupted());
    }

    @ParameterizedTest(name = "tryAwait={0}")
    @ValueSource(booleans = {false, true})
    void diagnosticFailurePreservesTheInterruptRaisedByRendering(boolean capture) {
        int[] messageCalls = {0};
        var diagnostic = new IllegalStateException() {
            @Override
            public String getMessage() {
                messageCalls[0]++;
                interrupted();
                throw new InternalError("diagnostic messages must not be evaluated");
            }
        };
        Object actual = mock();
        when(actual.toString()).thenAnswer(ignored -> {
            currentThread().interrupt();
            throw diagnostic;
        });
        var original = new AssertionError("not ready");
        var waiting = timedAwait(() -> actual, config(1, 2, 0), time, time);
        var check = condition("ready", value -> unsatisfied("not ready", original));
        AwaitFailure failure = capture
                ? failure(diagnostic(waiting).until(check))
                : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();
        assertEquals(TIMEOUT, failure.reason());
        assertSame(original, failure.cause());
        assertSame(diagnostic, failure.suppressed().getFirst());
        assertEquals(0, messageCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest(name = "tryAwait={0}")
    @ValueSource(booleans = {false, true})
    void diagnosticErrorMessagesAreNotEvaluatedRecursively(boolean capture) {
        var interruption = new InterruptedException("diagnostic message interrupted");
        int[] messageCalls = {0};
        var diagnostic = new IllegalStateException() {
            @Override
            public String getMessage() {
                messageCalls[0]++;
                return rethrow(interruption);
            }
        };
        var actual = new ThrowingValue(diagnostic);
        var original = new AssertionError("not ready");
        var waiting = await(() -> {
            time.advanceNanos(2);
            return actual;
        }).usingTime(time, time).upTo(Duration.ofNanos(2));
        var check = condition("not ready", value -> unsatisfied("not ready", original));
        AwaitFailure failure;
        if (capture) {
            failure = failure(diagnostic(waiting).until(check));
        } else {
            failure = assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();
        }
        assertEquals(TIMEOUT, failure.reason());
        assertSame(original, failure.cause());
        assertSame(diagnostic, failure.suppressed().getFirst());
        assertEquals(1, actual.calls);
        assertEquals(0, messageCalls[0]);
        assertFalse(currentThread().isInterrupted());
    }

    @Test
    void isolatedExceptionClassDoesNotRequireItsEnclosingClassForDiagnostics() throws Exception {
        byte[] bytes;
        try (var resource = CountingAssertion.class.getResourceAsStream("DiagnosticsSnapshotTest$CountingAssertion.class")) {
            bytes = resource.readAllBytes();
        }
        var loader = new ClassLoader(null) {
            Class<?> isolatedClass() {
                return defineClass(null, bytes, 0, bytes.length);
            }
        };
        var constructor = loader.isolatedClass().getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        var cause = (AssertionError) constructor.newInstance("isolated cause");

        var sourceFailure = assertFailure(SOURCE_FAILED,
                () -> await((Callable<String>) () -> { throw cause; }).until(Conditions.isNotNull));
        assertSame(cause, sourceFailure.getCause());
        assertThat(sourceFailure).hasMessageContainingAll(cause.getClass().getName(), "isolated cause");

        var diagnosticFailure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> new ThrowingValue(cause), config(1, 2, 0), time, time)
                        .until(condition("not ready", actual -> unsatisfied())));
        assertNull(diagnosticFailure.getCause());
        assertArrayEquals(new Throwable[]{cause}, diagnosticFailure.getSuppressed());
        assertThat(diagnosticFailure).hasMessageContainingAll("Rendering failure: " + cause.getClass().getName());
    }

    @Test
    void repeatedRenderingFailureIsReportedOnceAcrossBothTerminals() {
        var diagnostic = new IllegalStateException("rendering failed");
        var value = new ThrowingValue(diagnostic);
        var waiting = await(() -> value).usingTime(time, time)
                .every(Duration.ofNanos(1)).upTo(Duration.ofNanos(2));
        var check = notEqualTo(value);

        var captured = failure(diagnostic(waiting).until(check));
        var thrown = assertThrows(AwaitAssertionError.class, () -> waiting.until(check));

        assertThat(captured.suppressed()).singleElement().isSameAs(diagnostic);
        assertThat(thrown.getSuppressed()).singleElement().isSameAs(diagnostic);
        assertEquals(0, diagnostic.getSuppressed().length);
    }

    @Test
    void distinctRenderingFailuresAreRetainedEvenWhenTheyAreEqual() {
        var expectedFailure = new EqualFailure("expected");
        var actualFailure = new EqualFailure("actual");
        assertEquals(expectedFailure, actualFailure);
        var expected = new ThrowingValue(expectedFailure);
        var actual = new ThrowingValue(actualFailure);

        var result = tryAwait(() -> actual).usingTime(time, time)
                .every(Duration.ofNanos(1)).upTo(Duration.ofNanos(2)).until(equalTo(expected));
        var failure = failure(result);

        assertEquals(TIMEOUT, failure.reason());
        assertEquals(2, failure.suppressed().size());
        assertSame(expectedFailure, failure.suppressed().get(0));
        assertSame(actualFailure, failure.suppressed().get(1));
    }

    @Test
    void renderingFailureEqualToThePrimaryCauseIsStillRetained() {
        var original = new EqualFailure("source failed");
        var diagnostic = new EqualFailure("expected rendering failed");
        var expected = new ThrowingValue(diagnostic);
        Callable<ThrowingValue> source = () -> { throw original; };

        var failure = assertFailure(SOURCE_FAILED, () -> await(source).until(equalTo(expected)));

        assertSame(original, failure.getCause());
        assertThat(failure.getSuppressed()).singleElement().isSameAs(diagnostic);
    }

    @Test
    void multilineFieldsAreNormalizedAndArraysRenderWithoutDependencies() {
        var cause = new IllegalStateException("first\r\n\r\nsecond");
        Callable<String> source = () -> { throw cause; };
        var sourceFailure = assertFailure(SOURCE_FAILED,
                () -> await(source).usingTime(time, time).every(Duration.ofNanos(1)).upTo(Duration.ofNanos(2))
                        .until(condition("condition\rdescription", ignored -> ConditionResult.satisfied())));
        var arrayFailure = assertFailure(TIMEOUT,
                () -> AwaitSupport.complete(new Timeout(
                                evaluatedAttempt(CONDITION, new int[] {1, 2}, unsatisfied("not ready"), 2)), config(1, 2, 0), 1));

        assertEquals("""
                Source retrieval failed
                Attempt: 1 (ACQUISITION)

                Condition:
                    condition
                    description

                Timing: elapsed 0 ns; timeout 2 ns; interval 1 ns
                Cause:
                    java.lang.IllegalStateException: first

                    second""", sourceFailure.getMessage());
        assertThat(arrayFailure).hasMessageContainingAll("[1, 2]");
    }

    @Test
    void nestedActualAndExpectedArraysRemainDiagnosticValues() {
        Object nested = "leaf";
        int depth = 64;
        for (int index = 0; index < depth; index++) nested = new Object[] {nested};
        String rendered = "[".repeat(depth) + "leaf" + "]".repeat(depth);
        for (boolean expectedArray : List.of(false, true)) {
            Object actual = expectedArray ? "other" : nested;
            Object expected = expectedArray ? nested : "other";
            var time = new FakeTime(0);
            var failure = assertFailure(TIMEOUT,
                    () -> timedAwait((Callable<Object>) () -> actual, config(1, 2, 0), time, time).until(equalTo(expected)));
            assertThat(failure).hasMessageContainingAll(rendered);
        }
    }

    @Test
    void errorsFromArrayElementToStringAreRetainedAsDiagnosticFailures() {
        var fatal = new InternalError("array element fatal");
        Object[] actual = {new ThrowingValue(fatal)};
        var failure = assertThrows(AwaitAssertionError.class,
                () -> AwaitSupport.complete(new Timeout(evaluatedAttempt(CONDITION, actual, unsatisfied("not ready"), 2)), config(1, 2, 0), 1));
        assertArrayEquals(new Throwable[]{fatal}, failure.getSuppressed());
    }

    @Test
    void arrayDiagnosticsKeepPrimitiveValuesSharedArraysAndCycleMarkers() {
        Object[] shared = {new int[] {1, 2}, "leaf"};
        Object[] cycle = new Object[1];
        cycle[0] = cycle;
        Object[] actual = {shared, shared, new Object[] {null, new char[] {'a', 'b'}}, cycle};
        var failure = assertFailure(TIMEOUT,
                () -> AwaitSupport.complete(new Timeout(evaluatedAttempt(CONDITION, actual, unsatisfied("not ready"), 2)), config(1, 2, 0), 1));
        assertThat(failure).hasMessageContainingAll("[[[1, 2], leaf], [[1, 2], leaf], [null, [a, b]], [[...]]]");
    }

    private static Condition.CapturedSource<Condition.InputCondition<String>> lifecycle(
            PreservingCondition<String> pending,
            PreservingCondition<String> completed) {
        return captured(status("created", "payment status is created"),
                pending, completed);
    }

    private static PreservingCondition<String> pending() {
        return status("pending", "payment status is pending");
    }

    private static PreservingCondition<String> completed() {
        return status("completed", "payment status is completed");
    }

    private static PreservingCondition<String> status(String expected, String description) {
        return condition(description,
                value -> value.equals(expected)
                        ? satisfied() : unsatisfied("payment status was " + value));
    }

    private static <OBSERVED> ExecutionStep<OBSERVED> evaluatedAttempt(Condition condition, OBSERVED observed, ConditionResult result, long completedNanos) {
        return new ExecutionStep<>(condition, 1, 0, ACQUISITION, completedNanos,
                switch (result) {
                    case ConditionResult.Satisfied ignored -> new Attempt.Satisfied<>(observed);
                    case ConditionResult.Unsatisfied mismatch -> new Attempt.Unsatisfied<>(observed, mismatch.mismatch(), mismatch.cause());
                });
    }

    private static WaitCompletion sourceFailure(Condition condition, Throwable failure, long completedNanos) {
        return new Uncontrolled(new ExecutionStep<>(condition, 1, 0, ACQUISITION, completedNanos,
                new Attempt.FailedBeforeObservation<>(Attempt.Operation.SOURCE, failure)));
    }

    private static WaitCompletion waitingFailure(Throwable failure, long completedNanos) {
        return new Uncontrolled(new ExecutionStep<>(CONDITION, 1, 0, ACQUISITION, completedNanos,
                new Attempt.FailedBeforeObservation<>(Attempt.Operation.WAITING, failure)));
    }

    private static WaitCompletion conditionFailure(Condition expectation, Object observed, Throwable failure, long completedNanos) {
        return new Uncontrolled(new ExecutionStep<>(expectation, 1, 0, ACQUISITION, completedNanos,
                new Attempt.FailedAfterObservation<>(observed, Attempt.Operation.CONDITION, failure, null)));
    }

    private static final class EqualFailure extends RuntimeException {
        private EqualFailure(String message) {
            super(message);
        }

        @Override public boolean equals(Object other) {
            return other instanceof EqualFailure;
        }

        @Override public int hashCode() {
            return 1;
        }
    }

    private static final class ThrowingValue {
        private final Throwable failure;
        private int calls;

        private ThrowingValue(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public String toString() {
            calls++;
            return rethrow(failure);
        }
    }

    private static final class CountingAssertion extends AssertionError {
        private int calls;

        private CountingAssertion(String message) {
            super(message);
        }

        @Override
        public String getMessage() {
            calls++;
            return super.getMessage();
        }
    }
}

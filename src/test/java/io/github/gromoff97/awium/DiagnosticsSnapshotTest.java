package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.time.Duration;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AttemptRecord.Phase.ACQUISITION;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.timedMapAwait;
import static io.github.gromoff97.awium.ConditionSupport.captured;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.WaitCompletion.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsSnapshotTest {

    private static final Condition<?, ?> CONDITION = diagnosticCondition("condition", null, null);
    private static final long SECOND = 1_000_000_000L;
    private static final long MILLISECOND = 1_000_000L;

    @Test
    void builtInOperandIsRetainedThroughBecause() {
        var time = new FakeTime(0);
        var condition = equalTo("expected").because("payment must reach the target state");

        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> "actual", config(1, 2, 0), time, time).until(condition));

        assertMessage(failure,
                "Condition: value equals expected",
                "Expected: expected",
                "Importance: payment must reach the target state");
    }

    @Test
    void capturedBuiltInOperandIdentifiesTheCurrentStage() {
        var time = new FakeTime(0);
        var condition = captured(equalTo("first"), equalTo("second"));

        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> "actual", config(1, 2, 0), time, time).until(condition));

        assertMessage(failure,
                "Sequence (captured 0 of 2)",
                "Expectation: value equals expected",
                "Expected: first");
    }

    @Test
    void capturedAcquisitionTimeoutRendersTheCurrentStage() {
        var time = new FakeTime(0);

        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> "created", config(1, 2, 0), time, time).until(lifecycle(pending(), completed())));

        assertEquals("""
                Acquisition deadline elapsed

                Condition: conditions are satisfied in order
                    Importance: payment must complete its lifecycle

                Attempt: 2
                    Actual: created

                Sequence (captured 1 of 3):
                    Expectation: payment status is pending
                    Importance: processing must begin before completion
                    Mismatch: payment status was created

                Timing:
                    Acquisition timeout: 2 nanoseconds
                    Last attempt completed after: 1 nanosecond
                    Elapsed: 2 nanoseconds
                    Polling interval: 1 nanosecond""", failure.getMessage());
    }

    @Test
    void capturedPersistenceFailureKeepsTheFinalStage() {
        var time = new FakeTime(0);
        String[] statuses = {"created", "pending", "completed", "created"};
        int[] next = {0};

        var failure = assertFailure(PERSISTENCE_FAILED,
                () -> timedAwait(() -> statuses[next[0]++],
                        config(1, 10, 5), time, time).until(lifecycle(pending(), completed())));

        assertEquals("""
                Condition did not persist for the required duration

                Condition: conditions are satisfied in order
                    Importance: payment must complete its lifecycle

                Attempt: 4
                    Actual: created

                Sequence (captured 2 of 3):
                    Expectation: payment status is completed
                    Importance: completion must remain stable
                    Mismatch: payment status was created

                Timing:
                    Acquisition timeout: 10 nanoseconds
                    Acquired after: 2 nanoseconds
                    Required persistence: 5 nanoseconds
                    Failure detected after: 1 nanosecond
                    Polling interval: 1 nanosecond""", failure.getMessage());
    }

    @Test
    void capturedNestedAssertionRetainsCauseAndStack() {
        var time = new FakeTime(0);
        var assertion = new AssertionError("pending assertion failed");
        var frame = new StackTraceElement("Payments", "verifyPending",
                "Payments.java", 42);
        assertion.setStackTrace(new StackTraceElement[]{frame});
        PreservingCondition<String> assertedPending = Conditions.<String>condition(
                "payment status is pending", value -> unsatisfied(
                        "payment status was created", assertion))
                .because("processing must begin before completion");

        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> "created", config(1, 2, 0), time, time).until(lifecycle(assertedPending, completed())));

        assertEquals("""
                Acquisition deadline elapsed

                Condition: conditions are satisfied in order
                    Importance: payment must complete its lifecycle

                Attempt: 2
                    Actual: created

                Sequence (captured 1 of 3):
                    Expectation: payment status is pending
                    Importance: processing must begin before completion
                    Mismatch: payment status was created

                Timing:
                    Acquisition timeout: 2 nanoseconds
                    Last attempt completed after: 1 nanosecond
                    Elapsed: 2 nanoseconds
                    Polling interval: 1 nanosecond

                Cause: AssertionError
                    Message: pending assertion failed""", failure.getMessage());
        assertSame(assertion, failure.getCause());
        assertArrayEquals(new StackTraceElement[]{frame}, failure.getCause().getStackTrace());
    }

    @Test
    void capturedNestedUncontrolledRetainsCauseAndStage() {
        var time = new FakeTime(0);
        var cause = new IllegalStateException("status callback failed");
        PreservingCondition<String> brokenPending = Conditions.<String>condition(
                "payment status is pending", value -> {
                    throw cause;
                }).because("processing must begin before completion");

        var failure = assertFailure(CONDITION_FAILED,
                () -> timedAwait(() -> "created", config(1, 10, 0), time, time).until(lifecycle(brokenPending, completed())));

        assertEquals("""
                Condition evaluation failed

                Condition: conditions are satisfied in order
                    Importance: payment must complete its lifecycle

                Attempt: 2
                    Actual: created

                Sequence (captured 1 of 3):
                    Expectation: payment status is pending
                    Importance: processing must begin before completion

                Cause: IllegalStateException
                    Message: status callback failed""", failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    @Test
    void controlledFailuresRetainTheirSemanticContext() {
        var betweenAssertion = new AssertionError("collection was empty");
        var persistenceAssertion = new AssertionError("optional was empty");
        WaitCompletion<Object, Object> between = new Timeout<>(
                10 * SECOND, assertionUnsatisfiedAttempt(diagnosticCondition("collection to be non-empty", "Settlement requires an eligible payment", null),
                        null, "collection was empty",
                        betweenAssertion, 4, 9 * SECOND));
        WaitCompletion<Object, Object> lateUnsatisfied = new Timeout<>(
                unsatisfiedAttempt(diagnosticCondition("status equals COMPLETED", "payment must complete", null), "Payment[PENDING]", "status was PENDING",
                        100, 10 * SECOND + 200 * MILLISECOND));
        WaitCompletion<Object, Object> lateSatisfied = new Timeout<>(
                satisfiedAttempt(diagnosticCondition("status equals COMPLETED", "payment must complete", null), "Payment[COMPLETED]", new Object(), 100,
                        10 * SECOND + 200 * MILLISECOND));
        WaitCompletion<Object, Object> persistence = new PersistenceFailure<>(7 * SECOND,
                assertionUnsatisfiedAttempt(diagnosticCondition("optional to remain present", "payment must remain available", null),
                        "Optional.empty", "optional was empty",
                        persistenceAssertion, 71, 9 * SECOND + 100 * MILLISECOND));

        var betweenFailure = assertFailure(TIMEOUT,
                () -> complete(between, "collection to be non-empty",
                        "Settlement requires an eligible payment",
                        config(3 * SECOND, 10 * SECOND, 0)));
        var lateUnsatisfiedFailure = assertFailure(TIMEOUT,
                () -> complete(lateUnsatisfied, "status equals COMPLETED",
                        "payment must complete",
                        config(100 * MILLISECOND, 10 * SECOND, 0)));
        var lateSatisfiedFailure = assertFailure(TIMEOUT,
                () -> complete(lateSatisfied, "status equals COMPLETED",
                        "payment must complete",
                        config(100 * MILLISECOND, 10 * SECOND, 0)));
        var persistenceFailure = assertFailure(PERSISTENCE_FAILED,
                () -> complete(persistence, "optional to remain present",
                        "payment must remain available",
                        config(100 * MILLISECOND, 10 * SECOND, 5 * SECOND)));

        assertEquals("""
                Acquisition deadline elapsed

                Condition: collection to be non-empty
                    Importance: Settlement requires an eligible payment

                Attempt: 4
                    Actual: null
                    Mismatch: collection was empty

                Timing:
                    Acquisition timeout: 10 seconds
                    Last attempt completed after: 9 seconds
                    Elapsed: 10 seconds
                    Polling interval: 3 seconds

                Cause: AssertionError
                    Message: collection was empty""", betweenFailure.getMessage());
        assertEquals("""
                Acquisition deadline elapsed

                Condition: status equals COMPLETED
                    Importance: payment must complete

                Attempt: 100
                    Actual: Payment[PENDING]
                    Mismatch: status was PENDING

                Timing:
                    Acquisition timeout: 10 seconds
                    Elapsed: 10 seconds 200 milliseconds
                    Polling interval: 100 milliseconds""",
                lateUnsatisfiedFailure.getMessage());
        assertEquals("""
                Acquisition deadline elapsed

                Condition: status equals COMPLETED
                    Importance: payment must complete

                Attempt: 100
                    Actual: Payment[COMPLETED]

                Timing:
                    Acquisition timeout: 10 seconds
                    Elapsed: 10 seconds 200 milliseconds
                    Polling interval: 100 milliseconds""",
                lateSatisfiedFailure.getMessage());
        assertEquals("""
                Condition did not persist for the required duration

                Condition: optional to remain present
                    Importance: payment must remain available

                Attempt: 71
                    Actual: Optional.empty
                    Mismatch: optional was empty

                Timing:
                    Acquisition timeout: 10 seconds
                    Acquired after: 7 seconds
                    Required persistence: 5 seconds
                    Failure detected after: 2 seconds 100 milliseconds
                    Polling interval: 100 milliseconds

                Cause: AssertionError
                    Message: optional was empty""", persistenceFailure.getMessage());
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
                () -> complete(sourceFailure(sourceCause, 2, SECOND),
                        "condition", "business reason",
                        config(SECOND, 10 * SECOND, 0)));
        var conditionFailure = assertFailure(CONDITION_FAILED,
                () -> complete(conditionFailure(diagnosticCondition("condition", "business reason", null),
                        "actual", conditionCause, 3, SECOND), "condition",
                        "business reason", config(SECOND, 10 * SECOND, 0)));
        var waitingFailure = assertFailure(WAIT_FAILED,
                () -> complete(waitingFailure(waitingCause, 4, SECOND), "condition", null,
                        config(SECOND, 10 * SECOND, 0)));
        var interruptedFailure = assertFailure(WAIT_FAILED,
                () -> complete(waitingFailure(interruption, 5, SECOND), "condition", null,
                        config(SECOND, 10 * SECOND, 0)));

        assertSame(sourceCause, sourceFailure.getCause());
        assertSame(conditionCause, conditionFailure.getCause());
        assertSame(waitingCause, waitingFailure.getCause());
        assertSame(interruption, interruptedFailure.getCause());
        assertEquals("""
                Source retrieval failed

                Condition: condition
                    Importance: business reason

                Attempt: 2

                Cause: IllegalStateException
                    Message: source failed""", sourceFailure.getMessage());
        assertEquals("""
                Condition evaluation failed

                Condition: condition
                    Importance: business reason

                Attempt: 3
                    Actual: actual

                Cause: IllegalArgumentException
                    Message: condition failed""", conditionFailure.getMessage());
        assertEquals("""
                Waiting before the next attempt failed

                Condition: condition

                Attempt: 4

                Cause: IllegalStateException
                    Message: waiting failed""", waitingFailure.getMessage());
        assertEquals("""
                Waiting before the next attempt failed

                Condition: condition

                Attempt: 5

                Cause: InterruptedException
                    Message: stopped""", interruptedFailure.getMessage());
    }

    @Test
    void interruptionFlagIsRestoredAfterUserDiagnosticsClearIt() {
        var interruption = new IllegalStateException("stopped") {
            @Override
            public String getMessage() {
                assertTrue(interrupted());
                return super.getMessage();
            }
        };
        currentThread().interrupt();

        try {
            var failure = assertFailure(INTERRUPTED,
                    () -> Await.complete(waitingFailure(interruption, 1, 0),
                            CONDITION, config(1, 2, 0)));

            assertSame(interruption, failure.getCause());
            assertTrue(currentThread().isInterrupted());
        } finally {
            interrupted();
        }
    }

    @Test
    void thrownConditionFailureRetainsInterruptFlagAfterDiagnosticsClearIt() {
        var pollingTime = new FakeTime(0);
        var cause = new IllegalStateException("condition failed");
        PreservingCondition<Object> condition = Conditions.condition("condition", actual -> {
            currentThread().interrupt();
            throw cause;
        });

        try {
            var failure = assertFailure(INTERRUPTED,
                    () -> await((Source<Object>) Object::new).usingTime(pollingTime, pollingTime).until(condition));

            assertSame(cause, failure.getCause());
            assertTrue(currentThread().isInterrupted());
        } finally {
            interrupted();
        }
    }

    @Test
    void assertionCauseIsRetainedAndItsMessageIsReadOnce() {
        var assertion = new CountingAssertion("assertion failed");
        WaitCompletion<Object, Object> outcome = new Timeout<>(
                assertionUnsatisfiedAttempt(CONDITION, "actual", "business mismatch", assertion,
                        2, 3 * SECOND));

        var failure = assertFailure(TIMEOUT,
                () -> complete(outcome, "condition", null,
                        config(SECOND, 2 * SECOND, 0)));

        assertSame(assertion, failure.getCause());
        assertEquals("""
                Acquisition deadline elapsed

                Condition: condition

                Attempt: 2
                    Actual: actual
                    Mismatch: business mismatch

                Timing:
                    Acquisition timeout: 2 seconds
                    Elapsed: 3 seconds
                    Polling interval: 1 second

                Cause: CountingAssertion
                    Message: assertion failed""", failure.getMessage());
        assertEquals(1, assertion.calls);
    }

    @Test
    void longCauseMessagesPointToTheUnchangedCauseStackTrace() {
        String exactMessage = "x".repeat(160);
        String longMessage = "x".repeat(140) + "😀".repeat(21);
        var exactCause = new IllegalStateException(exactMessage);
        var longCause = new IllegalStateException(longMessage);

        var exactFailure = assertFailure(SOURCE_FAILED,
                () -> complete(sourceFailure(exactCause, 1, 0),
                        "condition", null, config(1, 2, 0)));
        var longFailure = assertFailure(SOURCE_FAILED,
                () -> complete(sourceFailure(longCause, 1, 0),
                        "condition", null, config(1, 2, 0)));

        assertTrue(exactFailure.getMessage().contains("Message: " + exactMessage));
        assertTrue(longFailure.getMessage().contains(
                "Message: " + "x".repeat(140) + "😀… <see stack trace>"));
        assertSame(longCause, longFailure.getCause());
        assertEquals(longMessage, longFailure.getCause().getMessage());
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
                Source<Object> source = () -> { throw cause; };
                var waiting = await(source).usingTime(time, time);
                AwaitFailure failure = capture
                        ? ((AwaitResult.Failed<?, ?>) waiting.tryUntil(Conditions.isNotNull)).failure()
                        : assertThrows(AwaitExecutionException.class, () -> waiting.until(Conditions.isNotNull)).failure();

                assertEquals(SOURCE_FAILED, failure.reason());
                assertSame(cause, failure.cause());
                assertFalse(failure.message().contains("Message:"), failure.message());
                assertEquals(1, reads[0]);
            }
        }
    }

    @Test
    void diagnosticRenderingFailuresPreserveTheTimeoutWithoutACause() {
        var valueCause = new IllegalArgumentException("toString failed");
        var actual = new ThrowingValue(valueCause);
        WaitCompletion<Object, Object> outcome = new Timeout<>(
                unsatisfiedAttempt(diagnosticCondition("condition", "business reason", null), actual, "not ready", 1, 2));

        var valueFailure = assertFailure(TIMEOUT,
                () -> complete(outcome, "condition", "business reason",
                        config(1, 2, 0)));

        assertNull(valueFailure.getCause());
        assertArrayEquals(new Throwable[]{valueCause}, valueFailure.getSuppressed());
        assertEquals("""
                Acquisition deadline elapsed

                Condition: condition
                    Importance: business reason

                Attempt: 1
                    Actual: <value unavailable: diagnostics failed>
                    Mismatch: not ready

                Timing:
                    Acquisition timeout: 2 nanoseconds
                    Elapsed: 2 nanoseconds
                    Polling interval: 1 nanosecond

                Diagnostics failure: IllegalArgumentException""", valueFailure.getMessage());
        assertEquals(1, actual.calls);
    }

    @Test
    void diagnosticThrowableDoesNotSetTheInterruptFlag() {
        var interruption = new InterruptedException("diagnostics interrupted");
        Object actual = new Object() {
            @Override
            public String toString() {
                throwUnchecked(interruption);
                return "unreachable";
            }
        };

        try {
            var failure = assertFailure(TIMEOUT,
                    () -> complete(new Timeout<>(unsatisfiedAttempt(CONDITION, actual, "not ready", 1, 2)),
                            "condition", null, config(1, 2, 0)));

            assertNull(failure.getCause());
            assertArrayEquals(new Throwable[]{interruption}, failure.getSuppressed());
            assertFalse(currentThread().isInterrupted());
        } finally {
            interrupted();
        }
    }

    @Test
    void expectedOperandRenderingFailureKeepsItsOriginalCause() {
        var time = new FakeTime(0);
        var cause = new IllegalArgumentException("expected toString failed");
        var expected = new ThrowingValue(cause);

        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(Object::new, config(1, 2, 0), time, time).until(equalTo(expected)));

        assertNull(failure.getCause());
        assertArrayEquals(new Throwable[]{cause}, failure.getSuppressed());
        assertMessage(failure,
                "Condition: value equals expected",
                "Expected: <value unavailable: diagnostics failed>",
                "Actual: java.lang.Object@");
        assertEquals(1, expected.calls);
    }

    @Test
    void diagnosticFailureRetainsSequenceContext() {
        var cause = new IllegalArgumentException("actual toString failed");
        var progress = new AttemptRecord.Progress(1, 2, 2,
                diagnosticCondition("inner second", "inner business reason", null));
        var attempt = new AttemptRecord<>(2, ACQUISITION,
                new AttemptRecord.Outcome.Evaluated<>(afterObservation(2), new ThrowingValue(cause),
                        new ConditionResult.Unsatisfied<>("inner mismatch", null),
                        diagnosticCondition("conditions are satisfied in order", null, null), progress));

        var failure = assertFailure(TIMEOUT,
                () -> complete(new Timeout<>(attempt), "conditions are satisfied in order",
                        null, config(1, 2, 0)));

        assertMessage(failure, "Sequence (captured 1 of 2)",
                "Expectation: inner second", "Importance: inner business reason",
                "Mismatch: inner mismatch");
    }

    @Test
    void sequenceRenderingFailureDoesNotRepeatActualRendering() {
        int[] calls = {0};
        Object actual = new Object() {
            @Override
            public String toString() {
                calls[0]++;
                return "observed value";
            }
        };
        var cause = new IllegalStateException("expected toString failed");
        var expected = new ThrowingValue(cause);
        var progress = new AttemptRecord.Progress(1, 2, 2, diagnosticCondition("second stage", null,
                new Condition.Reference<>("Expected", expected)));
        var attempt = new AttemptRecord<>(2, ACQUISITION,
                new AttemptRecord.Outcome.Evaluated<>(afterObservation(2), actual,
                        new ConditionResult.Unsatisfied<>("not ready", null),
                        diagnosticCondition("ordered conditions", null, null), progress));

        var failure = assertFailure(TIMEOUT,
                () -> complete(new Timeout<>(attempt), "ordered conditions", null, config(1, 2, 0)));

        assertNull(failure.getCause());
        assertArrayEquals(new Throwable[]{cause}, failure.getSuppressed());
        assertMessage(failure, "Actual: observed value", "Sequence (captured 1 of 2)",
                "Expected: <value unavailable: diagnostics failed>", "Mismatch: not ready");
        assertEquals(1, calls[0]);
        assertEquals(1, expected.calls);
    }

    @Test
    void causeMessageFailureDoesNotRepeatValueRendering() {
        for (boolean capture : List.of(false, true)) {
            int[] renders = {0, 0, 0};
            Object[] values = new Object[3];
            List<String> labels = List.of("root operand", "observed value", "stage operand");
            for (int index = 0; index < values.length; index++) {
                int position = index;
                values[index] = new Object() {
                    @Override
                    public String toString() {
                        renders[position]++;
                        return labels.get(position);
                    }
                };
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
            var progress = new AttemptRecord.Progress(1, 2, 2, diagnosticCondition("second stage", null,
                    new Condition.Reference<>("Expected", values[2])));
            var check = new Condition<Object, Object>("ordered conditions", null,
                    new Condition.Reference<>("Expected", values[0]),
                    () -> actual -> new AttemptResult.Evaluated<>(unsatisfied("not ready", original), null, progress));
            var time = new FakeTime(0);
            var waiting = timedAwait(() -> {
                time.advanceNanos(2);
                return values[1];
            }, config(1, 2, 0), time, time);

            AwaitFailure failure = capture
                    ? ((AwaitResult.Failed<?, ?>) waiting.tryUntil(check)).failure()
                    : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();
            assertEquals(TIMEOUT, failure.reason());
            assertSame(original, failure.cause());
            assertEquals(List.of(diagnostic), failure.suppressed());
            assertTrue(failure.message().contains("Condition: ordered conditions\n    Expected: root operand"));
            assertTrue(failure.message().contains("Actual: observed value"));
            assertTrue(failure.message().contains("Expectation: second stage\n    Expected: stage operand"));
            assertArrayEquals(new int[]{1, 1, 1}, renders);
            assertEquals(1, messageCalls[0]);
        }
    }

    @Test
    void failuresInSeveralFieldsDoNotHideLaterFieldsOrTheOriginalCause() {
        for (boolean capture : List.of(false, true)) {
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
            var progress = new AttemptRecord.Progress(1, 2, 2, diagnosticCondition("second stage", null,
                    new Condition.Reference<>("Expected", "stage operand")));
            var check = new Condition<Object, Object>("ordered conditions", "business reason",
                    new Condition.Reference<>("Expected", expected),
                    () -> value -> new AttemptResult.Evaluated<>(unsatisfied("not ready", original), null, progress));
            var time = new FakeTime(0);
            var waiting = timedAwait(() -> actual, config(1, 2, 0), time, time);
            AwaitFailure failure = capture
                    ? ((AwaitResult.Failed<?, ?>) waiting.tryUntil(check)).failure()
                    : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();

            assertSame(original, failure.cause());
            assertEquals(List.of(expectedFailure, actualFailure, causeFailure), failure.suppressed());
            assertTrue(failure.message().contains("Importance: business reason"));
            assertTrue(failure.message().contains("Expectation: second stage\n    Expected: stage operand"));
            assertTrue(failure.message().contains("Mismatch: not ready"));
            assertTrue(failure.message().contains("Message: <value unavailable: diagnostics failed>"));
            assertEquals(1, expected.calls);
            assertEquals(1, actual.calls);
            assertEquals(1, messageCalls[0]);
        }
    }

    @Test
    void diagnosticRenderingFailureRetainsTheEngineCauseAsPrimary() {
        var diagnosticCause = new IllegalStateException("toString failed");
        var engineCause = new AssertionError("assertion failed");
        WaitCompletion<Object, Object> outcome = new Timeout<>(
                assertionUnsatisfiedAttempt(CONDITION, new ThrowingValue(diagnosticCause),
                        "not ready", engineCause, 1, 2));

        var failure = assertFailure(TIMEOUT,
                () -> complete(outcome, "condition", null,
                        config(1, 2, 0)));

        assertSame(engineCause, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(diagnosticCause, failure.getSuppressed()[0]);
    }

    @Test
    void persistenceDiagnosticsDistinguishRequiredDurationFromDetectionTime() {
        WaitCompletion<Object, Object> outcome = new PersistenceFailure<>(0,
                unsatisfiedAttempt(CONDITION, "actual", "condition was false", 2, 11));

        var failure = assertFailure(PERSISTENCE_FAILED,
                () -> complete(outcome, "condition", null,
                        config(1, 10, 2)));

        assertMessage(failure, "Required persistence: 2 nanoseconds",
                "Failure detected after: 11 nanoseconds");
    }

    @Test
    void nullFromToStringUsesStandardArrayFormatting() {
        for (Object actual : List.of(new NullStringValue(), new Object[] {new NullStringValue()})) {
            WaitCompletion<Object, Object> rendered = new Timeout<>(
                    unsatisfiedAttempt(CONDITION, actual, "not ready", 1, 2));

            var valueFailure = assertFailure(TIMEOUT,
                    () -> complete(rendered, "condition", null,
                            config(1, 2, 0)));

            assertNull(valueFailure.getCause());
            assertEquals(0, valueFailure.getSuppressed().length);
            assertMessage(valueFailure, actual instanceof Object[] ? "Actual: [null]" : "Actual: null", "Timing:");
        }
    }

    @Test
    void expectedListFormattingUsesTheFinalInterruptFlagOfTheField() {
        for (boolean capture : List.of(false, true)) {
            for (boolean negative : List.of(false, true)) {
                int[] renders = {0, 0};
                Object signalling = new Object() {
                    @Override
                    public String toString() {
                        renders[0]++;
                        currentThread().interrupt();
                        return "signalling";
                    }
                };
                Object clearing = new Object() {
                    @Override
                    public String toString() {
                        renders[1]++;
                        interrupted();
                        return "clearing";
                    }
                };
                Object actual = new Object() {
                    @Override
                    public boolean equals(Object other) { return negative && other == signalling; }

                    @Override
                    public int hashCode() { throw new AssertionError("hashCode must not be called"); }

                    @Override
                    public String toString() { return "actual"; }
                };
                var check = negative ? Conditions.notIn(signalling, clearing) : Conditions.in(signalling, clearing);
                var time = new FakeTime(0);
                var waiting = timedAwait(() -> {
                    time.advanceNanos(2);
                    return actual;
                }, config(1, 2, 0), time, time);
                try {
                    AwaitFailure failure = capture
                            ? ((AwaitResult.Failed<?, ?>) waiting.tryUntil(check)).failure()
                            : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();
                    assertEquals(TIMEOUT, failure.reason());
                    assertTrue(failure.message().contains("[signalling, clearing]"));
                    assertEquals(1, renders[0]);
                    assertEquals(1, renders[1]);
                    assertFalse(currentThread().isInterrupted());
                } finally {
                    interrupted();
                }
            }
        }
    }

    @Test
    void expectedEntryFormattingUsesTheFinalInterruptFlagOfTheField() {
        for (boolean capture : List.of(false, true)) {
            for (boolean negative : List.of(false, true)) {
                int[] renders = {0, 0};
                Object key = new Object() {
                    @Override
                    public String toString() {
                        renders[0]++;
                        currentThread().interrupt();
                        return "key";
                    }
                };
                Object value = new Object() {
                    @Override
                    public String toString() {
                        renders[1]++;
                        interrupted();
                        return "value";
                    }
                };
                var expected = new IdentityHashMap<Object, Object>();
                expected.put(key, value);
                Map<Object, Object> actual = negative
                        ? new ProbeContainers.EntryMap<>(List.of(new SimpleImmutableEntry<>(key, value)))
                        : Map.of("other", "other");
                List<? extends PreservingCondition<? super Map<Object, Object>>> checks = negative
                        ? List.of(MapConditions.doesNotContainEntries(expected), MapConditions.doesNotContainAllEntries(expected),
                                MapConditions.doesNotContainExactlyEntries(expected))
                        : List.of(MapConditions.containsEntries(expected),
                                MapConditions.containsAnyEntries(expected), MapConditions.containsExactlyEntries(expected));
                for (var check : checks) {
                    renders[0] = renders[1] = 0;
                    var time = new FakeTime(0);
                    var waiting = timedMapAwait(() -> {
                        time.advanceNanos(2);
                        return actual;
                    }, config(1, 2, 0), time, time);
                    try {
                        AwaitFailure failure = capture
                                ? ((AwaitResult.Failed<?, ?>) waiting.tryUntil(check)).failure()
                                : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();
                        assertEquals(TIMEOUT, failure.reason());
                        assertTrue(failure.message().contains("[key=value]"));
                        assertEquals(1, renders[0]);
                        assertEquals(1, renders[1]);
                        assertFalse(currentThread().isInterrupted());
                    } finally {
                        interrupted();
                    }
                }
            }
        }
    }

    @Test
    void renderingPreservesInterruptionBetweenFieldsButUsesTheFinalFlagWithinAnArray() {
        for (boolean capture : List.of(false, true)) {
            for (boolean array : List.of(false, true)) {
                int[] renders = {0, 0, 0};
                Object signalling = new Object() {
                    @Override
                    public String toString() {
                        renders[0]++;
                        currentThread().interrupt();
                        return "signalling";
                    }
                };
                Object clearing = new Object() {
                    @Override
                    public String toString() {
                        renders[1]++;
                        interrupted();
                        return "clearing";
                    }
                };
                var original = new AssertionError("not ready") {
                    @Override
                    public String getMessage() {
                        renders[2]++;
                        interrupted();
                        return super.getMessage();
                    }
                };
                Object expected = array ? new Object[] {signalling, clearing} : signalling;
                var check = ConditionSupport.condition("ready",
                        new Condition.Reference<>("Expected", expected),
                        value -> unsatisfied("not ready", original));
                var time = new FakeTime(0);
                var waiting = timedAwait(() -> clearing, config(1, 2, 0), time, time);
                try {
                    AwaitFailure failure = capture
                            ? ((AwaitResult.Failed<?, ?>) waiting.tryUntil(check)).failure()
                            : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();

                    assertEquals(TIMEOUT, failure.reason());
                    assertSame(original, failure.cause());
                    assertEquals(1, renders[0]);
                    assertEquals(array ? 2 : 1, renders[1]);
                    assertEquals(1, renders[2]);
                    assertEquals(!array, currentThread().isInterrupted());
                } finally {
                    interrupted();
                }
            }
        }
    }

    @Test
    void diagnosticFailurePreservesTheInterruptRaisedByRendering() {
        for (boolean capture : List.of(false, true)) {
            int[] messageCalls = {0};
            var diagnostic = new IllegalStateException() {
                @Override
                public String getMessage() {
                    messageCalls[0]++;
                    interrupted();
                    throw new InternalError("diagnostic messages must not be evaluated");
                }
            };
            Object actual = new Object() {
                @Override
                public String toString() {
                    currentThread().interrupt();
                    throw diagnostic;
                }
            };
            var original = new AssertionError("not ready");
            var time = new FakeTime(0);
            var waiting = timedAwait(() -> actual, config(1, 2, 0), time, time);
            var check = condition("ready", value -> unsatisfied("not ready", original));
            try {
                AwaitFailure failure = capture
                        ? ((AwaitResult.Failed<?, ?>) waiting.tryUntil(check)).failure()
                        : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();
                assertEquals(TIMEOUT, failure.reason());
                assertSame(original, failure.cause());
                assertSame(diagnostic, failure.suppressed().getFirst());
                assertEquals(0, messageCalls[0]);
                assertTrue(currentThread().isInterrupted());
            } finally {
                interrupted();
            }
        }
    }

    @Test
    void diagnosticErrorMessagesAreNotEvaluatedRecursively() {
        for (boolean capture : List.of(false, true)) {
            var interruption = new InterruptedException("diagnostic message interrupted");
            int[] messageCalls = {0};
            var diagnostic = new IllegalStateException() {
                @Override
                public String getMessage() {
                    messageCalls[0]++;
                    throwUnchecked(interruption);
                    return null;
                }
            };
            var actual = new ThrowingValue(diagnostic);
            var original = new AssertionError("not ready");
            var time = new FakeTime(0);
            var waiting = await(() -> {
                time.advanceNanos(2);
                return actual;
            }).usingTime(time, time).upTo(Duration.ofNanos(2));
            var check = condition("not ready", value -> unsatisfied("not ready", original));
            try {
                AwaitFailure failure;
                if (capture) {
                    failure = ((AwaitResult.Failed<?, ?>) waiting.tryUntil(check)).failure();
                } else {
                    failure = assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();
                }
                assertEquals(TIMEOUT, failure.reason());
                assertSame(original, failure.cause());
                assertSame(diagnostic, failure.suppressed().getFirst());
                assertEquals(1, actual.calls);
                assertEquals(0, messageCalls[0]);
                assertFalse(currentThread().isInterrupted());
            } finally {
                interrupted();
            }
        }
    }

    @Test
    void diagnosticErrorsAreRetainedAlongsideTheOriginalFailure() {
        var valueFatal = new InternalError("value fatal");
        var engineCause = new AssertionError("assertion failed");
        WaitCompletion<Object, Object> valueFailure = new Timeout<>(
                assertionUnsatisfiedAttempt(CONDITION, new ThrowingValue(valueFatal), "not ready",
                        engineCause, 1, 2));

        var thrown = assertThrows(AwaitAssertionError.class,
                () -> complete(valueFailure, "condition", null,
                        config(1, 2, 0)));
        assertSame(engineCause, thrown.getCause());
        assertArrayEquals(new Throwable[]{valueFatal}, thrown.getSuppressed());
    }

    @Test
    @SuppressWarnings("removal")
    void uncontrolledCausesRemainDiagnosticDataRegardlessOfThrowableType() {
        var virtualMachineError = new InternalError("fatal");
        var threadDeath = new ThreadDeath();

        assertSame(virtualMachineError, assertThrows(AwaitExecutionException.class,
                () -> complete(sourceFailure(virtualMachineError, 1, 0),
                        "condition", null, config(1, 2, 0))).getCause());
        assertSame(threadDeath, assertThrows(AwaitExecutionException.class,
                () -> complete(conditionFailure(CONDITION, null, threadDeath, 1, 0),
                        "condition", null, config(1, 2, 0))).getCause());
    }

    @Test
    void multilineFieldsAreNormalizedAndArraysRenderWithoutDependencies() {
        var cause = new IllegalStateException("first\r\nsecond");
        var sourceFailure = assertFailure(SOURCE_FAILED,
                () -> complete(sourceFailure(cause, 1, 0),
                        "condition\rdescription", "business\nreason",
                        config(1, 2, 0)));
        var arrayFailure = assertFailure(TIMEOUT,
                () -> complete(new Timeout<>(
                                unsatisfiedAttempt(CONDITION, new int[] {1, 2}, "not ready", 1, 2)),
                        "condition", null,
                        config(1, 2, 0)));

        assertFalse(sourceFailure.getMessage().contains("\r"));
        assertTrue(sourceFailure.getMessage().lines()
                .noneMatch(line -> line.endsWith(" ")));
        assertMessage(sourceFailure, "condition", "description", "business",
                "reason", "first", "second");
        assertMessage(arrayFailure, "[1, 2]");
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
                    () -> timedAwait((Source<Object>) () -> actual, config(1, 2, 0), time, time).until(equalTo(expected)));
            assertMessage(failure, rendered);
        }
    }

    @Test
    void errorsFromArrayElementToStringAreRetainedAsDiagnosticFailures() {
        var fatal = new InternalError("array element fatal");
        Object[] actual = {new Object() {
            @Override
            public String toString() {
                throw fatal;
            }
        }};
        var failure = assertThrows(AwaitAssertionError.class,
                () -> complete(new Timeout<>(unsatisfiedAttempt(CONDITION, actual, "not ready", 1, 2)),
                        "condition", null, config(1, 2, 0)));
        assertArrayEquals(new Throwable[]{fatal}, failure.getSuppressed());
    }

    @Test
    void arrayDiagnosticsKeepPrimitiveValuesSharedArraysAndCycleMarkers() {
        Object[] shared = {new int[] {1, 2}, "leaf"};
        Object[] cycle = new Object[1];
        cycle[0] = cycle;
        Object[] actual = {shared, shared, new Object[] {null, new char[] {'a', 'b'}}, cycle};
        var failure = assertFailure(TIMEOUT,
                () -> complete(new Timeout<>(unsatisfiedAttempt(CONDITION, actual, "not ready", 1, 2)),
                        "condition", null, config(1, 2, 0)));
        assertMessage(failure, "[[[1, 2], leaf], [[1, 2], leaf], [null, [a, b]], [[...]]]");
    }

    private static <S, R> R complete(WaitCompletion<S, R> outcome, String description,
            String explanation, WaitConfiguration configuration) {
        return Await.complete(outcome, diagnosticCondition(description, explanation, null), configuration);
    }

    private static Condition<String, List<String>> lifecycle(
            PreservingCondition<String> pending,
            PreservingCondition<String> completed) {
        return captured(status("created", "payment status is created", null),
                pending, completed).because("payment must complete its lifecycle");
    }

    private static PreservingCondition<String> pending() {
        return status("pending", "payment status is pending",
                "processing must begin before completion");
    }

    private static PreservingCondition<String> completed() {
        return status("completed", "payment status is completed",
                "completion must remain stable");
    }

    private static PreservingCondition<String> status(String expected,
            String description, String importance) {
        PreservingCondition<String> condition = condition(description,
                value -> value.equals(expected)
                        ? satisfied(value) : unsatisfied("payment status was " + value));
        return importance == null ? condition : condition.because(importance);
    }

    private static <S, R> AttemptRecord<S, R> satisfiedAttempt(
            Condition<?, ?> expectation, S observed, R result, long number, long completedNanos) {
        return new AttemptRecord<>(number, ACQUISITION,
                new AttemptRecord.Outcome.Evaluated<>(afterObservation(completedNanos), observed,
                        new ConditionResult.Satisfied<>(result), expectation, null));
    }

    private static <S> AttemptRecord<S, Object> unsatisfiedAttempt(
            Condition<?, ?> expectation, S observed, String mismatch, long number, long completedNanos) {
        return new AttemptRecord<>(number, ACQUISITION,
                new AttemptRecord.Outcome.Evaluated<>(afterObservation(completedNanos), observed,
                        new ConditionResult.Unsatisfied<>(mismatch, null), expectation, null));
    }

    private static <S> AttemptRecord<S, Object> assertionUnsatisfiedAttempt(
            Condition<?, ?> expectation, S observed, String mismatch, AssertionError assertion,
            long number, long completedNanos) {
        return new AttemptRecord<>(number, ACQUISITION,
                new AttemptRecord.Outcome.Evaluated<>(afterObservation(completedNanos), observed,
                        new ConditionResult.Unsatisfied<>(mismatch, assertion), expectation, null));
    }

    private static WaitCompletion<Object, Object> sourceFailure(
            Throwable failure, long number, long completedNanos) {
        var timing = new AttemptRecord.Timing.BeforeObservation(
                Duration.ZERO, Duration.ZERO, Duration.ofNanos(completedNanos));
        return new Uncontrolled<>(new AttemptRecord<>(number, ACQUISITION,
                new AttemptRecord.Outcome.SourceRetrievalFailed<>(timing, failure)));
    }

    private static WaitCompletion<Object, Object> waitingFailure(
            Throwable failure, long number, long completedNanos) {
        var timing = new AttemptRecord.Timing.BeforeRetrieval(
                Duration.ZERO, Duration.ofNanos(completedNanos));
        return new Uncontrolled<>(new AttemptRecord<>(number, ACQUISITION,
                new AttemptRecord.Outcome.WaitingFailed<>(timing, failure)));
    }

    private static WaitCompletion<Object, Object> conditionFailure(
            Condition<?, ?> expectation, Object observed, Throwable failure, long number, long completedNanos) {
        return new Uncontrolled<>(new AttemptRecord<>(number, ACQUISITION,
                new AttemptRecord.Outcome.ConditionFailed<>(afterObservation(completedNanos), observed,
                        failure, expectation, null)));
    }

    private static AttemptRecord.Timing.AfterObservation afterObservation(
            long completedNanos) {
        return new AttemptRecord.Timing.AfterObservation(Duration.ZERO,
                Duration.ZERO, Duration.ZERO, Duration.ofNanos(completedNanos));
    }

    private static void assertMessage(Throwable failure, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(failure.getMessage().contains(fragment),
                    () -> "missing diagnostic fragment: " + fragment
                            + "\n" + failure.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static <FAILURE extends Throwable> void throwUnchecked(Throwable failure)
            throws FAILURE {
        throw (FAILURE) failure;
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
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw (Error) failure;
        }
    }

    private static final class NullStringValue {

        @Override
        public String toString() {
            return null;
        }
    }

    private static final class CountingAssertion extends AssertionError {
        private final String message;
        private int calls;

        private CountingAssertion(String message) {
            this.message = message;
        }

        @Override
        public String getMessage() {
            calls++;
            return message;
        }
    }
}

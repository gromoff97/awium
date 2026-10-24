package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.io.IOException;
import java.time.Duration;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.ConditionTestRuntime.throwUnchecked;
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
import static io.github.gromoff97.awium.Conditions.notEqualTo;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.WaitCompletion.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
                "Because: payment must reach the target state");
    }

    @Test
    void capturedBuiltInOperandIdentifiesTheCurrentStage() {
        var time = new FakeTime(0);
        var condition = captured(equalTo("first"), equalTo("second"));

        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> "actual", config(1, 2, 0), time, time).until(condition));

        assertMessage(failure,
                "Sequence (captured 0 of 2)",
                "Condition: value equals expected",
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
                    Because: payment must complete its lifecycle

                Attempt: 2 (ACQUISITION)
                    Actual: created

                Sequence (captured 1 of 3):
                    Condition: payment status is pending
                        Because: processing must begin before completion
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
                    Because: payment must complete its lifecycle

                Attempt: 4 (PERSISTENCE)
                    Actual: created

                Sequence (captured 2 of 3):
                    Condition: payment status is completed
                        Because: completion must remain stable
                        Mismatch: payment status was created

                Timing:
                    Acquisition timeout: 10 nanoseconds
                    Elapsed: 3 nanoseconds
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
                    Because: payment must complete its lifecycle

                Attempt: 2 (ACQUISITION)
                    Actual: created

                Sequence (captured 1 of 3):
                    Condition: payment status is pending
                        Because: processing must begin before completion
                        Mismatch: payment status was created

                Timing:
                    Acquisition timeout: 2 nanoseconds
                    Last attempt completed after: 1 nanosecond
                    Elapsed: 2 nanoseconds
                    Polling interval: 1 nanosecond

                Cause: java.lang.AssertionError
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
                    Because: payment must complete its lifecycle

                Attempt: 2 (ACQUISITION)
                    Actual: created

                Sequence (captured 1 of 3):
                    Condition: payment status is pending
                        Because: processing must begin before completion

                Timing:
                    Acquisition timeout: 10 nanoseconds
                    Elapsed: 1 nanosecond
                    Polling interval: 1 nanosecond

                Cause: java.lang.IllegalStateException
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
        var persistedAttempt = assertionUnsatisfiedAttempt(diagnosticCondition("optional to remain present", "payment must remain available", null),
                "Optional.empty", "optional was empty",
                persistenceAssertion, 71, 9 * SECOND + 100 * MILLISECOND);
        var persistence = new PersistenceFailure<>(7 * SECOND,
                new AttemptRecord<>(persistedAttempt.number(), AttemptRecord.Phase.PERSISTENCE, persistedAttempt.outcome()));

        var betweenFailure = assertFailure(TIMEOUT,
                () -> Await.complete(between, CONDITION,
                        config(3 * SECOND, 10 * SECOND, 0)));
        var lateUnsatisfiedFailure = assertFailure(TIMEOUT,
                () -> Await.complete(lateUnsatisfied, CONDITION,
                        config(100 * MILLISECOND, 10 * SECOND, 0)));
        var lateSatisfiedFailure = assertFailure(TIMEOUT,
                () -> Await.complete(lateSatisfied, CONDITION,
                        config(100 * MILLISECOND, 10 * SECOND, 0)));
        var persistenceFailure = assertFailure(PERSISTENCE_FAILED,
                () -> Await.complete(persistence, CONDITION,
                        config(100 * MILLISECOND, 10 * SECOND, 5 * SECOND)));

        assertEquals("""
                Acquisition deadline elapsed

                Condition: collection to be non-empty
                    Because: Settlement requires an eligible payment

                Attempt: 4 (ACQUISITION)
                    Actual: null
                    Mismatch: collection was empty

                Timing:
                    Acquisition timeout: 10 seconds
                    Last attempt completed after: 9 seconds
                    Elapsed: 10 seconds
                    Polling interval: 3 seconds

                Cause: java.lang.AssertionError
                    Message: collection was empty""", betweenFailure.getMessage());
        assertEquals("""
                Acquisition deadline elapsed

                Condition: status equals COMPLETED
                    Because: payment must complete

                Attempt: 100 (ACQUISITION)
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
                    Because: payment must complete

                Attempt: 100 (ACQUISITION)
                    Actual: Payment[COMPLETED]

                Timing:
                    Acquisition timeout: 10 seconds
                    Elapsed: 10 seconds 200 milliseconds
                    Polling interval: 100 milliseconds""",
                lateSatisfiedFailure.getMessage());
        assertEquals("""
                Condition did not persist for the required duration

                Condition: optional to remain present
                    Because: payment must remain available

                Attempt: 71 (PERSISTENCE)
                    Actual: Optional.empty
                    Mismatch: optional was empty

                Timing:
                    Acquisition timeout: 10 seconds
                    Elapsed: 9 seconds 100 milliseconds
                    Acquired after: 7 seconds
                    Required persistence: 5 seconds
                    Failure detected after: 2 seconds 100 milliseconds
                    Polling interval: 100 milliseconds

                Cause: java.lang.AssertionError
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
                () -> Await.complete(sourceFailure(sourceCause, 2, SECOND),
                        diagnosticCondition("condition", "business reason", null),
                        config(SECOND, 10 * SECOND, 0)));
        var conditionFailure = assertFailure(CONDITION_FAILED,
                () -> Await.complete(conditionFailure(diagnosticCondition("condition", "business reason", null),
                        "actual", conditionCause, 3, SECOND), CONDITION, config(SECOND, 10 * SECOND, 0)));
        var waitingFailure = assertFailure(WAIT_FAILED,
                () -> Await.complete(waitingFailure(waitingCause, 4, SECOND), CONDITION,
                        config(SECOND, 10 * SECOND, 0)));
        var interruptedFailure = assertFailure(WAIT_FAILED,
                () -> Await.complete(waitingFailure(interruption, 5, SECOND), CONDITION,
                        config(SECOND, 10 * SECOND, 0)));

        assertSame(sourceCause, sourceFailure.getCause());
        assertSame(conditionCause, conditionFailure.getCause());
        assertSame(waitingCause, waitingFailure.getCause());
        assertSame(interruption, interruptedFailure.getCause());
        assertEquals("""
                Source retrieval failed

                Condition: condition
                    Because: business reason

                Attempt: 2 (ACQUISITION)

                Timing:
                    Acquisition timeout: 10 seconds
                    Elapsed: 1 second
                    Polling interval: 1 second

                Cause: java.lang.IllegalStateException
                    Message: source failed""", sourceFailure.getMessage());
        assertEquals("""
                Condition evaluation failed

                Condition: condition
                    Because: business reason

                Attempt: 3 (ACQUISITION)
                    Actual: actual

                Timing:
                    Acquisition timeout: 10 seconds
                    Elapsed: 1 second
                    Polling interval: 1 second

                Cause: java.lang.IllegalArgumentException
                    Message: condition failed""", conditionFailure.getMessage());
        assertEquals("""
                Waiting before the next attempt failed

                Condition: condition

                Attempt: 4 (ACQUISITION)

                Timing:
                    Acquisition timeout: 10 seconds
                    Elapsed: 1 second
                    Polling interval: 1 second

                Cause: java.lang.IllegalStateException
                    Message: waiting failed""", waitingFailure.getMessage());
        assertEquals("""
                Waiting before the next attempt failed

                Condition: condition

                Attempt: 5 (ACQUISITION)

                Timing:
                    Acquisition timeout: 10 seconds
                    Elapsed: 1 second
                    Polling interval: 1 second

                Cause: java.lang.InterruptedException
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
                    () -> await((Callable<Object>) Object::new).usingTime(pollingTime, pollingTime).until(condition));

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
                () -> Await.complete(outcome, CONDITION,
                        config(SECOND, 2 * SECOND, 0)));

        assertSame(assertion, failure.getCause());
        assertEquals("""
                Acquisition deadline elapsed

                Condition: condition

                Attempt: 2 (ACQUISITION)
                    Actual: actual
                    Mismatch: business mismatch

                Timing:
                    Acquisition timeout: 2 seconds
                    Elapsed: 3 seconds
                    Polling interval: 1 second

                Cause: io.github.gromoff97.awium.DiagnosticsSnapshotTest$CountingAssertion
                    Message: assertion failed""", failure.getMessage());
        assertEquals(1, assertion.calls);
    }

    @Test
    void causeMessagesRemainCompleteInBothTerminals() {
        for (String message : List.of("x".repeat(1024), "detailed assertion 😀 ".repeat(32))) {
            var cause = new IllegalStateException(message);
            Callable<String> source = () -> { throw cause; };
            var waiting = await(source);

            var captured = assertInstanceOf(AwaitResult.Failed.class,
                    waiting.tryUntil(Conditions.isNotNull)).failure();
            var thrown = assertThrows(AwaitExecutionException.class,
                    () -> waiting.until(Conditions.isNotNull));

            assertTrue(captured.message().contains("Message: " + message));
            assertTrue(thrown.getMessage().contains("Message: " + message));
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
                () -> Await.complete(outcome, CONDITION,
                        config(1, 2, 0)));

        assertNull(valueFailure.getCause());
        assertArrayEquals(new Throwable[]{valueCause}, valueFailure.getSuppressed());
        assertEquals("""
                Acquisition deadline elapsed

                Condition: condition
                    Because: business reason

                Attempt: 1 (ACQUISITION)
                    Actual: <value unavailable: diagnostics failed>
                    Mismatch: not ready

                Timing:
                    Acquisition timeout: 2 nanoseconds
                    Elapsed: 2 nanoseconds
                    Polling interval: 1 nanosecond

                Diagnostics failure: java.lang.IllegalArgumentException""", valueFailure.getMessage());
        assertEquals(1, actual.calls);
    }

    @Test
    void diagnosticThrowableDoesNotSetTheInterruptFlag() {
        var interruption = new InterruptedException("diagnostics interrupted");
        Object actual = new Object() {
            @Override
            public String toString() {
                return throwUnchecked(interruption);
            }
        };

        try {
            var failure = assertFailure(TIMEOUT,
                    () -> Await.complete(new Timeout<>(unsatisfiedAttempt(CONDITION, actual, "not ready", 1, 2)),
                            CONDITION, config(1, 2, 0)));

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
                () -> Await.complete(new Timeout<>(attempt), CONDITION, config(1, 2, 0)));

        assertMessage(failure, "Sequence (captured 1 of 2)",
                "Condition: inner second", "Because: inner business reason",
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
                () -> Await.complete(new Timeout<>(attempt), CONDITION, config(1, 2, 0)));

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
            assertTrue(failure.message().contains("Condition: second stage\n        Expected: stage operand"));
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
            assertTrue(failure.message().contains("Because: business reason"));
            assertTrue(failure.message().contains("Condition: second stage\n        Expected: stage operand"));
            assertTrue(failure.message().contains("Mismatch: not ready"));
            assertTrue(failure.message().contains("Message: <value unavailable: diagnostics failed>"));
            assertEquals(1, expected.calls);
            assertEquals(1, actual.calls);
            assertEquals(1, messageCalls[0]);
        }
    }

    @Test
    void diagnosticRenderingFailureRetainsTheEngineCauseAsPrimary() {
        for (Throwable diagnosticCause : List.of(new IllegalStateException("toString failed"),
                new InternalError("value fatal"))) {
            var engineCause = new AssertionError("assertion failed");
            WaitCompletion<Object, Object> outcome = new Timeout<>(
                    assertionUnsatisfiedAttempt(CONDITION, new ThrowingValue(diagnosticCause),
                            "not ready", engineCause, 1, 2));

            var failure = assertFailure(TIMEOUT,
                    () -> Await.complete(outcome, CONDITION,
                            config(1, 2, 0)));

            assertSame(engineCause, failure.getCause());
            assertEquals(1, failure.getSuppressed().length);
            assertSame(diagnosticCause, failure.getSuppressed()[0]);
        }
    }

    @Test
    void persistenceDiagnosticsDistinguishRequiredDurationFromDetectionTime() {
        var time = new FakeTime(0);
        int[] reads = {0};
        var failure = assertFailure(PERSISTENCE_FAILED,
                () -> await(() -> {
                    if (reads[0]++ == 0) return "ready";
                    time.advanceNanos(10);
                    return "gone";
                }).usingTime(time, time).every(Duration.ofNanos(1)).upTo(Duration.ofNanos(10))
                        .persisting(Duration.ofNanos(2)).until(equalTo("ready")));

        assertMessage(failure, "Attempt: 2 (PERSISTENCE)", "Elapsed: 11 nanoseconds",
                "Required persistence: 2 nanoseconds",
                "Failure detected after: 11 nanoseconds");
    }

    @Test
    void sourceFailureDuringPersistenceIncludesItsPhaseAndElapsedTime() {
        var time = new FakeTime(0);
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
        assertMessage(failure, "Attempt: 3 (PERSISTENCE)", "Elapsed: 5 nanoseconds");
    }

    @Test
    void nullFromToStringUsesStandardArrayFormatting() {
        for (Object actual : List.of(new NullStringValue(), new Object[] {new NullStringValue()})) {
            WaitCompletion<Object, Object> rendered = new Timeout<>(
                    unsatisfiedAttempt(CONDITION, actual, "not ready", 1, 2));

            var valueFailure = assertFailure(TIMEOUT,
                    () -> Await.complete(rendered, CONDITION,
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
                    return throwUnchecked(interruption);
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
        assertMessage(sourceFailure, cause.getClass().getName(), "isolated cause");

        var time = new FakeTime(0);
        var diagnosticFailure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> new ThrowingValue(cause), config(1, 2, 0), time, time)
                        .until(condition("not ready", actual -> unsatisfied())));
        assertNull(diagnosticFailure.getCause());
        assertArrayEquals(new Throwable[]{cause}, diagnosticFailure.getSuppressed());
        assertMessage(diagnosticFailure, "Diagnostics failure: " + cause.getClass().getName());
    }

    @Test
    void repeatedRenderingFailureIsReportedOnceAcrossBothTerminals() {
        var diagnostic = new IllegalStateException("rendering failed");
        var value = new ThrowingValue(diagnostic);
        var time = new FakeTime(0);
        var waiting = await(() -> value).usingTime(time, time)
                .every(Duration.ofNanos(1)).upTo(Duration.ofNanos(2));
        var check = notEqualTo(value).because("the value must change");

        var captured = assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(check)).failure();
        var thrown = assertThrows(AwaitAssertionError.class, () -> waiting.until(check));

        assertEquals(1, captured.suppressed().size());
        assertSame(diagnostic, captured.suppressed().getFirst());
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(diagnostic, thrown.getSuppressed()[0]);
        assertEquals(0, diagnostic.getSuppressed().length);
    }

    @Test
    void distinctRenderingFailuresAreRetainedEvenWhenTheyAreEqual() {
        var expectedFailure = new EqualFailure("expected");
        var actualFailure = new EqualFailure("actual");
        assertEquals(expectedFailure, actualFailure);
        var expected = new ThrowingValue(expectedFailure);
        var actual = new ThrowingValue(actualFailure);
        var time = new FakeTime(0);

        var result = await(() -> actual).usingTime(time, time)
                .every(Duration.ofNanos(1)).upTo(Duration.ofNanos(2)).tryUntil(equalTo(expected));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();

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
        assertEquals(1, failure.getSuppressed().length);
        assertSame(diagnostic, failure.getSuppressed()[0]);
    }

    @Test
    void multilineFieldsAreNormalizedAndArraysRenderWithoutDependencies() {
        var cause = new IllegalStateException("first\r\n\r\nsecond");
        Callable<String> source = () -> { throw cause; };
        var time = new FakeTime(0);
        var sourceFailure = assertFailure(SOURCE_FAILED,
                () -> await(source).usingTime(time, time).every(Duration.ofNanos(1)).upTo(Duration.ofNanos(2))
                        .until(condition("condition\rdescription", ConditionResult::satisfied)
                                .because("business\n\nreason")));
        var arrayFailure = assertFailure(TIMEOUT,
                () -> Await.complete(new Timeout<>(
                                unsatisfiedAttempt(CONDITION, new int[] {1, 2}, "not ready", 1, 2)),
                        CONDITION,
                        config(1, 2, 0)));

        assertFalse(sourceFailure.getMessage().contains("\r"));
        assertTrue(sourceFailure.getMessage().lines()
                .noneMatch(line -> line.endsWith(" ")));
        assertMessage(sourceFailure, "condition", "description", "business",
                "reason", "first", "second");
        assertEquals("""
                Source retrieval failed

                Condition:
                    condition
                    description
                    Because:
                        business

                        reason

                Attempt: 1 (ACQUISITION)

                Timing:
                    Acquisition timeout: 2 nanoseconds
                    Elapsed: 0 nanoseconds
                    Polling interval: 1 nanosecond

                Cause: java.lang.IllegalStateException
                    Message:
                        first

                        second""", sourceFailure.getMessage());
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
                    () -> timedAwait((Callable<Object>) () -> actual, config(1, 2, 0), time, time).until(equalTo(expected)));
            assertMessage(failure, rendered);
        }
    }

    @Test
    void errorsFromArrayElementToStringAreRetainedAsDiagnosticFailures() {
        var fatal = new InternalError("array element fatal");
        Object[] actual = {new ThrowingValue(fatal)};
        var failure = assertThrows(AwaitAssertionError.class,
                () -> Await.complete(new Timeout<>(unsatisfiedAttempt(CONDITION, actual, "not ready", 1, 2)),
                        CONDITION, config(1, 2, 0)));
        assertArrayEquals(new Throwable[]{fatal}, failure.getSuppressed());
    }

    @Test
    void arrayDiagnosticsKeepPrimitiveValuesSharedArraysAndCycleMarkers() {
        Object[] shared = {new int[] {1, 2}, "leaf"};
        Object[] cycle = new Object[1];
        cycle[0] = cycle;
        Object[] actual = {shared, shared, new Object[] {null, new char[] {'a', 'b'}}, cycle};
        var failure = assertFailure(TIMEOUT,
                () -> Await.complete(new Timeout<>(unsatisfiedAttempt(CONDITION, actual, "not ready", 1, 2)),
                        CONDITION, config(1, 2, 0)));
        assertMessage(failure, "[[[1, 2], leaf], [[1, 2], leaf], [null, [a, b]], [[...]]]");
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
        var timing = new AttemptRecord.Timing(Duration.ZERO, Duration.ZERO, null, Duration.ofNanos(completedNanos));
        return new Uncontrolled<>(new AttemptRecord<>(number, ACQUISITION,
                new AttemptRecord.Outcome.SourceRetrievalFailed<>(timing, failure)));
    }

    private static WaitCompletion<Object, Object> waitingFailure(
            Throwable failure, long number, long completedNanos) {
        var timing = new AttemptRecord.Timing(Duration.ZERO, null, null, Duration.ofNanos(completedNanos));
        return new Uncontrolled<>(new AttemptRecord<>(number, ACQUISITION,
                new AttemptRecord.Outcome.WaitingFailed<>(timing, failure)));
    }

    private static WaitCompletion<Object, Object> conditionFailure(
            Condition<?, ?> expectation, Object observed, Throwable failure, long number, long completedNanos) {
        return new Uncontrolled<>(new AttemptRecord<>(number, ACQUISITION,
                new AttemptRecord.Outcome.ConditionFailed<>(afterObservation(completedNanos), observed,
                        failure, expectation, null)));
    }

    private static AttemptRecord.Timing afterObservation(
            long completedNanos) {
        return new AttemptRecord.Timing(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ofNanos(completedNanos));
    }

    private static void assertMessage(Throwable failure, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(failure.getMessage().contains(fragment),
                    () -> "missing diagnostic fragment: " + fragment
                            + "\n" + failure.getMessage());
        }
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
            return throwUnchecked(failure);
        }
    }

    private static final class NullStringValue {

        @Override
        public String toString() {
            return null;
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

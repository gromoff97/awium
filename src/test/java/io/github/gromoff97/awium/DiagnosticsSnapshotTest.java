package io.github.gromoff97.awium;

import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
import io.github.gromoff97.awium.fluent.Condition;
import io.github.gromoff97.awium.fluent.ConditionStage.ResultStage;
import io.github.gromoff97.awium.fluent.Conditions;
import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitCompletion;
import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitPersistenceException;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitInterruptedException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitSourceRetrievalException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitUnhandledException;
import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.fluent.Await.await;
import static io.github.gromoff97.awium.fluent.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.uncontrolled;
import static io.github.gromoff97.awium.fluent.Conditions.captured;
import static io.github.gromoff97.awium.fluent.Conditions.condition;
import static io.github.gromoff97.awium.engine.WaitCompletion.*;
import static io.github.gromoff97.awium.results.AwaitAttempt.Phase.ACQUISITION;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class DiagnosticsSnapshotTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long MILLISECOND = 1_000_000L;

    @Test
    void capturedAcquisitionTimeoutRendersTheCurrentStage() {
        var time = new FakeTime(0);

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> timedAwait(() -> "created", config(1, 2, 0), time, time).until(lifecycle(pending(), completed())));

        assertEquals("""
                Acquisition deadline elapsed before the next attempt

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

        AwaitPersistenceException failure = assertThrows(
                AwaitPersistenceException.class,
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
        ResultStage<String, String> assertedPending = Conditions.<String, String>condition(
                "payment status is pending", value -> assertionUnsatisfied(
                        "payment status was created", assertion))
                .because("processing must begin before completion");

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> timedAwait(() -> "created", config(1, 2, 0), time, time).until(lifecycle(assertedPending, completed())));

        assertEquals("""
                Acquisition deadline elapsed before the next attempt

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
        ResultStage<String, String> brokenPending = Conditions.<String, String>condition(
                "payment status is pending", value -> {
                    throw cause;
                }).because("processing must begin before completion");

        AwaitConditionEvaluationException failure = assertThrows(
                AwaitConditionEvaluationException.class,
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
        WaitCompletion<Object, Object> between = new TimeoutBetweenObservations<>(
                10 * SECOND, assertionUnsatisfiedAttempt(null, "collection was empty",
                        betweenAssertion, 4, 9 * SECOND));
        WaitCompletion<Object, Object> lateUnsatisfied = new LateTimeout<>(
                unsatisfiedAttempt("Payment[PENDING]", "status was PENDING",
                        100, 10 * SECOND + 200 * MILLISECOND));
        WaitCompletion<Object, Object> lateSatisfied = new LateTimeout<>(
                satisfiedAttempt("Payment[COMPLETED]", new Object(), 100,
                        10 * SECOND + 200 * MILLISECOND));
        WaitCompletion<Object, Object> persistence = new PersistenceFailure<>(7 * SECOND,
                assertionUnsatisfiedAttempt("Optional.empty", "optional was empty",
                        persistenceAssertion, 71, 9 * SECOND + 100 * MILLISECOND));

        AwaitTimeoutException betweenFailure = assertThrows(
                AwaitTimeoutException.class,
                () -> complete(between, "collection to be non-empty",
                        "Settlement requires an eligible payment",
                        config(3 * SECOND, 10 * SECOND, 0)));
        AwaitTimeoutException lateUnsatisfiedFailure = assertThrows(
                AwaitTimeoutException.class,
                () -> complete(lateUnsatisfied, "status equals COMPLETED",
                        "payment must complete",
                        config(100 * MILLISECOND, 10 * SECOND, 0)));
        AwaitTimeoutException lateSatisfiedFailure = assertThrows(
                AwaitTimeoutException.class,
                () -> complete(lateSatisfied, "status equals COMPLETED",
                        "payment must complete",
                        config(100 * MILLISECOND, 10 * SECOND, 0)));
        AwaitPersistenceException persistenceFailure = assertThrows(
                AwaitPersistenceException.class,
                () -> complete(persistence, "optional to remain present",
                        "payment must remain available",
                        config(100 * MILLISECOND, 10 * SECOND, 5 * SECOND)));

        assertEquals("""
                Acquisition deadline elapsed before the next attempt

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
                Condition remained unsatisfied at or after the acquisition deadline

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
                Condition became satisfied at or after the acquisition deadline

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

        AwaitSourceRetrievalException sourceFailure = assertThrows(
                AwaitSourceRetrievalException.class,
                () -> complete(sourceFailure(sourceCause, 2, SECOND),
                        "condition", "business reason",
                        config(SECOND, 10 * SECOND, 0)));
        AwaitConditionEvaluationException conditionFailure = assertThrows(
                AwaitConditionEvaluationException.class,
                () -> complete(conditionFailure("actual", conditionCause, 3, SECOND), "condition",
                        "business reason", config(SECOND, 10 * SECOND, 0)));
        AwaitUnhandledException waitingFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> complete(waitingFailure(waitingCause, 4, SECOND), "condition", null,
                        config(SECOND, 10 * SECOND, 0)));
        AwaitInterruptedException interruptedFailure = assertThrows(
                AwaitInterruptedException.class,
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
                Caller thread was interrupted while waiting

                Condition: condition

                Attempt: 5

                Cause: InterruptedException
                    Message: stopped""", interruptedFailure.getMessage());
    }

    @Test
    void interruptionFlagIsRestoredAfterUserDiagnosticsClearIt() {
        var interruption = new InterruptedException("stopped") {
            @Override
            public String getMessage() {
                assertTrue(interrupted());
                return super.getMessage();
            }
        };
        currentThread().interrupt();

        try {
            AwaitInterruptedException failure = assertThrows(
                    AwaitInterruptedException.class,
                    () -> FailureFactory.complete(waitingFailure(interruption, 1, 0),
                            "condition", null, config(1, 2, 0)));

            assertSame(interruption, failure.getCause());
            assertTrue(currentThread().isInterrupted());
        } finally {
            interrupted();
        }
    }

    @Test
    void explicitUncontrolledRetainsInterruptFlagAfterDiagnosticsClearIt() {
        var cause = new IllegalStateException("condition failed");
        Condition<Object, Object> condition = Conditions.condition("condition", actual -> {
            currentThread().interrupt();
            return uncontrolled(cause);
        });

        try {
            AwaitConditionEvaluationException failure = assertThrows(
                    AwaitConditionEvaluationException.class,
                    () -> await((Source<Object>) Object::new).until(condition));

            assertSame(cause, failure.getCause());
            assertTrue(currentThread().isInterrupted());
        } finally {
            interrupted();
        }
    }

    @Test
    void assertionCauseIsRetainedAndItsMessageIsReadOnce() {
        var assertion = new CountingAssertion("assertion failed");
        WaitCompletion<Object, Object> outcome = new LateTimeout<>(
                assertionUnsatisfiedAttempt("actual", "business mismatch", assertion,
                        2, 3 * SECOND));

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> complete(outcome, "condition", null,
                        config(SECOND, 2 * SECOND, 0)));

        assertSame(assertion, failure.getCause());
        assertEquals("""
                Condition remained unsatisfied at or after the acquisition deadline

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
        assertTrue(assertion.calls == 1);
    }

    @Test
    void longCauseMessagesPointToTheUnchangedCauseStackTrace() {
        String exactMessage = "x".repeat(160);
        String longMessage = "x".repeat(140) + "😀".repeat(21);
        var exactCause = new IllegalStateException(exactMessage);
        var longCause = new IllegalStateException(longMessage);

        AwaitSourceRetrievalException exactFailure = assertThrows(
                AwaitSourceRetrievalException.class,
                () -> complete(sourceFailure(exactCause, 1, 0),
                        "condition", null, config(1, 2, 0)));
        AwaitSourceRetrievalException longFailure = assertThrows(
                AwaitSourceRetrievalException.class,
                () -> complete(sourceFailure(longCause, 1, 0),
                        "condition", null, config(1, 2, 0)));

        assertTrue(exactFailure.getMessage().contains("Message: " + exactMessage));
        assertTrue(longFailure.getMessage().contains(
                "Message: " + "x".repeat(140) + "😀… <see stack trace>"));
        assertSame(longCause, longFailure.getCause());
        assertEquals(longMessage, longFailure.getCause().getMessage());
    }

    @Test
    void diagnosticRenderingFailuresAreUnhandledWithTheirOriginalCause() {
        var valueCause = new IllegalArgumentException("toString failed");
        var actual = new ThrowingValue(valueCause);
        WaitCompletion<Object, Object> outcome = new LateTimeout<>(
                unsatisfiedAttempt(actual, "not ready", 1, 2));

        AwaitUnhandledException valueFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> complete(outcome, "condition", "business reason",
                        config(1, 2, 0)));

        assertSame(valueCause, valueFailure.getCause());
        assertEquals("""
                Failure diagnostics could not be formatted

                Condition: condition
                    Importance: business reason

                Attempt: 1
                    Actual: <value unavailable: diagnostics failed>
                    Mismatch: not ready

                Timing:
                    Acquisition timeout: 2 nanoseconds
                    Elapsed: 2 nanoseconds
                    Polling interval: 1 nanosecond

                Cause: IllegalArgumentException
                    Message: toString failed""", valueFailure.getMessage());
        assertTrue(actual.calls == 1);
    }

    @Test
    void diagnosticRenderingFailureRetainsTheEngineCauseAsSuppressed() {
        var diagnosticCause = new IllegalStateException("toString failed");
        var engineCause = new AssertionError("assertion failed");
        WaitCompletion<Object, Object> outcome = new LateTimeout<>(
                assertionUnsatisfiedAttempt(new ThrowingValue(diagnosticCause),
                        "not ready", engineCause, 1, 2));

        AwaitUnhandledException failure = assertThrows(
                AwaitUnhandledException.class,
                () -> complete(outcome, "condition", null,
                        config(1, 2, 0)));

        assertSame(diagnosticCause, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(engineCause, failure.getSuppressed()[0]);
    }

    @Test
    void persistenceDiagnosticsDistinguishRequiredDurationFromDetectionTime() {
        WaitCompletion<Object, Object> outcome = new PersistenceFailure<>(0,
                unsatisfiedAttempt("actual", "condition was false", 2, 11));

        AwaitPersistenceException failure = assertThrows(
                AwaitPersistenceException.class,
                () -> complete(outcome, "condition", null,
                        config(1, 10, 2)));

        assertMessage(failure, "Required persistence: 2 nanoseconds",
                "Failure detected after: 11 nanoseconds");
    }

    @Test
    void invalidRenderedValueIsUnhandled() {
        WaitCompletion<Object, Object> rendered = new LateTimeout<>(
                unsatisfiedAttempt(new NullStringValue(), "not ready", 1, 2));

        AwaitUnhandledException valueFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> complete(rendered, "condition", null,
                        config(1, 2, 0)));

        assertTrue(valueFailure.getCause() instanceof NullPointerException);
        assertMessage(valueFailure, "actual toString() must not return null",
                "Timing:");
    }

    @Test
    void fatalDiagnosticSignalsEscapeUnchanged() {
        var valueFatal = new InternalError("value fatal");
        var engineCause = new AssertionError("assertion failed");
        WaitCompletion<Object, Object> valueFailure = new LateTimeout<>(
                assertionUnsatisfiedAttempt(new ThrowingValue(valueFatal), "not ready",
                        engineCause, 1, 2));

        InternalError thrown = assertThrows(InternalError.class,
                () -> complete(valueFailure, "condition", null,
                        config(1, 2, 0)));
        assertSame(valueFatal, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(engineCause, thrown.getSuppressed()[0]);
    }

    @Test
    @SuppressWarnings("removal")
    void fatalUncontrolledCausesEscapeUnchangedAtTheLowLevelBoundary() {
        var virtualMachineError = new InternalError("fatal");
        var threadDeath = new ThreadDeath();

        assertSame(virtualMachineError, assertThrows(InternalError.class,
                () -> complete(sourceFailure(virtualMachineError, 1, 0),
                        "condition", null, config(1, 2, 0))));
        assertSame(threadDeath, assertThrows(ThreadDeath.class,
                () -> complete(conditionFailure(null, threadDeath, 1, 0),
                        "condition", null, config(1, 2, 0))));
    }

    @Test
    void fatalEmergencyDiagnosticsRetainDiagnosticAndEngineCauses() {
        var fatal = new InternalError("emergency diagnostics fatal");
        var diagnosticCause = new FatalMessageException(fatal);
        var engineCause = new AssertionError("assertion failed");
        WaitCompletion<Object, Object> outcome = new LateTimeout<>(
                assertionUnsatisfiedAttempt(new ThrowingValue(diagnosticCause),
                        "not ready", engineCause, 1, 2));
        InternalError thrown = assertThrows(InternalError.class,
                () -> FailureFactory.complete(outcome, "condition", null,
                        config(1, 2, 0)));

        assertSame(fatal, thrown);
        assertEquals(2, thrown.getSuppressed().length);
        assertSame(diagnosticCause, thrown.getSuppressed()[0]);
        assertSame(engineCause, thrown.getSuppressed()[1]);
    }

    @Test
    void multilineFieldsAreNormalizedAndArraysRenderWithoutDependencies() {
        var cause = new IllegalStateException("first\r\nsecond");
        AwaitSourceRetrievalException sourceFailure = assertThrows(
                AwaitSourceRetrievalException.class,
                () -> complete(sourceFailure(cause, 1, 0),
                        "condition\rdescription", "business\nreason",
                        config(1, 2, 0)));
        AwaitTimeoutException arrayFailure = assertThrows(
                AwaitTimeoutException.class,
                () -> complete(new LateTimeout<>(
                                unsatisfiedAttempt(new int[] {1, 2}, "not ready", 1, 2)),
                        "condition", null,
                        config(1, 2, 0)));

        assertFalse(sourceFailure.getMessage().contains("\r"));
        assertTrue(sourceFailure.getMessage().lines()
                .noneMatch(line -> line.endsWith(" ")));
        assertMessage(sourceFailure, "condition", "description", "business",
                "reason", "first", "second");
        assertMessage(arrayFailure, "[1, 2]");
    }

    private static <S, R> R complete(WaitCompletion<S, R> outcome, String description,
            String explanation, WaitConfiguration configuration) {
        return FailureFactory.complete(outcome, description, explanation, configuration);
    }

    private static ResultStage<String, java.util.List<String>> lifecycle(
            ResultStage<String, String> pending,
            ResultStage<String, String> completed) {
        return captured(status("created", "payment status is created", null),
                pending, completed).because("payment must complete its lifecycle");
    }

    private static ResultStage<String, String> pending() {
        return status("pending", "payment status is pending",
                "processing must begin before completion");
    }

    private static ResultStage<String, String> completed() {
        return status("completed", "payment status is completed",
                "completion must remain stable");
    }

    private static ResultStage<String, String> status(String expected,
            String description, String importance) {
        Condition<String, String> condition = condition(description,
                value -> value.equals(expected)
                        ? satisfied(value) : unsatisfied("payment status was " + value));
        return importance == null ? condition : condition.because(importance);
    }

    private static <S, R> AwaitAttempt<S, R> satisfiedAttempt(
            S observed, R result, long number, long completedNanos) {
        return new AwaitAttempt<>(number, ACQUISITION,
                new AwaitAttempt.Outcome.Satisfied<>(
                        afterObservation(completedNanos), observed, result));
    }

    private static <S> AwaitAttempt<S, Object> unsatisfiedAttempt(
            S observed, String mismatch, long number, long completedNanos) {
        return new AwaitAttempt<>(number, ACQUISITION,
                new AwaitAttempt.Outcome.Unsatisfied<>(
                        afterObservation(completedNanos), observed, mismatch, null,
                        AwaitAttempt.Context.Plain.INSTANCE));
    }

    private static <S> AwaitAttempt<S, Object> assertionUnsatisfiedAttempt(
            S observed, String mismatch, AssertionError assertion,
            long number, long completedNanos) {
        return new AwaitAttempt<>(number, ACQUISITION,
                new AwaitAttempt.Outcome.Unsatisfied<>(
                        afterObservation(completedNanos), observed, mismatch, assertion,
                        AwaitAttempt.Context.Plain.INSTANCE));
    }

    private static WaitCompletion<Object, Object> sourceFailure(
            Throwable failure, long number, long completedNanos) {
        var timing = new AwaitAttempt.Timing.BeforeObservation(
                Duration.ZERO, Duration.ZERO, Duration.ofNanos(completedNanos));
        return new Uncontrolled<>(new AwaitAttempt<>(number, ACQUISITION,
                new AwaitAttempt.Outcome.SourceRetrievalFailed<>(timing, failure)));
    }

    private static WaitCompletion<Object, Object> waitingFailure(
            Throwable failure, long number, long completedNanos) {
        var timing = new AwaitAttempt.Timing.BeforeRetrieval(
                Duration.ZERO, Duration.ofNanos(completedNanos));
        return new Uncontrolled<>(new AwaitAttempt<>(number, ACQUISITION,
                new AwaitAttempt.Outcome.WaitingFailed<>(timing, failure)));
    }

    private static WaitCompletion<Object, Object> conditionFailure(
            Object observed, Throwable failure, long number, long completedNanos) {
        return new Uncontrolled<>(new AwaitAttempt<>(number, ACQUISITION,
                new AwaitAttempt.Outcome.ConditionEvaluationFailed<>(
                        afterObservation(completedNanos), observed, failure,
                        AwaitAttempt.Context.Plain.INSTANCE)));
    }

    private static AwaitAttempt.Timing.AfterObservation afterObservation(
            long completedNanos) {
        return new AwaitAttempt.Timing.AfterObservation(Duration.ZERO,
                Duration.ZERO, Duration.ZERO, Duration.ofNanos(completedNanos));
    }

    private static WaitConfiguration config(long every, long upTo,
            long persistence) {
        return new WaitConfiguration(every, upTo, persistence);
    }

    private static void assertMessage(Throwable failure, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(failure.getMessage().contains(fragment),
                    () -> "missing diagnostic fragment: " + fragment
                            + "\n" + failure.getMessage());
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

    private static final class FatalMessageException extends RuntimeException {

        private final Error fatal;

        private FatalMessageException(Error fatal) {
            this.fatal = fatal;
        }

        @Override
        public String getMessage() {
            throw fatal;
        }
    }
}

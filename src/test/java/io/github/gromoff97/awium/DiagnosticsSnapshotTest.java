package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.await.AwaitAttempt;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitPersistenceException;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitInterruptedException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitSourceRetrievalException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitUnhandledException;
import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.uncontrolled;
import static io.github.gromoff97.awium.engine.WaitOutcome.*;
import static io.github.gromoff97.awium.await.AwaitAttempt.Phase.ACQUISITION;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void controlledFailuresRetainTheirSemanticContext() {
        var betweenAssertion = new AssertionError("collection was empty");
        var persistenceAssertion = new AssertionError("optional was empty");
        WaitOutcome<Object, Object> between = new TimeoutBetweenObservations<>(0,
                10 * SECOND, assertionUnsatisfiedAttempt(null, "collection was empty",
                        betweenAssertion, 4, 9 * SECOND));
        WaitOutcome<Object, Object> lateUnsatisfied = new LateUnsatisfiedTimeout<>(0,
                unsatisfiedAttempt("Payment[PENDING]", "status was PENDING",
                        100, 10 * SECOND + 200 * MILLISECOND));
        WaitOutcome<Object, Object> lateSatisfied = new LateSatisfiedTimeout<>(0,
                satisfiedAttempt("Payment[COMPLETED]", new Object(), 100,
                        10 * SECOND + 200 * MILLISECOND));
        WaitOutcome<Object, Object> persistence = new PersistenceFailure<>(0, 7 * SECOND,
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

                Condition:
                    Expectation: collection to be non-empty
                    Importance: Settlement requires an eligible payment

                Attempt:
                    Number: 4
                    Actual: null
                    Mismatch: collection was empty

                Timing:
                    Acquisition timeout: 10 seconds
                    Last attempt completed after: 9 seconds
                    Elapsed: 10 seconds
                    Polling interval: 3 seconds

                Cause:
                    Type: AssertionError
                    Message: collection was empty""", betweenFailure.getMessage());
        assertEquals("""
                Condition remained unsatisfied at or after the acquisition deadline

                Condition:
                    Expectation: status equals COMPLETED
                    Importance: payment must complete

                Attempt:
                    Number: 100
                    Actual: Payment[PENDING]
                    Mismatch: status was PENDING

                Timing:
                    Acquisition timeout: 10 seconds
                    Elapsed: 10 seconds 200 milliseconds
                    Polling interval: 100 milliseconds""",
                lateUnsatisfiedFailure.getMessage());
        assertEquals("""
                Condition became satisfied at or after the acquisition deadline

                Condition:
                    Expectation: status equals COMPLETED
                    Importance: payment must complete

                Attempt:
                    Number: 100
                    Actual: Payment[COMPLETED]

                Timing:
                    Acquisition timeout: 10 seconds
                    Elapsed: 10 seconds 200 milliseconds
                    Polling interval: 100 milliseconds""",
                lateSatisfiedFailure.getMessage());
        assertEquals("""
                Condition did not persist for the required duration

                Condition:
                    Expectation: optional to remain present
                    Importance: payment must remain available

                Attempt:
                    Number: 71
                    Actual: Optional.empty
                    Mismatch: optional was empty

                Timing:
                    Acquisition timeout: 10 seconds
                    Acquired after: 7 seconds
                    Required persistence: 5 seconds
                    Failure detected after: 2 seconds 100 milliseconds
                    Elapsed: 9 seconds 100 milliseconds
                    Polling interval: 100 milliseconds

                Cause:
                    Type: AssertionError
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

                Condition:
                    Expectation: condition
                    Importance: business reason

                Attempt:
                    Number: 2
                    Origin: source

                Timing:
                    Acquisition timeout: 10 seconds
                    Polling interval: 1 second

                Cause:
                    Type: IllegalStateException
                    Message: source failed""", sourceFailure.getMessage());
        assertEquals("""
                Condition evaluation failed

                Condition:
                    Expectation: condition
                    Importance: business reason

                Attempt:
                    Number: 3
                    Origin: condition
                    Actual: actual

                Timing:
                    Acquisition timeout: 10 seconds
                    Polling interval: 1 second

                Cause:
                    Type: IllegalArgumentException
                    Message: condition failed""", conditionFailure.getMessage());
        assertEquals("""
                Waiting before the next attempt failed

                Condition:
                    Expectation: condition

                Attempt:
                    Number: 4
                    Origin: waiting

                Timing:
                    Acquisition timeout: 10 seconds
                    Polling interval: 1 second

                Cause:
                    Type: IllegalStateException
                    Message: waiting failed""", waitingFailure.getMessage());
        assertEquals("""
                Caller thread was interrupted

                Condition:
                    Expectation: condition

                Attempt:
                    Number: 5
                    Origin: waiting

                Timing:
                    Acquisition timeout: 10 seconds
                    Polling interval: 1 second

                Cause:
                    Type: InterruptedException
                    Message: stopped""", interruptedFailure.getMessage());
    }

    @Test
    void interruptionFlagIsRestoredAfterUserDiagnosticsClearIt() {
        var interruption = new InterruptedException("stopped");
        currentThread().interrupt();

        try {
            AwaitInterruptedException failure = assertThrows(
                    AwaitInterruptedException.class,
                    () -> FailureFactory.complete(
                            waitingFailure(interruption, 1, 0), () -> {
                                assertTrue(interrupted());
                                return "condition";
                            }, null,
                            config(1, 2, 0)));

            assertSame(interruption, failure.getCause());
            assertTrue(currentThread().isInterrupted());
        } finally {
            interrupted();
        }
    }

    @Test
    void explicitUncontrolledRetainsInterruptFlagAfterDiagnosticsClearIt() {
        var cause = new IllegalStateException("condition failed");
        Condition<Object, Object> condition = new Condition<>() {
            @Override
            public Evaluation<Object> evaluate(Object actual) {
                currentThread().interrupt();
                return uncontrolled(cause);
            }

            @Override
            public String description() {
                assertTrue(interrupted());
                return "condition";
            }
        };

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
        WaitOutcome<Object, Object> outcome = new LateUnsatisfiedTimeout<>(0,
                assertionUnsatisfiedAttempt("actual", "business mismatch", assertion,
                        2, 3 * SECOND));

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> complete(outcome, "condition", null,
                        config(SECOND, 2 * SECOND, 0)));

        assertSame(assertion, failure.getCause());
        assertEquals("""
                Condition remained unsatisfied at or after the acquisition deadline

                Condition:
                    Expectation: condition

                Attempt:
                    Number: 2
                    Actual: actual
                    Mismatch: business mismatch

                Timing:
                    Acquisition timeout: 2 seconds
                    Elapsed: 3 seconds
                    Polling interval: 1 second

                Cause:
                    Type: CountingAssertion
                    Message: assertion failed""", failure.getMessage());
        assertTrue(assertion.calls == 1);
    }

    @Test
    void successfulOutcomeDoesNotRenderFailureMetadata() {
        var result = new Object();

        assertSame(result, FailureFactory.complete(
                new Satisfied<>(satisfiedAttempt(
                        new ThrowingValue(new AssertionError()), result, 1, 0)), () -> {
                    throw new AssertionError("description must not be read");
                }, null, config(1, 2, 0)));
    }

    @Test
    void diagnosticRenderingFailuresAreUnhandledWithTheirOriginalCause() {
        var descriptionCause = new IllegalStateException("description failed");
        var valueCause = new IllegalArgumentException("toString failed");
        var actual = new ThrowingValue(valueCause);
        WaitOutcome<Object, Object> outcome = new LateUnsatisfiedTimeout<>(0,
                unsatisfiedAttempt(actual, "not ready", 1, 2));

        AwaitUnhandledException descriptionFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> FailureFactory.complete(outcome, () -> {
                    throw descriptionCause;
                }, "business reason", config(1, 2, 0)));
        AwaitUnhandledException valueFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> complete(outcome, "condition", "business reason",
                        config(1, 2, 0)));

        assertSame(descriptionCause, descriptionFailure.getCause());
        assertEquals("""
                Failure diagnostics could not be formatted

                Condition:
                    Expectation: condition description unavailable
                    Importance: business reason

                Attempt:
                    Number: 1
                    Origin: diagnostics
                    Actual: <value unavailable: diagnostics failed>
                    Mismatch: not ready

                Timing:
                    Acquisition timeout: 2 nanoseconds
                    Elapsed: 2 nanoseconds
                    Polling interval: 1 nanosecond

                Cause:
                    Type: IllegalStateException
                    Message: description failed""",
                descriptionFailure.getMessage());
        assertSame(valueCause, valueFailure.getCause());
        assertEquals("""
                Failure diagnostics could not be formatted

                Condition:
                    Expectation: condition
                    Importance: business reason

                Attempt:
                    Number: 1
                    Origin: diagnostics
                    Actual: <value unavailable: diagnostics failed>
                    Mismatch: not ready

                Timing:
                    Acquisition timeout: 2 nanoseconds
                    Elapsed: 2 nanoseconds
                    Polling interval: 1 nanosecond

                Cause:
                    Type: IllegalArgumentException
                    Message: toString failed""", valueFailure.getMessage());
        assertTrue(actual.calls == 1);
    }

    @Test
    void diagnosticRenderingFailureRetainsTheEngineCauseAsSuppressed() {
        var diagnosticCause = new IllegalStateException("toString failed");
        var engineCause = new AssertionError("assertion failed");
        WaitOutcome<Object, Object> outcome = new LateUnsatisfiedTimeout<>(0,
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
        WaitOutcome<Object, Object> outcome = new PersistenceFailure<>(0, 0,
                unsatisfiedAttempt("actual", "condition was false", 2, 11));

        AwaitPersistenceException failure = assertThrows(
                AwaitPersistenceException.class,
                () -> complete(outcome, "condition", null,
                        config(1, 10, 2)));

        assertMessage(failure, "Required persistence: 2 nanoseconds",
                "Failure detected after: 11 nanoseconds");
    }

    @Test
    void invalidDiagnosticTextIsUnhandled() {
        WaitOutcome<Object, Object> described = new LateUnsatisfiedTimeout<>(0,
                unsatisfiedAttempt("actual", "not ready", 1, 2));
        WaitOutcome<Object, Object> rendered = new LateUnsatisfiedTimeout<>(0,
                unsatisfiedAttempt(new NullStringValue(), "not ready", 1, 2));

        AwaitUnhandledException nullFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> FailureFactory.complete(described, () -> null, null,
                        config(1, 2, 0)));
        AwaitUnhandledException blankFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> FailureFactory.complete(described, () -> " \n ", null,
                        config(1, 2, 0)));
        AwaitUnhandledException valueFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> complete(rendered, "condition", null,
                        config(1, 2, 0)));

        assertTrue(nullFailure.getCause() instanceof NullPointerException);
        assertMessage(nullFailure, "condition description must not be null",
                "Origin: diagnostics", "Timing:");
        assertTrue(blankFailure.getCause() instanceof IllegalArgumentException);
        assertMessage(blankFailure, "condition description must not be blank",
                "Origin: diagnostics", "Timing:");
        assertTrue(valueFailure.getCause() instanceof NullPointerException);
        assertMessage(valueFailure, "actual toString() must not return null",
                "Origin: diagnostics", "Timing:");
    }

    @Test
    void fatalDiagnosticSignalsEscapeUnchanged() {
        var descriptionFatal = new InternalError("description fatal");
        var valueFatal = new InternalError("value fatal");
        var engineCause = new AssertionError("assertion failed");
        WaitOutcome<Object, Object> sourceFailure = sourceFailure(
                new IllegalStateException("source"), 1, 0);
        WaitOutcome<Object, Object> valueFailure = new LateUnsatisfiedTimeout<>(0,
                assertionUnsatisfiedAttempt(new ThrowingValue(valueFatal), "not ready",
                        engineCause, 1, 2));

        assertSame(descriptionFatal, assertThrows(InternalError.class,
                () -> FailureFactory.complete(sourceFailure,
                        () -> {
                            throw descriptionFatal;
                        }, null, config(1, 2, 0))));
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
        var engineCause = new IllegalStateException("source failed");
        InternalError thrown = assertThrows(InternalError.class,
                () -> FailureFactory.complete(
                        sourceFailure(engineCause, 1, 0),
                        () -> {
                            throw diagnosticCause;
                        }, null, config(1, 2, 0)));

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
                () -> complete(new LateUnsatisfiedTimeout<>(0,
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

    private static <S, R> R complete(WaitOutcome<S, R> outcome, String description,
            String explanation, WaitConfiguration configuration) {
        return FailureFactory.complete(outcome, () -> description,
                explanation, configuration);
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
                        afterObservation(completedNanos), observed, mismatch));
    }

    private static <S> AwaitAttempt<S, Object> assertionUnsatisfiedAttempt(
            S observed, String mismatch, AssertionError assertion,
            long number, long completedNanos) {
        return new AwaitAttempt<>(number, ACQUISITION,
                new AwaitAttempt.Outcome.AssertionUnsatisfied<>(
                        afterObservation(completedNanos), observed, mismatch, assertion));
    }

    private static WaitOutcome<Object, Object> sourceFailure(
            Throwable failure, long number, long completedNanos) {
        var timing = new AwaitAttempt.Timing.BeforeObservation(
                Duration.ZERO, Duration.ZERO, Duration.ofNanos(completedNanos));
        return new Uncontrolled<>(new AwaitAttempt<>(number, ACQUISITION,
                new AwaitAttempt.Outcome.SourceRetrievalFailed<>(timing, failure)));
    }

    private static WaitOutcome<Object, Object> waitingFailure(
            Throwable failure, long number, long completedNanos) {
        var timing = new AwaitAttempt.Timing.BeforeRetrieval(
                Duration.ZERO, Duration.ofNanos(completedNanos));
        return new Uncontrolled<>(new AwaitAttempt<>(number, ACQUISITION,
                new AwaitAttempt.Outcome.WaitingFailed<>(timing, failure)));
    }

    private static WaitOutcome<Object, Object> conditionFailure(
            Object observed, Throwable failure, long number, long completedNanos) {
        return new Uncontrolled<>(new AwaitAttempt<>(number, ACQUISITION,
                new AwaitAttempt.Outcome.ConditionEvaluationFailed<>(
                        afterObservation(completedNanos), observed, failure)));
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

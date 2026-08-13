package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.sources.Source;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.uncontrolled;
import static io.github.gromoff97.awium.engine.Attempt.*;
import static io.github.gromoff97.awium.engine.Attempt.Origin.*;
import static io.github.gromoff97.awium.engine.Attempt.Uncontrolled.*;
import static io.github.gromoff97.awium.engine.WaitOutcome.*;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DiagnosticsSnapshotTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long MILLISECOND = 1_000_000L;

    @Test
    void controlledFailuresRetainTheirSemanticContext() {
        var betweenAssertion = new AssertionError("collection was empty");
        var stabilityAssertion = new AssertionError("optional was empty");
        WaitOutcome<Object> between = new TimeoutBetweenObservations<>(0,
                10 * SECOND, new Unsatisfied<>(null, "collection was empty",
                        betweenAssertion, 4, 9 * SECOND));
        WaitOutcome<Object> lateUnsatisfied = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>("Payment[PENDING]", "status was PENDING",
                        null, 100, 10 * SECOND + 200 * MILLISECOND));
        WaitOutcome<Object> lateSatisfied = new LateSatisfiedTimeout<>(0,
                new Satisfied<>("Payment[COMPLETED]", new Object(), 100,
                        10 * SECOND + 200 * MILLISECOND));
        WaitOutcome<Object> stability = new StabilityLoss<>(0, 7 * SECOND,
                new Unsatisfied<>("Optional.empty", "optional was empty",
                        stabilityAssertion, 71,
                        9 * SECOND + 100 * MILLISECOND));

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
        AwaitStabilizationException stabilityFailure = assertThrows(
                AwaitStabilizationException.class,
                () -> complete(stability, "optional to remain present",
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
                Condition did not remain stable for the required duration

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
                    Required stability: 5 seconds
                    Failure detected after: 2 seconds 100 milliseconds
                    Elapsed: 9 seconds 100 milliseconds
                    Polling interval: 100 milliseconds

                Cause:
                    Type: AssertionError
                    Message: optional was empty""", stabilityFailure.getMessage());
        assertSame(betweenAssertion, betweenFailure.getCause());
        assertSame(stabilityAssertion, stabilityFailure.getCause());
    }

    @Test
    void uncontrolledFailuresKeepCategoryCauseAttemptAndObservation() {
        var sourceCause = new IllegalStateException("source failed");
        var conditionCause = new IllegalArgumentException("condition failed");
        var waitingCause = new IllegalStateException("waiting failed");
        var interruption = new InterruptedException("stopped");

        AwaitSourceRetrievalException sourceFailure = assertThrows(
                AwaitSourceRetrievalException.class,
                () -> complete(new BeforeObservation<>(SOURCE, sourceCause,
                                2, SECOND), "condition", "business reason",
                        config(SECOND, 10 * SECOND, 0)));
        AwaitConditionEvaluationException conditionFailure = assertThrows(
                AwaitConditionEvaluationException.class,
                () -> complete(new AfterObservation<>(CONDITION, "actual",
                                conditionCause, 3, SECOND), "condition",
                        "business reason", config(SECOND, 10 * SECOND, 0)));
        AwaitUnhandledException waitingFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> complete(new BeforeObservation<>(WAITING, waitingCause,
                                4, SECOND), "condition", null,
                        config(SECOND, 10 * SECOND, 0)));
        AwaitInterruptedException interruptedFailure = assertThrows(
                AwaitInterruptedException.class,
                () -> complete(new BeforeObservation<>(WAITING, interruption,
                                5, SECOND), "condition", null,
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
        RuntimeCondition<Object, Object> condition = new RuntimeCondition<>(
                Evaluation::satisfied, () -> {
                    assertTrue(interrupted());
                    return "condition";
                }, null);
        currentThread().interrupt();

        try {
            AwaitInterruptedException failure = assertThrows(
                    AwaitInterruptedException.class,
                    () -> new FailureFactory().complete(
                            new BeforeObservation<>(WAITING, interruption,
                                    1, 0), condition,
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
                    () -> await((Source<Object>) Object::new)
                            .until(condition));

            assertSame(cause, failure.getCause());
            assertTrue(currentThread().isInterrupted());
        } finally {
            interrupted();
        }
    }

    @Test
    void assertionCauseIsRetainedAndItsMessageIsReadOnce() {
        var assertion = new CountingAssertion("assertion failed");
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>("actual", "business mismatch", assertion,
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
        RuntimeCondition<Object, Object> condition = new RuntimeCondition<>(
                Evaluation::satisfied, () -> {
                    throw new AssertionError("description must not be read");
                }, null);

        assertSame(result, new FailureFactory().complete(
                new Satisfied<>(new ThrowingValue(new AssertionError()), result,
                        1, 0), condition, config(1, 2, 0)));
    }

    @Test
    void diagnosticRenderingFailuresAreUnhandledWithTheirOriginalCause() {
        var descriptionCause = new IllegalStateException("description failed");
        var valueCause = new IllegalArgumentException("toString failed");
        var actual = new ThrowingValue(valueCause);
        RuntimeCondition<Object, Object> brokenDescription = new RuntimeCondition<>(
                Evaluation::satisfied, () -> {
                    throw descriptionCause;
                }, "business reason");
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>(actual, "not ready", null, 1, 2));

        AwaitUnhandledException descriptionFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> new FailureFactory().complete(outcome, brokenDescription,
                        config(1, 2, 0)));
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
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>(new ThrowingValue(diagnosticCause),
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
    void stabilityDiagnosticsDoNotDescribeDetectionTimeAsStable() {
        WaitOutcome<Object> outcome = new StabilityLoss<>(0, 0,
                new Unsatisfied<>("actual", "condition was false", null,
                        2, 11));

        AwaitStabilizationException failure = assertThrows(
                AwaitStabilizationException.class,
                () -> complete(outcome, "condition", null,
                        config(1, 10, 2)));

        assertMessage(failure, "Required stability: 2 nanoseconds",
                "Failure detected after: 11 nanoseconds");
        assertFalse(failure.getMessage().contains("Stable for:"));
    }

    @Test
    void invalidDiagnosticTextIsUnhandled() {
        RuntimeCondition<Object, Object> nullDescription = new RuntimeCondition<>(
                Evaluation::satisfied, () -> null, null);
        RuntimeCondition<Object, Object> blankDescription = new RuntimeCondition<>(
                Evaluation::satisfied, () -> " \n ", null);
        WaitOutcome<Object> described = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>("actual", "not ready", null, 1, 2));
        WaitOutcome<Object> rendered = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>(new NullStringValue(), "not ready", null,
                        1, 2));

        AwaitUnhandledException nullFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> new FailureFactory().complete(described, nullDescription,
                        config(1, 2, 0)));
        AwaitUnhandledException blankFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> new FailureFactory().complete(described, blankDescription,
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
        WaitOutcome<Object> sourceFailure = new BeforeObservation<>(SOURCE,
                new IllegalStateException("source"), 1, 0);
        WaitOutcome<Object> valueFailure = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>(new ThrowingValue(valueFatal), "not ready",
                        engineCause, 1, 2));

        assertSame(descriptionFatal, assertThrows(InternalError.class,
                () -> new FailureFactory().complete(sourceFailure,
                        new RuntimeCondition<>(Evaluation::satisfied, () -> {
                            throw descriptionFatal;
                        }, null), config(1, 2, 0))));
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
                () -> complete(new BeforeObservation<>(SOURCE,
                                virtualMachineError, 1, 0),
                        "condition", null, config(1, 2, 0))));
        assertSame(threadDeath, assertThrows(ThreadDeath.class,
                () -> complete(new BeforeObservation<>(CONDITION,
                                threadDeath, 1, 0),
                        "condition", null, config(1, 2, 0))));
    }

    @Test
    void fatalEmergencyDiagnosticsRetainDiagnosticAndEngineCauses() {
        var fatal = new InternalError("emergency diagnostics fatal");
        var diagnosticCause = new FatalMessageException(fatal);
        var engineCause = new IllegalStateException("source failed");
        RuntimeCondition<Object, Object> condition = new RuntimeCondition<>(
                Evaluation::satisfied, () -> {
                    throw diagnosticCause;
                }, null);

        InternalError thrown = assertThrows(InternalError.class,
                () -> new FailureFactory().complete(
                        new BeforeObservation<>(SOURCE, engineCause, 1, 0),
                        condition, config(1, 2, 0)));

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
                () -> complete(new BeforeObservation<>(SOURCE, cause, 1, 0),
                        "condition\rdescription", "business\nreason",
                        config(1, 2, 0)));
        AwaitTimeoutException arrayFailure = assertThrows(
                AwaitTimeoutException.class,
                () -> complete(new LateUnsatisfiedTimeout<>(0,
                                new Unsatisfied<>(new int[] {1, 2}, "not ready",
                                        null, 1, 2)), "condition", null,
                        config(1, 2, 0)));

        assertFalse(sourceFailure.getMessage().contains("\r"));
        assertTrue(sourceFailure.getMessage().lines()
                .noneMatch(line -> line.endsWith(" ")));
        assertMessage(sourceFailure, "condition", "description", "business",
                "reason", "first", "second");
        assertMessage(arrayFailure, "[1, 2]");
    }

    private static <R> R complete(WaitOutcome<R> outcome, String description,
            String explanation, WaitConfiguration configuration) {
        return new FailureFactory().complete(outcome,
                runtime(description, explanation), configuration);
    }

    private static <R> RuntimeCondition<Object, R> runtime(
            String description, String explanation) {
        return new RuntimeCondition<>(value -> satisfied(null),
                () -> description, explanation);
    }

    private static WaitConfiguration config(long every, long upTo,
            long stableFor) {
        return new WaitConfiguration(every, upTo, stableFor);
    }

    private static void assertMessage(Throwable failure, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(failure.getMessage().contains(fragment),
                    () -> "missing diagnostic fragment: " + fragment
                            + "\n" + failure.getMessage());
        }
    }

    private static void assertCondition(Throwable failure, String expectation,
            String rationale) {
        String expected = "Condition:\n    Expectation: " + expectation + "\n";
        if (rationale != null) {
            expected += "    Importance: " + rationale + "\n";
        }
        assertTrue((failure.getMessage() + "\n").contains(expected),
                failure::getMessage);
        assertFalse(failure.getMessage().contains("Because:"));
        assertFalse(failure.getMessage().contains("Reason:"));
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

package io.github.gromoff97.assertility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
class DiagnosticsSnapshotTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long MILLISECOND = 1_000_000L;

    @Test
    void formatsTheBetweenObservationsTimeoutBaselineVerbatim() {
        WaitOutcome<Object> outcome = WaitOutcome.timeoutBetween(0, 10 * SECOND,
                new WaitOutcome.LastObservation(4, 9 * SECOND,
                        "collection was empty", null));

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> complete(outcome, "collection to be non-empty", null,
                        config(3 * SECOND, 10 * SECOND, 0)));

        assertEquals("""
                Await timed out

                Condition: collection to be non-empty
                Reason: acquisition deadline elapsed before the next observation

                Last observation:
                    Attempt: 4
                    Completed after: 9 seconds
                    Mismatch: collection was empty

                Timing:
                    Waited up to: 10 seconds
                    Elapsed: 10 seconds
                    Interval: 3 seconds""", failure.getMessage());
    }

    @Test
    void formatsALateUnsatisfiedTimeoutWithTheTerminalAttempt() {
        WaitOutcome<Object> outcome = WaitOutcome.lateUnsatisfied(0,
                10 * SECOND + 200 * MILLISECOND,
                ObservationOutcome.unsatisfied(
                        "Payment[id=42, status=PENDING]",
                        "payment status was PENDING", null, 100));

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> complete(outcome, "payment status equals COMPLETED",
                        "payment must complete",
                        config(100 * MILLISECOND, 10 * SECOND, 0)));

        assertEquals("""
                Await timed out

                Condition: payment status equals COMPLETED
                Observed: Payment[id=42, status=PENDING]
                Mismatch: payment status was PENDING
                Because: payment must complete

                Timing:
                    Waited up to: 10 seconds
                    Elapsed: 10 seconds 200 milliseconds
                    Attempts: 100
                    Interval: 100 milliseconds""", failure.getMessage());
    }

    @Test
    void formatsALateSatisfiedTimeoutWithTheTerminalAttempt() {
        WaitOutcome<Object> outcome = WaitOutcome.lateSatisfied(0,
                10 * SECOND + 200 * MILLISECOND,
                ObservationOutcome.satisfied(
                        "Optional[Payment[id=42, status=COMPLETED]]",
                        new Object(), 100));

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> complete(outcome,
                        "optional value equal to Payment[id=42, status=COMPLETED]",
                        "payment 42 must complete",
                        config(100 * MILLISECOND, 10 * SECOND, 0)));

        assertEquals("""
                Await timed out

                Condition: optional value equal to Payment[id=42, status=COMPLETED]
                Observed: Optional[Payment[id=42, status=COMPLETED]]
                Reason: condition became satisfied after the timeout
                Because: payment 42 must complete

                Timing:
                    Waited up to: 10 seconds
                    Elapsed: 10 seconds 200 milliseconds
                    Attempts: 100
                    Interval: 100 milliseconds""", failure.getMessage());
    }

    @Test
    void formatsAStabilityLossVerbatim() {
        WaitOutcome<Object> outcome = WaitOutcome.stabilityLoss(0, 7 * SECOND,
                9 * SECOND + 100 * MILLISECOND,
                ObservationOutcome.unsatisfied(
                        "Optional.empty", "optional was empty", null, 71));

        AwaitStabilizationException failure = assertThrows(
                AwaitStabilizationException.class,
                () -> complete(outcome, "optional to remain present",
                        "payment 42 must remain available",
                        config(100 * MILLISECOND, 10 * SECOND, 5 * SECOND)));

        assertEquals("""
                Await lost stability

                Expected: optional to remain present
                Because: payment 42 must remain available
                Required: 5 seconds
                Failure detected after: 2 seconds 100 milliseconds

                Observed:
                    Actual: Optional.empty
                    Mismatch: optional was empty

                Timing:
                    Acquired after: 7 seconds
                    Interval: 100 milliseconds""", failure.getMessage());
    }

    @Test
    void formatsAConditionFailureInGlobalFieldOrder() {
        var cause = new IllegalStateException("connection is closed");
        WaitOutcome<Object> outcome = WaitOutcome.uncontrolled(
                ObservationOutcome.uncontrolled(
                        ObservationOutcome.Origin.CONDITION, cause, 7,
                        "Payment[id=42, status=PENDING]"));

        AwaitConditionEvaluationException failure = assertThrows(
                AwaitConditionEvaluationException.class,
                () -> complete(outcome, "payment status equals COMPLETED",
                        "payment must complete", config(1, 2, 0)));

        assertSame(cause, failure.getCause());
        assertEquals("""
                Await condition evaluation failed

                Attempt: 7
                Condition: payment status equals COMPLETED
                Actual: Payment[id=42, status=PENDING]
                Because: payment must complete
                Cause: IllegalStateException: connection is closed""",
                failure.getMessage());
    }

    @Test
    void formatsAnInterruptionVerbatim() {
        var cause = new InterruptedException(
                "caller thread interrupt flag was set");
        WaitOutcome<Object> outcome = WaitOutcome.uncontrolled(
                ObservationOutcome.uncontrolled(
                        ObservationOutcome.Origin.CONDITION, cause, 12,
                        "Payment[id=42, status=PENDING]"));

        AwaitInterruptedException failure = assertThrows(
                AwaitInterruptedException.class,
                () -> complete(outcome, "payment status equals COMPLETED",
                        "payment must complete", config(1, 2, 0)));

        assertSame(cause, failure.getCause());
        assertEquals("""
                Await was interrupted

                Attempt: 12
                Origin: condition
                Condition: payment status equals COMPLETED
                Actual: Payment[id=42, status=PENDING]
                Because: payment must complete
                Cause: InterruptedException: caller thread interrupt flag was set""",
                failure.getMessage());
    }

    @Test
    void mapsSourceAndWaitingFailuresWithoutBorrowingAnActual() {
        var sourceCause = new AssertionError("source broke");
        var waitingCause = new IllegalStateException("parker broke");
        WaitOutcome<Object> source = WaitOutcome.uncontrolled(
                ObservationOutcome.uncontrolled(
                        ObservationOutcome.Origin.SOURCE, sourceCause, 3));
        WaitOutcome<Object> waiting = WaitOutcome.uncontrolled(
                ObservationOutcome.uncontrolled(
                        ObservationOutcome.Origin.WAITING, waitingCause, 4));

        AwaitSourceRetrievalException sourceFailure = assertThrows(
                AwaitSourceRetrievalException.class,
                () -> complete(source, "payment exists", null, config(1, 2, 0)));
        AwaitUnhandledException waitingFailure = assertThrows(
                AwaitUnhandledException.class,
                () -> complete(waiting, "payment exists", null, config(1, 2, 0)));

        assertSame(sourceCause, sourceFailure.getCause());
        assertEquals("""
                Await source retrieval failed

                Attempt: 3
                Condition: payment exists
                Cause: AssertionError: source broke""", sourceFailure.getMessage());
        assertSame(waitingCause, waitingFailure.getCause());
        assertEquals("""
                Await execution was unhandled

                Attempt: 4
                Condition: payment exists
                Cause: IllegalStateException: parker broke""",
                waitingFailure.getMessage());
    }

    @Test
    void returnsSuccessWithoutRenderingTerminalMetadata() {
        var result = new Object();
        var actual = new CountingValue("actual");
        var descriptions = new int[1];
        ConditionRuntime<Object, Object> runtime = new ConditionRuntime<>(
                value -> Evaluation.satisfied(result), () -> {
                    descriptions[0]++;
                    return "condition";
                }, null);
        WaitOutcome<Object> outcome = WaitOutcome.success(0, 0, 0,
                ObservationOutcome.satisfied(actual, result, 1));

        assertSame(result, new FailureFactory().complete(
                outcome, runtime, config(1, 2, 0)));
        assertEquals(0, descriptions[0]);
        assertEquals(0, actual.calls);
    }

    @Test
    void protectsDescriptionAndActualRenderingWithoutChangingClassification() {
        var actual = new ThrowingValue(new IllegalStateException("bad value"));
        var cause = new IllegalArgumentException("condition broke");
        var descriptions = new int[1];
        ConditionRuntime<Object, Object> runtime = new ConditionRuntime<>(
                value -> Evaluation.satisfied(value),
                () -> {
                    descriptions[0]++;
                    throw new IllegalStateException("bad description");
                }, null);
        WaitOutcome<Object> outcome = WaitOutcome.uncontrolled(
                ObservationOutcome.uncontrolled(
                        ObservationOutcome.Origin.CONDITION, cause, 1, actual));

        AwaitConditionEvaluationException failure = assertThrows(
                AwaitConditionEvaluationException.class,
                () -> new FailureFactory().complete(
                        outcome, runtime, config(1, 2, 0)));

        assertSame(cause, failure.getCause());
        assertEquals("""
                Await condition evaluation failed

                Attempt: 1
                Condition: condition description unavailable
                Actual: <value unavailable: toString() threw IllegalStateException>
                Cause: IllegalArgumentException: condition broke""",
                failure.getMessage());
        assertEquals(1, descriptions[0]);
        assertEquals(1, actual.calls);
    }

    @Test
    void usesDescriptionFallbackForNullAndBlankValues() {
        for (String description : new String[] {null, "", " \t\n"}) {
            var calls = new int[1];
            WaitOutcome<Object> outcome = WaitOutcome.uncontrolled(
                    ObservationOutcome.uncontrolled(
                            ObservationOutcome.Origin.SOURCE,
                            new IllegalStateException(), 1));

            AwaitSourceRetrievalException failure = assertThrows(
                    AwaitSourceRetrievalException.class,
                    () -> new FailureFactory().complete(outcome,
                            new ConditionRuntime<>(
                                    value -> Evaluation.satisfied(value), () -> {
                                        calls[0]++;
                                        return description;
                                    }, null), config(1, 2, 0)));

            assertTrue(failure.getMessage().contains(
                    "Condition: condition description unavailable"));
            assertEquals(1, calls[0]);
        }
    }

    @Test
    void readsATerminalAssertionMessageOnceAndReusesIt() {
        var assertion = new CountingAssertion("expected\r\nbut was");
        WaitOutcome<Object> outcome = WaitOutcome.lateUnsatisfied(0, 2,
                ObservationOutcome.unsatisfied("actual",
                        "assertion did not pass", assertion, 1));

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> complete(outcome, "condition", null, config(1, 2, 0)));

        assertSame(assertion, failure.getCause());
        assertEquals(1, assertion.calls);
        assertTrue(failure.getMessage().contains("""
                Mismatch:
                    expected
                    but was
                Cause:
                    CountingAssertion: expected
                    but was"""));
    }

    @Test
    void handlesBlankAndThrowingAssertionMessagesWithoutReplacingTheCause() {
        var blank = new CountingAssertion("  ");
        var throwing = new CountingAssertion(
                new IllegalStateException("message unavailable"));

        AwaitTimeoutException blankFailure = assertionTimeout(blank);
        AwaitTimeoutException throwingFailure = assertionTimeout(throwing);

        assertSame(blank, blankFailure.getCause());
        assertTrue(blankFailure.getMessage().contains(
                "Mismatch: assertion did not pass\nCause: CountingAssertion"));
        assertEquals(1, blank.calls);
        assertSame(throwing, throwingFailure.getCause());
        assertTrue(throwingFailure.getMessage().contains(
                "Mismatch: assertion did not pass\n"
                        + "Cause: CountingAssertion: <message unavailable: "
                        + "getMessage() threw IllegalStateException>"));
        assertEquals(1, throwing.calls);
    }

    @Test
    void readsAnUncontrolledCauseMessageOnce() {
        var cause = new CountingCause("broken");
        WaitOutcome<Object> outcome = WaitOutcome.uncontrolled(
                ObservationOutcome.uncontrolled(
                        ObservationOutcome.Origin.SOURCE, cause, 1));

        AwaitSourceRetrievalException failure = assertThrows(
                AwaitSourceRetrievalException.class,
                () -> complete(outcome, "condition", null, config(1, 2, 0)));

        assertEquals(1, cause.calls);
        assertTrue(failure.getMessage().endsWith(
                "Cause: CountingCause: broken"));
    }

    @Test
    void normalizesEveryMultilineUncontrolledFieldAndReadsCallbacksOnce() {
        var actual = new CountingValue("actual one\ractual two");
        var cause = new CountingCause("cause one\r\n cause two\rcause three");
        var descriptions = new int[1];
        ConditionRuntime<Object, Object> runtime = new ConditionRuntime<>(
                value -> Evaluation.satisfied(value), () -> {
                    descriptions[0]++;
                    return "condition one\r\n condition two\rcondition three";
                }, "because one\r\nbecause two");
        WaitOutcome<Object> outcome = WaitOutcome.uncontrolled(
                ObservationOutcome.uncontrolled(
                        ObservationOutcome.Origin.CONDITION, cause, 2, actual));

        AwaitConditionEvaluationException failure = assertThrows(
                AwaitConditionEvaluationException.class,
                () -> new FailureFactory().complete(
                        outcome, runtime, config(1, 2, 0)));

        assertEquals("""
                Await condition evaluation failed

                Attempt: 2
                Condition:
                    condition one
                     condition two
                    condition three
                Actual:
                    actual one
                    actual two
                Because:
                    because one
                    because two
                Cause:
                    CountingCause: cause one
                     cause two
                    cause three""", failure.getMessage());
        assertEquals(1, descriptions[0]);
        assertEquals(1, actual.calls);
        assertEquals(1, cause.calls);
    }

    @Test
    void rendersNullBlankThrowingAndAnonymousCauseMessages() {
        var nullMessage = new CountingCause(null);
        var blankMessage = new CountingCause(" \t");
        RuntimeException anonymous = new RuntimeException() {
            private static final long serialVersionUID = 1L;
        };
        RuntimeException anonymousMessageFailure = new RuntimeException() {
            private static final long serialVersionUID = 1L;
        };
        var throwingMessage = new ThrowingMessageCause(anonymousMessageFailure);

        AwaitSourceRetrievalException nullFailure = sourceFailure(nullMessage);
        AwaitSourceRetrievalException blankFailure = sourceFailure(blankMessage);
        AwaitSourceRetrievalException anonymousFailure = sourceFailure(anonymous);
        AwaitSourceRetrievalException throwingFailure = sourceFailure(
                throwingMessage);

        assertTrue(nullFailure.getMessage().endsWith("Cause: CountingCause"));
        assertTrue(blankFailure.getMessage().endsWith("Cause: CountingCause"));
        assertTrue(anonymousFailure.getMessage().endsWith(
                "Cause: " + anonymous.getClass().getName()));
        assertTrue(throwingFailure.getMessage().endsWith(
                "Cause: ThrowingMessageCause: <message unavailable: "
                        + "getMessage() threw "
                        + anonymousMessageFailure.getClass().getName() + ">"));
        assertSame(throwingMessage, throwingFailure.getCause());
        assertEquals(1, nullMessage.calls);
        assertEquals(1, blankMessage.calls);
        assertEquals(1, throwingMessage.calls);
    }

    @Test
    void diagnosticCallbacksMaySetTheInterruptFlagWithoutChangingTheOutcome() {
        var sourceCause = new IllegalStateException("source broke");
        WaitOutcome<Object> sourceOutcome = WaitOutcome.uncontrolled(
                ObservationOutcome.uncontrolled(
                        ObservationOutcome.Origin.SOURCE, sourceCause, 1));
        var interruptingActual = new InterruptingValue();
        WaitOutcome<Object> timeoutOutcome = WaitOutcome.lateUnsatisfied(0, 2,
                ObservationOutcome.unsatisfied(
                        interruptingActual, "not ready", null, 1));

        Thread.interrupted();
        try {
            AwaitSourceRetrievalException sourceFailure = assertThrows(
                    AwaitSourceRetrievalException.class,
                    () -> new FailureFactory().complete(sourceOutcome,
                            new ConditionRuntime<>(
                                    value -> Evaluation.satisfied(value), () -> {
                                        Thread.currentThread().interrupt();
                                        return "condition";
                                    }, null), config(1, 2, 0)));
            assertSame(sourceCause, sourceFailure.getCause());
            assertFalse(sourceFailure.getMessage().contains("Interrupt flag"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }

        try {
            AwaitTimeoutException timeoutFailure = assertThrows(
                    AwaitTimeoutException.class,
                    () -> complete(timeoutOutcome, "condition", null,
                            config(1, 2, 0)));
            assertNull(timeoutFailure.getCause());
            assertFalse(timeoutFailure.getMessage().contains("Interrupt flag"));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, interruptingActual.calls);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void callbackFreeEmergencyBranchFailureEscapesWithoutAnotherWrapper() {
        var actual = new NullStringValue();
        var formatterFailure = new IllegalStateException("formatter broke");
        var formatterCalls = new int[1];
        DiagnosticFormatter formatter = context -> {
            formatterCalls[0]++;
            context.actualValue();
            throw formatterFailure;
        };
        WaitOutcome<Object> outcome = WaitOutcome.lateUnsatisfied(0, 2,
                ObservationOutcome.unsatisfied(actual, "not ready", null, 1));

        NullPointerException failure = assertThrows(NullPointerException.class,
                () -> new FailureFactory(formatter).complete(outcome,
                        runtime("condition", null), config(1, 2, 0)));

        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(1, formatterCalls[0]);
        assertEquals(1, actual.calls);
        assertNull(ValueRenderer.render(new NullStringValue()));
        assertEquals(" \t", ValueRenderer.render(new CountingValue(" \t")));
    }

    @Test
    void usesCallbackFreeEmergencyFormattingOnce() {
        var formatterFailure = new IllegalStateException("formatter broke");
        var formatterCalls = new int[1];
        var descriptions = new int[1];
        var actual = new CountingValue("actual");
        DiagnosticFormatter formatter = context -> {
            formatterCalls[0]++;
            throw formatterFailure;
        };
        ConditionRuntime<Object, Object> runtime = new ConditionRuntime<>(
                value -> Evaluation.satisfied(value), () -> {
                    descriptions[0]++;
                    return "condition";
                }, "payment must complete");
        WaitOutcome<Object> outcome = WaitOutcome.lateUnsatisfied(0, 2,
                ObservationOutcome.unsatisfied(actual, "not ready", null, 7));

        AwaitUnhandledException failure = assertThrows(AwaitUnhandledException.class,
                () -> new FailureFactory(formatter).complete(
                        outcome, runtime, config(1, 2, 0)));

        assertSame(formatterFailure, failure.getCause());
        assertEquals(1, formatterCalls[0]);
        assertEquals(0, descriptions[0]);
        assertEquals(0, actual.calls);
        assertEquals("""
                Await execution was unhandled

                Attempt: 7
                Condition: condition description unavailable
                Actual: <value unavailable: diagnostics failed>
                Because: payment must complete
                Cause: IllegalStateException""", failure.getMessage());
    }

    @Test
    void emergencyFormattingReusesAlreadyMaterializedFragments() {
        var formatterFailure = new IllegalStateException("formatter broke");
        var actual = new CountingValue("rendered actual");
        DiagnosticFormatter formatter = context -> {
            context.conditionDescription();
            context.actualValue();
            throw formatterFailure;
        };
        WaitOutcome<Object> outcome = WaitOutcome.lateUnsatisfied(0, 2,
                ObservationOutcome.unsatisfied(actual, "not ready", null, 3));

        AwaitUnhandledException failure = assertThrows(AwaitUnhandledException.class,
                () -> new FailureFactory(formatter).complete(outcome,
                        runtime("rendered condition", null), config(1, 2, 0)));

        assertEquals(1, actual.calls);
        assertTrue(failure.getMessage().contains(
                "Condition: rendered condition\nActual: rendered actual"));
    }

    @Test
    void fatalDiagnosticSignalsEscapeUnchanged() {
        var descriptionFatal = new ThrowableFixtures.Fatal("description fatal");
        var valueFatal = new ThrowableFixtures.Fatal("value fatal");
        var messageFatal = new ThrowableFixtures.Fatal("message fatal");
        var formatterFatal = new ThrowableFixtures.Fatal("formatter fatal");
        WaitOutcome<Object> sourceFailure = WaitOutcome.uncontrolled(
                ObservationOutcome.uncontrolled(ObservationOutcome.Origin.SOURCE,
                        new IllegalStateException("source"), 1));
        WaitOutcome<Object> valueFailure = WaitOutcome.lateUnsatisfied(0, 2,
                ObservationOutcome.unsatisfied(new ThrowingValue(valueFatal),
                        "not ready", null, 1));
        var assertion = new CountingAssertion(messageFatal);
        WaitOutcome<Object> assertionFailure = WaitOutcome.lateUnsatisfied(0, 2,
                ObservationOutcome.unsatisfied("actual",
                        "assertion did not pass", assertion, 1));

        assertSame(descriptionFatal, assertThrows(ThrowableFixtures.Fatal.class,
                () -> new FailureFactory().complete(sourceFailure,
                        new ConditionRuntime<>(value -> Evaluation.satisfied(value),
                                () -> {
                                    throw descriptionFatal;
                                }, null), config(1, 2, 0))));
        assertSame(valueFatal, assertThrows(ThrowableFixtures.Fatal.class,
                () -> complete(valueFailure, "condition", null, config(1, 2, 0))));
        assertSame(messageFatal, assertThrows(ThrowableFixtures.Fatal.class,
                () -> complete(assertionFailure, "condition", null,
                        config(1, 2, 0))));
        assertSame(formatterFatal, assertThrows(ThrowableFixtures.Fatal.class,
                () -> new FailureFactory(context -> {
                    throw formatterFatal;
                }).complete(sourceFailure, runtime("condition", null),
                        config(1, 2, 0))));
    }

    @Test
    void normalizesNestedMultilineFieldsWithoutTrailingSpaces() {
        StringBuilder out = new StringBuilder();

        Diagnostics.field(out, 0, "Label", "first\r\n second\rthird\n");

        assertEquals("Label:\n    first\n     second\n    third\n\n",
                out.toString());
        assertFalse(List.of(out.toString().split("\n", -1)).stream()
                .anyMatch(line -> line.endsWith(" ")));
    }

    @Test
    void rendersArraysAndDurationsWithoutDependencies() {
        Object[] nested = {new int[] {1, 2}, new Object[] {3, 4}};
        Object[] recursive = new Object[1];
        recursive[0] = recursive;

        assertEquals(List.of("[true, false]", "[1, 2]", "[1, 2]", "[1, 2]",
                        "[1, 2]", "[a, b]", "[1.0, 2.0]", "[1.0, 2.0]",
                        "[[1, 2], [3, 4]]", "[[...]]", "null"),
                List.of(ValueRenderer.render(new boolean[] {true, false}),
                        ValueRenderer.render(new byte[] {1, 2}),
                        ValueRenderer.render(new short[] {1, 2}),
                        ValueRenderer.render(new int[] {1, 2}),
                        ValueRenderer.render(new long[] {1, 2}),
                        ValueRenderer.render(new char[] {'a', 'b'}),
                        ValueRenderer.render(new float[] {1, 2}),
                        ValueRenderer.render(new double[] {1, 2}),
                        ValueRenderer.render(nested), ValueRenderer.render(recursive),
                        ValueRenderer.render(null)));
        assertEquals("0 nanoseconds", DurationFormatter.format(0));
        assertEquals("1 minute 30 seconds",
                DurationFormatter.format(90 * SECOND));
        assertEquals("1 second 1 millisecond 1 microsecond 1 nanosecond",
                DurationFormatter.format(SECOND + MILLISECOND + 1_001));
    }

    private static AwaitTimeoutException assertionTimeout(
            CountingAssertion assertion) {
        WaitOutcome<Object> outcome = WaitOutcome.lateUnsatisfied(0, 2,
                ObservationOutcome.unsatisfied("actual",
                        "assertion did not pass", assertion, 1));
        return assertThrows(AwaitTimeoutException.class,
                () -> complete(outcome, "condition", null, config(1, 2, 0)));
    }

    private static AwaitSourceRetrievalException sourceFailure(Throwable cause) {
        WaitOutcome<Object> outcome = WaitOutcome.uncontrolled(
                ObservationOutcome.uncontrolled(
                        ObservationOutcome.Origin.SOURCE, cause, 1));
        return assertThrows(AwaitSourceRetrievalException.class,
                () -> complete(outcome, "condition", null, config(1, 2, 0)));
    }

    private static <R> R complete(WaitOutcome<R> outcome, String description,
            String explanation, WaitConfig config) {
        return new FailureFactory().complete(
                outcome, runtime(description, explanation), config);
    }

    private static <R> ConditionRuntime<Object, R> runtime(
            String description, String explanation) {
        return new ConditionRuntime<>(value -> Evaluation.satisfied(null),
                () -> description, explanation);
    }

    private static WaitConfig config(long every, long upTo, long stableFor) {
        return new WaitConfig(every, upTo, stableFor);
    }

    private static final class CountingValue {
        private final String text;
        private int calls;

        private CountingValue(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            calls++;
            return text;
        }
    }

    private static final class ThrowingValue {
        private final Error error;
        private final RuntimeException exception;
        private int calls;

        private ThrowingValue(RuntimeException exception) {
            this.exception = exception;
            this.error = null;
        }

        private ThrowingValue(Error error) {
            this.exception = null;
            this.error = error;
        }

        @Override
        public String toString() {
            calls++;
            if (error != null) {
                throw error;
            }
            throw exception;
        }
    }

    private static final class CountingAssertion extends AssertionError {
        private static final long serialVersionUID = 1L;

        private final String message;
        private final Throwable thrown;
        private int calls;

        private CountingAssertion(String message) {
            this.message = message;
            this.thrown = null;
        }

        private CountingAssertion(Throwable thrown) {
            this.message = null;
            this.thrown = thrown;
        }

        @Override
        public String getMessage() {
            calls++;
            if (thrown instanceof Error error) {
                throw error;
            }
            if (thrown instanceof RuntimeException exception) {
                throw exception;
            }
            return message;
        }
    }

    private static final class CountingCause extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String message;
        private int calls;

        private CountingCause(String message) {
            this.message = message;
        }

        @Override
        public String getMessage() {
            calls++;
            return message;
        }
    }

    private static final class ThrowingMessageCause extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final RuntimeException thrown;
        private int calls;

        private ThrowingMessageCause(RuntimeException thrown) {
            this.thrown = thrown;
        }

        @Override
        public String getMessage() {
            calls++;
            throw thrown;
        }
    }

    private static final class InterruptingValue {
        private int calls;

        @Override
        public String toString() {
            calls++;
            Thread.currentThread().interrupt();
            return "actual";
        }
    }

    private static final class NullStringValue {
        private int calls;

        @Override
        public String toString() {
            calls++;
            return null;
        }
    }
}

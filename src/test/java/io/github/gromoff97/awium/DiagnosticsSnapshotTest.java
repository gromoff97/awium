package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.diagnostics.FailureMessage;

import io.github.gromoff97.awium.engine.*;

import io.github.gromoff97.awium.exceptions.*;

import static io.github.gromoff97.awium.engine.Attempt.*;
import static io.github.gromoff97.awium.engine.Attempt.Origin.*;
import static io.github.gromoff97.awium.engine.Attempt.Uncontrolled.*;
import static io.github.gromoff97.awium.engine.WaitOutcome.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class DiagnosticsSnapshotTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long MILLISECOND = 1_000_000L;

    @Test
    void formatsTheBetweenObservationsTimeoutBaselineVerbatim() {
        WaitOutcome<Object> outcome =
                new TimeoutBetweenObservations<>(0, 10 * SECOND,
                        new Unsatisfied<>(null,
                                "collection was empty", null,
                                4, 9 * SECOND));

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
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>(
                        "Payment[id=42, status=PENDING]",
                        "payment status was PENDING", null, 100,
                        10 * SECOND + 200 * MILLISECOND));

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
        WaitOutcome<Object> outcome = new LateSatisfiedTimeout<>(0,
                new Satisfied<>(
                        "Optional[Payment[id=42, status=COMPLETED]]",
                        new Object(), 100,
                        10 * SECOND + 200 * MILLISECOND));

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
        WaitOutcome<Object> outcome = new StabilityLoss<>(
                0, 7 * SECOND,
                new Unsatisfied<>(
                        "Optional.empty", "optional was empty", null, 71,
                        9 * SECOND + 100 * MILLISECOND));

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
    void timingFieldsRemainRelativeToANonZeroClockOrigin() {
        long started = 100 * SECOND;
        WaitOutcome<Object> between =
                new TimeoutBetweenObservations<>(
                started, started + 10 * SECOND,
                new Unsatisfied<>(null, "not yet", null,
                        2, started + 9 * SECOND));
        WaitOutcome<Object> stability = new StabilityLoss<>(
                started, started + 7 * SECOND,
                new Unsatisfied<>(
                        "actual", "lost", null, 4,
                        started + 9 * SECOND + 100 * MILLISECOND));

        String betweenMessage = assertThrows(AwaitTimeoutException.class,
                () -> complete(between, "condition", null,
                        config(SECOND, 10 * SECOND, 0))).getMessage();
        String stabilityMessage = assertThrows(
                AwaitStabilizationException.class,
                () -> complete(stability, "condition", null,
                        config(SECOND, 10 * SECOND, 5 * SECOND))).getMessage();

        assertTrue(betweenMessage.contains("Completed after: 9 seconds"));
        assertTrue(betweenMessage.contains("Elapsed: 10 seconds"));
        assertTrue(stabilityMessage.contains(
                "Failure detected after: 2 seconds 100 milliseconds"));
        assertTrue(stabilityMessage.contains("Acquired after: 7 seconds"));
    }

    @Test
    void assertionCausesAreRetainedInBetweenAndStabilityDiagnostics() {
        var betweenAssertion = new AssertionError("between assertion");
        var stabilityAssertion = new AssertionError("stability assertion");
        WaitOutcome<Object> between =
                new TimeoutBetweenObservations<>(0, 10,
                        new Unsatisfied<>(null,
                                "assertion did not pass",
                                betweenAssertion, 2, 9));
        WaitOutcome<Object> stability = new StabilityLoss<>(
                0, 2, new Unsatisfied<>("actual",
                        "assertion did not pass", stabilityAssertion, 2, 3));

        AwaitTimeoutException betweenFailure = assertThrows(
                AwaitTimeoutException.class,
                () -> complete(between, "condition", null,
                        config(1, 10, 0)));
        AwaitStabilizationException stabilityFailure = assertThrows(
                AwaitStabilizationException.class,
                () -> complete(stability, "condition", null,
                        config(1, 10, 5)));

        assertSame(betweenAssertion, betweenFailure.getCause());
        assertTrue(betweenFailure.getMessage().contains(
                "Cause: AssertionError: between assertion"));
        assertSame(stabilityAssertion, stabilityFailure.getCause());
        assertTrue(stabilityFailure.getMessage().contains(
                "Cause: AssertionError: stability assertion"));
    }

    @Test
    void formatsAConditionFailureInGlobalFieldOrder() {
        var cause = new IllegalStateException("connection is closed");
        WaitOutcome<Object> outcome =
                new AfterObservation<>(
                        CONDITION,
                        "Payment[id=42, status=PENDING]", cause, 7, 0);

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
        WaitOutcome<Object> outcome =
                new AfterObservation<>(
                        CONDITION,
                        "Payment[id=42, status=PENDING]", cause, 12, 0);

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
        WaitOutcome<Object> source =
                new BeforeObservation<>(
                        SOURCE, sourceCause, 3, 0);
        WaitOutcome<Object> waiting =
                new BeforeObservation<>(
                        WAITING, waitingCause, 4, 0);

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
        RuntimeCondition<Object, Object> runtime = new RuntimeCondition<>(
                value -> Evaluation.satisfied(result), () -> {
                    throw new InternalError("description must not be read");
                }, null);
        WaitOutcome<Object> outcome =
                new Satisfied<>(
                        new ThrowingValue(new InternalError(
                                "actual must not be rendered")),
                        result, 1, 0);

        assertSame(result, new FailureFactory().complete(
                outcome, runtime, config(1, 2, 0)));
    }

    @Test
    void protectsDescriptionAndActualRenderingWithoutChangingClassification() {
        var actual = new ThrowingValue(new IllegalStateException("bad value"));
        var cause = new IllegalArgumentException("condition broke");
        var descriptions = new int[1];
        RuntimeCondition<Object, Object> runtime = new RuntimeCondition<>(
                value -> Evaluation.satisfied(value),
                () -> {
                    descriptions[0]++;
                    throw new IllegalStateException("bad description");
                }, null);
        WaitOutcome<Object> outcome =
                new AfterObservation<>(
                        CONDITION, actual, cause, 1, 0);

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
            WaitOutcome<Object> outcome =
                    new BeforeObservation<>(
                            SOURCE,
                            new IllegalStateException(), 1, 0);

            AwaitSourceRetrievalException failure = assertThrows(
                    AwaitSourceRetrievalException.class,
                    () -> new FailureFactory().complete(outcome,
                            new RuntimeCondition<>(
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
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(
                0, new Unsatisfied<>("actual",
                        "assertion did not pass", assertion, 1, 2));

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
    void normalizesEveryMultilineUncontrolledFieldAndReadsCallbacksOnce() {
        var actual = new CountingValue("actual one\ractual two");
        var cause = new CountingCause("cause one\r\n cause two\rcause three");
        var descriptions = new int[1];
        RuntimeCondition<Object, Object> runtime = new RuntimeCondition<>(
                value -> Evaluation.satisfied(value), () -> {
                    descriptions[0]++;
                    return "condition one\r\n condition two\rcondition three";
                }, "because one\r\nbecause two");
        WaitOutcome<Object> outcome =
                new AfterObservation<>(
                        CONDITION, actual, cause, 2, 0);

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
        RuntimeException anonymous = new RuntimeException() {
        };
        RuntimeException anonymousMessageFailure = new RuntimeException() {
        };
        var throwingMessage = new ThrowingMessageCause(anonymousMessageFailure);

        AwaitSourceRetrievalException nullFailure = sourceFailure(nullMessage);
        AwaitSourceRetrievalException anonymousFailure = sourceFailure(anonymous);
        AwaitSourceRetrievalException throwingFailure = sourceFailure(
                throwingMessage);

        assertTrue(nullFailure.getMessage().endsWith("Cause: CountingCause"));
        assertTrue(anonymousFailure.getMessage().endsWith(
                "Cause: " + anonymous.getClass().getName()));
        assertTrue(throwingFailure.getMessage().endsWith(
                "Cause: ThrowingMessageCause: <message unavailable: "
                        + "getMessage() threw "
                        + anonymousMessageFailure.getClass().getName() + ">"));
        assertSame(throwingMessage, throwingFailure.getCause());
        assertEquals(1, nullMessage.calls);
        assertEquals(1, throwingMessage.calls);
    }

    @Test
    void callbackFreeEmergencyBranchFailureEscapesWithoutAnotherWrapper() {
        var actual = new CountingValue(null);
        var formatterFailure = new IllegalStateException("formatter broke");
        Function<FailureMessage.Context, String> formatter = context -> {
            context.actualValue();
            throw formatterFailure;
        };
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(
                0, new Unsatisfied<>(
                        actual, "not ready", null, 1, 2));

        assertThrows(NullPointerException.class,
                () -> new FailureFactory(new FailureMessage(formatter)).complete(
                        outcome, runtime("condition", null), config(1, 2, 0)));

        assertEquals(1, actual.calls);
    }

    @Test
    void usesCallbackFreeEmergencyFormattingOnce() {
        var formatterFailure = new IllegalStateException("formatter broke");
        Function<FailureMessage.Context, String> formatter = context -> {
            throw formatterFailure;
        };
        RuntimeCondition<Object, Object> runtime = new RuntimeCondition<>(
                value -> Evaluation.satisfied(value), () -> {
                    throw new InternalError("description must not be read");
                }, "payment must complete");
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(
                0, new Unsatisfied<>(
                        new ThrowingValue(new InternalError(
                                "actual must not be rendered")),
                        "not ready", null, 7, 2));

        AwaitUnhandledException failure = assertThrows(AwaitUnhandledException.class,
                () -> new FailureFactory(new FailureMessage(formatter)).complete(
                        outcome, runtime, config(1, 2, 0)));

        assertSame(formatterFailure, failure.getCause());
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
        Function<FailureMessage.Context, String> formatter = context -> {
            context.conditionDescription();
            context.actualValue();
            throw formatterFailure;
        };
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(
                0, new Unsatisfied<>(
                        actual, "not ready", null, 3, 2));

        AwaitUnhandledException failure = assertThrows(AwaitUnhandledException.class,
                () -> new FailureFactory(new FailureMessage(formatter)).complete(
                        outcome, runtime("rendered condition", null),
                        config(1, 2, 0)));

        assertEquals(1, actual.calls);
        assertTrue(failure.getMessage().contains(
                "Condition: rendered condition\nActual: rendered actual"));
    }

    @Test
    void readsAReentrantConditionDescriptionOnce() {
        var calls = new int[1];
        var contexts = new FailureMessage.Context[1];
        RuntimeCondition<Object, Object> runtime = new RuntimeCondition<>(
                Evaluation::satisfied, () -> {
                    calls[0]++;
                    if (calls[0] == 1) {
                        contexts[0].conditionDescription();
                    }
                    return "condition";
                }, null);
        Function<FailureMessage.Context, String> formatter = context -> {
            contexts[0] = context;
            return context.conditionDescription();
        };
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(
                0, new Unsatisfied<>(
                        "actual", "not ready", null, 1, 2));

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> new FailureFactory(new FailureMessage(formatter)).complete(
                        outcome, runtime, config(1, 2, 0)));

        assertEquals("condition", failure.getMessage());
        assertEquals(1, calls[0]);
    }

    @Test
    void fatalDiagnosticSignalsEscapeUnchanged() {
        var descriptionFatal = new InternalError("description fatal");
        var valueFatal = new InternalError("value fatal");
        var messageFatal = new InternalError("message fatal");
        var formatterFatal = new InternalError("formatter fatal");
        WaitOutcome<Object> sourceFailure =
                new BeforeObservation<>(
                        SOURCE,
                        new IllegalStateException("source"), 1, 0);
        WaitOutcome<Object> valueFailure =
                new LateUnsatisfiedTimeout<>(0,
                        new Unsatisfied<>(new ThrowingValue(valueFatal),
                                "not ready", null, 1, 2));
        var assertion = new CountingAssertion(messageFatal);
        WaitOutcome<Object> assertionFailure =
                new LateUnsatisfiedTimeout<>(0,
                        new Unsatisfied<>("actual",
                                "assertion did not pass", assertion, 1, 2));

        assertSame(descriptionFatal, assertThrows(InternalError.class,
                () -> new FailureFactory().complete(sourceFailure,
                        new RuntimeCondition<>(value -> Evaluation.satisfied(value),
                                () -> {
                                    throw descriptionFatal;
                                }, null), config(1, 2, 0))));
        assertSame(valueFatal, assertThrows(InternalError.class,
                () -> complete(valueFailure, "condition", null, config(1, 2, 0))));
        assertSame(messageFatal, assertThrows(InternalError.class,
                () -> complete(assertionFailure, "condition", null,
                        config(1, 2, 0))));
        assertSame(formatterFatal, assertThrows(InternalError.class,
                () -> new FailureFactory(new FailureMessage(context -> {
                    throw formatterFatal;
                })).complete(sourceFailure, runtime("condition", null),
                        config(1, 2, 0))));
    }

    @Test
    void normalizesNestedMultilineFieldsWithoutTrailingSpaces() {
        WaitOutcome<Object> outcome =
                new BeforeObservation<>(
                        SOURCE,
                        new IllegalStateException("failure"), 1, 0);

        String message = assertThrows(AwaitSourceRetrievalException.class,
                () -> complete(outcome, "first\r\n second\rthird\n", null,
                        config(1, 2, 0))).getMessage();

        assertEquals("""
                Await source retrieval failed

                Attempt: 1
                Condition:
                    first
                     second
                    third

                Cause: IllegalStateException: failure""", message);
    }

    @Test
    void rendersArraysWithoutDependencies() {
        Object[] nested = {new int[] {1, 2}, new Object[] {3, 4}};
        Object[] recursive = new Object[1];
        recursive[0] = recursive;

        assertEquals(List.of("[1, 2]", "[[1, 2], [3, 4]]", "[[...]]", "null"),
                List.of(renderedActual(new int[] {1, 2}),
                        renderedActual(nested), renderedActual(recursive),
                        renderedActual(null)));
    }

    private static AwaitTimeoutException assertionTimeout(
            CountingAssertion assertion) {
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(
                0, new Unsatisfied<>("actual",
                        "assertion did not pass", assertion, 1, 2));
        return assertThrows(AwaitTimeoutException.class,
                () -> complete(outcome, "condition", null, config(1, 2, 0)));
    }

    private static AwaitSourceRetrievalException sourceFailure(Throwable cause) {
        WaitOutcome<Object> outcome =
                new BeforeObservation<>(
                        SOURCE, cause, 1, 0);
        return assertThrows(AwaitSourceRetrievalException.class,
                () -> complete(outcome, "condition", null, config(1, 2, 0)));
    }

    private static String renderedActual(Object actual) {
        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> complete(new LateUnsatisfiedTimeout<>(0,
                                new Unsatisfied<>(
                                        actual, "not ready", null, 1, 2)),
                        "condition", null, config(1, 2, 0)));
        String prefix = "Observed: ";
        int start = failure.getMessage().indexOf(prefix) + prefix.length();
        return failure.getMessage().substring(start,
                failure.getMessage().indexOf('\n', start));
    }

    private static <R> R complete(WaitOutcome<R> outcome, String description,
            String explanation, WaitConfiguration config) {
        return new FailureFactory().complete(
                outcome, runtime(description, explanation), config);
    }

    private static <R> RuntimeCondition<Object, R> runtime(
            String description, String explanation) {
        return new RuntimeCondition<>(value -> Evaluation.satisfied(null),
                () -> description, explanation);
    }

    private static WaitConfiguration config(long every, long upTo, long stableFor) {
        return new WaitConfiguration(every, upTo, stableFor);
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
        private final Throwable failure;
        private int calls;

        private ThrowingValue(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public String toString() {
            calls++;
            if (failure instanceof Error error) {
                throw error;
            }
            throw (RuntimeException) failure;
        }
    }

    private static final class CountingAssertion extends AssertionError {
        private final Object message;
        private int calls;

        private CountingAssertion(Object message) {
            this.message = message;
        }

        @Override
        public String getMessage() {
            calls++;
            if (message instanceof Error error) {
                throw error;
            }
            if (message instanceof RuntimeException exception) {
                throw exception;
            }
            return (String) message;
        }
    }

    private static final class CountingCause extends RuntimeException {
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

}

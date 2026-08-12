package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.diagnostics.FailureFactory;
import io.github.gromoff97.awium.diagnostics.FailureMessage;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.exceptions.*;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.engine.Attempt.*;
import static io.github.gromoff97.awium.engine.Attempt.Origin.*;
import static io.github.gromoff97.awium.engine.Attempt.Uncontrolled.*;
import static io.github.gromoff97.awium.engine.WaitOutcome.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Function;
import org.junit.jupiter.api.Test;

class DiagnosticsSnapshotTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long MILLISECOND = 1_000_000L;

    @Test
    void controlledFailuresRetainTheirSemanticContext() {
        WaitOutcome<Object> between = new TimeoutBetweenObservations<>(0,
                10 * SECOND, new Unsatisfied<>(null, "collection was empty",
                        null, 4, 9 * SECOND));
        WaitOutcome<Object> lateUnsatisfied = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>("Payment[PENDING]", "status was PENDING",
                        null, 100, 10 * SECOND + 200 * MILLISECOND));
        WaitOutcome<Object> lateSatisfied = new LateSatisfiedTimeout<>(0,
                new Satisfied<>("Payment[COMPLETED]", new Object(), 100,
                        10 * SECOND + 200 * MILLISECOND));
        WaitOutcome<Object> stability = new StabilityLoss<>(0, 7 * SECOND,
                new Unsatisfied<>("Optional.empty", "optional was empty",
                        null, 71, 9 * SECOND + 100 * MILLISECOND));

        AwaitTimeoutException betweenFailure = assertThrows(
                AwaitTimeoutException.class,
                () -> complete(between, "collection to be non-empty", null,
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

        assertMessage(betweenFailure, "Await timed out",
                "collection to be non-empty", "Attempt: 4",
                "collection was empty", "9 seconds", "10 seconds",
                "3 seconds");
        assertMessage(lateUnsatisfiedFailure, "Await timed out",
                "status equals COMPLETED", "Payment[PENDING]",
                "status was PENDING", "payment must complete",
                "Attempts: 100", "10 seconds 200 milliseconds");
        assertMessage(lateSatisfiedFailure, "Await timed out",
                "status equals COMPLETED", "Payment[COMPLETED]",
                "condition became satisfied after the timeout",
                "payment must complete", "Attempts: 100");
        assertMessage(stabilityFailure, "Await lost stability",
                "optional to remain present", "payment must remain available",
                "5 seconds", "2 seconds 100 milliseconds",
                "Optional.empty", "optional was empty", "7 seconds");
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
        assertMessage(sourceFailure, "source retrieval", "Attempt: 2",
                "condition", "business reason", "source failed");
        assertFalse(sourceFailure.getMessage().contains("Actual:"));
        assertMessage(conditionFailure, "condition evaluation", "Attempt: 3",
                "condition", "Actual: actual", "business reason",
                "condition failed");
        assertMessage(waitingFailure, "execution was unhandled", "Attempt: 4",
                "waiting failed");
        assertMessage(interruptedFailure, "interrupted", "Attempt: 5",
                "Origin: waiting", "stopped");
    }

    @Test
    void assertionCauseIsRetainedAndItsMessageIsReadOnce() {
        var assertion = new CountingAssertion("assertion failed");
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>("actual", "fallback", assertion,
                        2, 3 * SECOND));

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> complete(outcome, "condition", null,
                        config(SECOND, 2 * SECOND, 0)));

        assertSame(assertion, failure.getCause());
        assertMessage(failure, "assertion failed", "CountingAssertion");
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
    void diagnosticRenderingFailuresDoNotChangeTheFailureCategory() {
        var actual = new ThrowingValue(new IllegalStateException("toString"));
        RuntimeCondition<Object, Object> condition = new RuntimeCondition<>(
                Evaluation::satisfied, () -> {
                    throw new IllegalStateException("description");
                }, "business reason");
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>(actual, "not ready", null, 1, 2));

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> new FailureFactory().complete(outcome, condition,
                        config(1, 2, 0)));

        assertMessage(failure, "condition description unavailable",
                "value unavailable", "business reason", "not ready");
        assertTrue(actual.calls == 1);
    }

    @Test
    void emergencyFormattingReusesMaterializedContext() {
        var formatterFailure = new IllegalStateException("formatter broke");
        var actual = new CountingValue("rendered actual");
        Function<FailureMessage.Context, String> formatter = context -> {
            context.conditionDescription();
            context.actualValue();
            throw formatterFailure;
        };
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>(actual, "not ready", null, 3, 2));

        AwaitUnhandledException failure = assertThrows(AwaitUnhandledException.class,
                () -> new FailureFactory(new FailureMessage(formatter)).complete(
                        outcome, runtime("rendered condition", "business reason"),
                        config(1, 2, 0)));

        assertSame(formatterFailure, failure.getCause());
        assertMessage(failure, "execution was unhandled", "Attempt: 3",
                "rendered condition", "rendered actual", "business reason",
                "IllegalStateException");
        assertTrue(actual.calls == 1);
    }

    @Test
    void reentrantDescriptionIsMaterializedOnce() {
        var calls = new int[1];
        var contexts = new FailureMessage.Context[1];
        RuntimeCondition<Object, Object> condition = new RuntimeCondition<>(
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
        WaitOutcome<Object> outcome = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>("actual", "not ready", null, 1, 2));

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> new FailureFactory(new FailureMessage(formatter)).complete(
                        outcome, condition, config(1, 2, 0)));

        assertTrue(failure.getMessage().equals("condition"));
        assertTrue(calls[0] == 1);
    }

    @Test
    void fatalDiagnosticSignalsEscapeUnchanged() {
        var descriptionFatal = new InternalError("description fatal");
        var valueFatal = new InternalError("value fatal");
        var formatterFatal = new InternalError("formatter fatal");
        WaitOutcome<Object> sourceFailure = new BeforeObservation<>(SOURCE,
                new IllegalStateException("source"), 1, 0);
        WaitOutcome<Object> valueFailure = new LateUnsatisfiedTimeout<>(0,
                new Unsatisfied<>(new ThrowingValue(valueFatal), "not ready",
                        null, 1, 2));

        assertSame(descriptionFatal, assertThrows(InternalError.class,
                () -> new FailureFactory().complete(sourceFailure,
                        new RuntimeCondition<>(Evaluation::satisfied, () -> {
                            throw descriptionFatal;
                        }, null), config(1, 2, 0))));
        assertSame(valueFatal, assertThrows(InternalError.class,
                () -> complete(valueFailure, "condition", null,
                        config(1, 2, 0))));
        assertSame(formatterFatal, assertThrows(InternalError.class,
                () -> new FailureFactory(new FailureMessage(context -> {
                    throw formatterFatal;
                })).complete(sourceFailure, runtime("condition", null),
                        config(1, 2, 0))));
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

    private static final class CountingValue {
        private final String value;
        private int calls;

        private CountingValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            calls++;
            return value;
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

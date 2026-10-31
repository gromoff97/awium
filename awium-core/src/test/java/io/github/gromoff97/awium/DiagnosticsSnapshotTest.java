package io.github.gromoff97.awium;

import io.github.gromoff97.awium.support.FakeTime;
import org.junit.jupiter.params.provider.CsvSource;
import io.github.gromoff97.awium.AwaitCondition.Reference;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.execution.ExecutionStep;
import io.github.gromoff97.awium.execution.WaitCompletion;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.support.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.support.TestConditions.accepted;
import static io.github.gromoff97.awium.support.TestConditions.check;
import static io.github.gromoff97.awium.support.TestConditions.expected;
import static io.github.gromoff97.awium.execution.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.execution.ExecutionStep.Phase.PERSISTENCE;
import static io.github.gromoff97.awium.execution.WaitCompletion.*;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosticsSnapshotTest {

    @AutoClose
    private AutoCloseable classResource = () -> {};

    private final FakeTime time = new FakeTime(0);

    private static final Condition<Object, Object> CONDITION = diagnosticCondition("condition", null);
    private static final long SECOND = 1_000_000_000L;
    private static final long MILLISECOND = 1_000_000L;

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void timeoutBetweenAttemptsRetainsTheLastMismatchAndCompletionTime() {
        var cause = new AssertionError("collection was empty");
        var outcome = new Timeout(10 * SECOND,
                evaluatedAttempt(null, unsatisfied("collection was empty", cause), 9 * SECOND));

        var failure = renderFailure(diagnosticCondition("collection to be non-empty", null), TIMEOUT,
                outcome, config(3 * SECOND, 10 * SECOND, 0), 4);

        assertEquals("""
                Wait timed out
                Attempt: 4 (ACQUISITION)

                Condition: collection to be non-empty
                    Actual: null
                    Mismatch: collection was empty

                Timing: elapsed 10 s; timeout 10 s; interval 3 s
                Last completed after: 9 s
                Cause: java.lang.AssertionError: collection was empty""", failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    @Test
    void lateMismatchReportsTheActualCompletionTime() {
        var outcome = timeout(evaluatedAttempt("Payment[PENDING]", unsatisfied("status was PENDING"), 10 * SECOND + 200 * MILLISECOND));

        var failure = renderFailure(diagnosticCondition("status equals COMPLETED", null), TIMEOUT,
                outcome, config(100 * MILLISECOND, 10 * SECOND, 0), 100);

        assertEquals("""
                Wait timed out
                Attempt: 100 (ACQUISITION)

                Condition: status equals COMPLETED
                    Actual: "Payment[PENDING]"
                    Mismatch: status was PENDING

                Timing: elapsed 10 s 200 ms; timeout 10 s; interval 100 ms""", failure.getMessage());
    }

    @Test
    void lateSuccessReportsATimeoutWithoutAMismatch() {
        var outcome = timeout(evaluatedAttempt("Payment[COMPLETED]", satisfied("Payment[COMPLETED]"), 10 * SECOND + 200 * MILLISECOND));

        var failure = renderFailure(diagnosticCondition("status equals COMPLETED", null), TIMEOUT,
                outcome, config(100 * MILLISECOND, 10 * SECOND, 0), 100);

        assertEquals("""
                Wait timed out
                Attempt: 100 (ACQUISITION)

                Condition: status equals COMPLETED
                    Actual: "Payment[COMPLETED]"

                Timing: elapsed 10 s 200 ms; timeout 10 s; interval 100 ms""", failure.getMessage());
    }

    @Test
    void persistenceFailureRetainsTheMismatchAndBothDurations() {
        var cause = new AssertionError("optional was empty");
        var attempt = evaluatedAttempt("Optional.empty", unsatisfied("optional was empty", cause), 9 * SECOND + 100 * MILLISECOND);
        var outcome = new PersistenceFailure(7 * SECOND,
                new ExecutionStep<>(false, PERSISTENCE, attempt.completedAfterNanos(), attempt.attempt()));

        var failure = renderFailure(diagnosticCondition("optional to remain present", null), PERSISTENCE_FAILED,
                outcome, config(100 * MILLISECOND, 10 * SECOND, 5 * SECOND), 71);

        assertEquals("""
                Condition did not stay satisfied
                Attempt: 71 (PERSISTENCE)

                Condition: optional to remain present
                    Actual: "Optional.empty"
                    Mismatch: optional was empty

                Timing: elapsed 9 s 100 ms; timeout 10 s; interval 100 ms
                Persistence: failed after 2 s 100 ms; required 5 s; acquired after 7 s
                Cause: java.lang.AssertionError: optional was empty""", failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    @Test
    void sourceFailureHasItsCauseAndAttemptWithoutAnObservation() {
        var cause = new IllegalStateException("source failed");
        var failure = renderFailure(CONDITION, SOURCE_FAILED, sourceFailure(cause, SECOND), config(SECOND, 10 * SECOND, 0), 2);

        assertSame(cause, failure.getCause());
        assertEquals("""
                Source retrieval failed
                Attempt: 2 (ACQUISITION)

                Condition: condition

                Timing: elapsed 1 s; timeout 10 s; interval 1 s
                Cause: java.lang.IllegalStateException: source failed""", failure.getMessage());
    }

    @Test
    void conditionFailureRetainsItsCauseAttemptAndObservation() {
        var cause = new IllegalArgumentException("condition failed");
        var failure = renderFailure(CONDITION, CONDITION_FAILED,
                conditionFailure("actual", cause, SECOND), config(SECOND, 10 * SECOND, 0), 3);

        assertSame(cause, failure.getCause());
        assertEquals("""
                Condition evaluation failed
                Attempt: 3 (ACQUISITION)

                Condition: condition
                    Actual: "actual"

                Timing: elapsed 1 s; timeout 10 s; interval 1 s
                Cause: java.lang.IllegalArgumentException: condition failed""", failure.getMessage());
    }

    @Test
    void waitingFailureRetainsItsCauseAndAttempt() {
        var cause = new IllegalStateException("waiting failed");
        var failure = renderFailure(CONDITION, WAIT_FAILED, waitingFailure(cause, SECOND), config(SECOND, 10 * SECOND, 0), 4);

        assertSame(cause, failure.getCause());
        assertEquals("""
                Waiting failed
                Attempt: 4 (ACQUISITION)

                Condition: condition

                Timing: elapsed 1 s; timeout 10 s; interval 1 s
                Cause: java.lang.IllegalStateException: waiting failed""", failure.getMessage());
    }

    @Test
    void thrownInterruptionRemainsAWaitingFailure() {
        var cause = new InterruptedException("stopped");
        var failure = renderFailure(CONDITION, WAIT_FAILED, waitingFailure(cause, SECOND), config(SECOND, 10 * SECOND, 0), 5);

        assertSame(cause, failure.getCause());
        assertEquals("""
                Waiting failed
                Attempt: 5 (ACQUISITION)

                Condition: condition

                Timing: elapsed 1 s; timeout 10 s; interval 1 s
                Cause: java.lang.InterruptedException: stopped""", failure.getMessage());
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
        var condition = check("condition", actual -> {
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
        WaitCompletion outcome = timeout(
                evaluatedAttempt("actual", unsatisfied("business mismatch", assertion), 3 * SECOND));

        var failure = renderFailure(CONDITION, TIMEOUT, outcome, config(SECOND, 2 * SECOND, 0), 2);

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

            var captured = failure(diagnostic(waiting).until(accepted));
            var thrown = assertThrows(AwaitExecutionException.class,
                    () -> waiting.until(accepted));

            assertThat(captured.message()).contains("java.lang.IllegalStateException: " + message);
            assertThat(thrown.getMessage()).contains("java.lang.IllegalStateException: " + message);
            assertSame(cause, captured.cause());
            assertSame(cause, thrown.getCause());
            assertEquals(message, cause.getMessage());
        }
    }

    @ParameterizedTest(name = "{0}, message={1}")
    @MethodSource("blankMessageTerminals")
    void blankCauseMessagesAreOmittedAndReadOnlyOnce(BinaryOperator<Supplier<AwaitFailure>> terminal, String message) {
        int[] reads = {0};
        var cause = new IllegalStateException(message) {
            @Override
            public String getMessage() {
                reads[0]++;
                return super.getMessage();
            }
        };
        Callable<Object> source = () -> { throw cause; };
        var waiting = await(source).usingTime(time, time);
        var failure = terminal.apply(
                () -> assertThrows(AwaitExecutionException.class, () -> waiting.until(accepted)).failure(),
                () -> failure(diagnostic(waiting).until(accepted))).get();

        assertEquals(SOURCE_FAILED, failure.reason());
        assertSame(cause, failure.cause());
        assertThat(failure.message()).endsWith("Cause: " + cause.getClass().getName());
        assertEquals(1, reads[0]);
    }

    private static Stream<Arguments> blankMessageTerminals() {
        return terminals().stream().flatMap(terminal -> Stream.of(null, "", " \r\n\t")
                .map(message -> arguments(terminal, message)));
    }

    @Test
    void diagnosticRenderingFailuresPreserveTheTimeoutWithoutACause() {
        var valueCause = new IllegalArgumentException("toString failed");
        var actual = new ThrowingValue(valueCause);
        WaitCompletion outcome = timeout(
                evaluatedAttempt(actual, unsatisfied("not ready"), 2));

        var valueFailure = renderFailure(CONDITION, TIMEOUT, outcome, config(1, 2, 0), 1);

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

        var failure = renderFailure(CONDITION, TIMEOUT,
                timeout(evaluatedAttempt(actual, unsatisfied("not ready"), 2)), config(1, 2, 0), 1);

        assertNull(failure.getCause());
        assertArrayEquals(new Throwable[]{interruption}, failure.getSuppressed());
        assertFalse(currentThread().isInterrupted());
    }

    @Test
    void expectedOperandRenderingFailureKeepsItsOriginalCause() {
        var cause = new IllegalArgumentException("expected toString failed");
        var expected = new ThrowingValue(cause);

        var failure = assertFailure(TIMEOUT,
                () -> timedAwait(Object::new, config(1, 2, 0), time, time).until(expected(expected)));

        assertNull(failure.getCause());
        assertArrayEquals(new Throwable[]{cause}, failure.getSuppressed());
        assertThat(failure).hasMessageContainingAll("Condition: expected value",
                "Expected: <unprintable>",
                "Actual: java.lang.Object@");
        assertEquals(1, expected.calls);
    }

    @Test
    void diagnosticRenderingFailureRetainsTheEngineCauseAsPrimary() {
        for (Throwable diagnosticCause : List.of(new IllegalStateException("toString failed"),
                new InternalError("value fatal"))) {
            var engineCause = new AssertionError("assertion failed");
            WaitCompletion outcome = timeout(
                    evaluatedAttempt(new ThrowingValue(diagnosticCause),
                            unsatisfied("not ready", engineCause), 2));

            var failure = renderFailure(CONDITION, TIMEOUT, outcome, config(1, 2, 0), 1);

            assertSame(engineCause, failure.getCause());
            assertThat(failure.getSuppressed()).singleElement().isSameAs(diagnosticCause);
        }
    }

    @Test
    void persistenceDiagnosticsDistinguishRequiredDurationFromDetectionTime() {
        var observations = List.<Callable<String>>of(() -> "ready", () -> {
            time.advanceNanos(10);
            return "gone";
        }).iterator();
        var failure = assertFailure(PERSISTENCE_FAILED,
                () -> await(() -> observations.next().call()).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10))
                        .persisting(ofNanos(2)).until(expected("ready")));

        assertThat(failure).hasMessageContainingAll("Attempt: 2 (PERSISTENCE)", "elapsed 11 ns",
                "required 2 ns",
                "failed after 11 ns");
    }

    @Test
    void sourceFailureDuringPersistenceIncludesItsPhaseAndElapsedTime() {
        var cause = new IOException("source failed after acquisition");
        var observations = List.<Callable<String>>of(() -> "ready", () -> "ready", () -> {
            time.advanceNanos(3);
            throw cause;
        }).iterator();

        var failure = assertFailure(SOURCE_FAILED,
                () -> await(() -> observations.next().call()).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10))
                        .persisting(ofNanos(5)).until(expected("ready")));

        assertSame(cause, failure.getCause());
        assertThat(failure).hasMessageContainingAll("Attempt: 3 (PERSISTENCE)", "elapsed 5 ns");
    }

    @ParameterizedTest(name = "array={0}")
    @ValueSource(booleans = {false, true})
    void nullFromToStringUsesStandardArrayFormatting(boolean array) {
        Object value = mock();
        when(value.toString()).thenReturn(null);
        Object actual = array ? new Object[] {value} : value;
        WaitCompletion rendered = timeout(
                evaluatedAttempt(actual, unsatisfied("not ready"), 2));

        var valueFailure = renderFailure(CONDITION, TIMEOUT, rendered, config(1, 2, 0), 1);

        assertNull(valueFailure.getCause());
        assertEquals(0, valueFailure.getSuppressed().length);
        assertThat(valueFailure).hasMessageContainingAll(array ? "Actual: [null]" : "Actual: null", "Timing:");
    }

    @ParameterizedTest
    @MethodSource("terminals")
    void diagnosticFailurePreservesTheInterruptRaisedByRendering(BinaryOperator<Supplier<AwaitFailure>> terminal) {
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
        var check = check("ready", value -> unsatisfied("not ready", original));
        var failure = terminal.apply(
                () -> assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure(),
                () -> failure(diagnostic(waiting).until(check))).get();
        assertEquals(TIMEOUT, failure.reason());
        assertSame(original, failure.cause());
        assertSame(diagnostic, failure.suppressed().getFirst());
        assertEquals(0, messageCalls[0]);
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("terminals")
    void diagnosticErrorMessagesAreNotEvaluatedRecursively(BinaryOperator<Supplier<AwaitFailure>> terminal) {
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
        }).usingTime(time, time).upTo(ofNanos(2));
        var check = check("not ready", value -> unsatisfied("not ready", original));
        var failure = terminal.apply(
                () -> assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure(),
                () -> failure(diagnostic(waiting).until(check))).get();
        assertEquals(TIMEOUT, failure.reason());
        assertSame(original, failure.cause());
        assertSame(diagnostic, failure.suppressed().getFirst());
        assertEquals(1, actual.calls);
        assertEquals(0, messageCalls[0]);
        assertFalse(currentThread().isInterrupted());
    }

    @Test
    void isolatedExceptionClassDoesNotRequireItsEnclosingClassForDiagnostics() throws Exception {
        var resource = CountingAssertion.class.getResourceAsStream("DiagnosticsSnapshotTest$CountingAssertion.class");
        classResource = resource;
        byte[] bytes = resource.readAllBytes();
        var loader = new ClassLoader(null) {
            Class<?> isolatedClass() {
                return defineClass(null, bytes, 0, bytes.length);
            }
        };
        var constructor = loader.isolatedClass().getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        var cause = (AssertionError) constructor.newInstance("isolated cause");

        var sourceFailure = assertFailure(SOURCE_FAILED,
                () -> await((Callable<String>) () -> { throw cause; }).until(accepted));
        assertSame(cause, sourceFailure.getCause());
        assertThat(sourceFailure).hasMessageContainingAll(cause.getClass().getName(), "isolated cause");

        var diagnosticFailure = assertFailure(TIMEOUT,
                () -> timedAwait(() -> new ThrowingValue(cause), config(1, 2, 0), time, time)
                        .until(check("not ready", actual -> unsatisfied())));
        assertNull(diagnosticFailure.getCause());
        assertArrayEquals(new Throwable[]{cause}, diagnosticFailure.getSuppressed());
        assertThat(diagnosticFailure).hasMessageContainingAll("Rendering failure: " + cause.getClass().getName());
    }

    @Test
    void repeatedRenderingFailureIsReportedOnceAcrossBothTerminals() {
        var diagnostic = new IllegalStateException("rendering failed");
        var value = new ThrowingValue(diagnostic);
        var waiting = await(() -> value).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(2));
        var check = new DefinedCondition<>("not ready", new Reference<>("Expected", value),
                (Object actual) -> unsatisfied());

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
                .every(ofNanos(1)).upTo(ofNanos(2)).until(expected(expected));
        var failure = failure(TIMEOUT, result);
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

        var failure = assertFailure(SOURCE_FAILED, () -> await(source).until(expected(expected)));

        assertSame(original, failure.getCause());
        assertThat(failure.getSuppressed()).singleElement().isSameAs(diagnostic);
    }

    @Test
    void multilineFieldsAreNormalizedAndArraysRenderWithoutDependencies() {
        var cause = new IllegalStateException("first\r\n\r\nsecond");
        Callable<String> source = () -> { throw cause; };
        var sourceFailure = assertFailure(SOURCE_FAILED,
                () -> await(source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                        .until(check("condition\rdescription", ignored -> satisfied(ignored))));
        var arrayFailure = renderFailure(CONDITION, TIMEOUT,
                timeout(evaluatedAttempt(new int[] {1, 2}, unsatisfied("not ready"), 2)), config(1, 2, 0), 1);

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
                    () -> timedAwait((Callable<Object>) () -> actual, config(1, 2, 0), time, time).until(expected(expected)));
            assertThat(failure).hasMessageContainingAll(rendered);
        }
    }

    @Test
    void errorsFromArrayElementToStringAreRetainedAsDiagnosticFailures() {
        var fatal = new InternalError("array element fatal");
        Object[] actual = {new ThrowingValue(fatal)};
        var failure = renderFailure(CONDITION, TIMEOUT,
                timeout(evaluatedAttempt(actual, unsatisfied("not ready"), 2)), config(1, 2, 0), 1);
        assertArrayEquals(new Throwable[]{fatal}, failure.getSuppressed());
    }

    @Test
    void arrayDiagnosticsKeepPrimitiveValuesSharedArraysAndCycleMarkers() {
        Object[] shared = {new int[] {1, 2}, "leaf"};
        Object[] cycle = new Object[1];
        cycle[0] = cycle;
        Object[] actual = {shared, shared, new Object[] {null, new char[] {'a', 'b'}}, cycle};
        var failure = renderFailure(CONDITION, TIMEOUT,
                timeout(evaluatedAttempt(actual, unsatisfied("not ready"), 2)), config(1, 2, 0), 1);
        assertThat(failure).hasMessageContainingAll("[[[1, 2], leaf], [[1, 2], leaf], [null, [a, b]], [[...]]]");
    }

    private static Throwable renderFailure(AwaitCondition condition, AwaitFailure.Reason reason, WaitCompletion outcome,
            WaitConfiguration configuration, long attemptNumber) {
        return renderFailure(ConditionExecution.start(condition, false), reason, outcome, configuration, attemptNumber);
    }

    private static Throwable renderFailure(ConditionRun<?, ?> condition,
            AwaitFailure.Reason reason, WaitCompletion outcome, WaitConfiguration configuration, long attemptNumber) {
        for (long number = 0; number < attemptNumber; number++) {
            condition.record(new Attempt.SatisfiedObservation<>(null));
        }
        var failure = failure(condition.finish(outcome, configuration));
        return assertFailure(reason, () -> new AwaitResult.Failed<>(List.of(), failure).orThrow());
    }

    private static Timeout timeout(ExecutionStep<?> step) {
        return new Timeout(step.completedAfterNanos(), step);
    }

    private static List<Named<BinaryOperator<Supplier<AwaitFailure>>>> terminals() {
        return List.of(named("await", (thrown, captured) -> thrown), named("tryAwait", (thrown, captured) -> captured));
    }

    private static <OBSERVED> ExecutionStep<OBSERVED> evaluatedAttempt(OBSERVED observed, ConditionResult<?> result, long completedNanos) {
        return new ExecutionStep<>(false, ACQUISITION, completedNanos,
                switch (result) {
                    case ConditionResult.Satisfied<?> ignored -> new Attempt.SatisfiedObservation<>(observed);
                    case ConditionResult.Unsatisfied<?> mismatch -> new Attempt.UnsatisfiedObservation<>(observed, mismatch.mismatch(), mismatch.cause());
                });
    }

    private static WaitCompletion sourceFailure(Throwable failure, long completedNanos) {
        return new Uncontrolled(new ExecutionStep<>(false, ACQUISITION, completedNanos,
                new FailedBeforeObservation<>(Operation.SOURCE, failure)), currentThread().isInterrupted());
    }

    private static WaitCompletion waitingFailure(Throwable failure, long completedNanos) {
        return new Uncontrolled(new ExecutionStep<>(false, ACQUISITION, completedNanos,
                new FailedBeforeObservation<>(Operation.WAITING, failure)), currentThread().isInterrupted());
    }

    private static WaitCompletion conditionFailure(Object observed, Throwable failure, long completedNanos) {
        return new Uncontrolled(new ExecutionStep<>(false, ACQUISITION, completedNanos,
                new FailedAfterObservation<>(observed, Operation.CONDITION, failure, null)), currentThread().isInterrupted());
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


    @ParameterizedTest(name = "tryAwait={0}, reference={1}")
    @CsvSource({"false,scalar", "true,scalar", "false,array", "true,array", "false,list", "true,list", "false,entry", "true,entry"})
    void renderingPreservesInterruptionBetweenFieldsButUsesTheFinalFlagWithinOneReference(boolean capture, String shape) {
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
        Object expected = switch (shape) {
            case "array" -> new Object[]{signalling, clearing};
            case "list" -> List.of(signalling, clearing);
            case "entry" -> java.util.Map.entry(signalling, clearing);
            default -> signalling;
        };
        var check = new AwaitCondition.PreservingCondition<>("ready",
                new Reference<>("Expected", expected),
                value -> unsatisfied("not ready", original));
        var waiting = timedAwait(() -> clearing, config(1, 2, 0), time, time);
        AwaitFailure failure = capture
                ? failure(diagnostic(waiting).until(check))
                : assertThrows(AwaitAssertionError.class, () -> waiting.until(check)).failure();

        assertEquals(TIMEOUT, failure.reason());
        assertSame(original, failure.cause());
        assertEquals(1, renders[0]);
        assertEquals(shape.equals("scalar") ? 1 : 2, renders[1]);
        assertEquals(1, renders[2]);
        assertEquals(shape.equals("scalar"), currentThread().isInterrupted());
    }
}

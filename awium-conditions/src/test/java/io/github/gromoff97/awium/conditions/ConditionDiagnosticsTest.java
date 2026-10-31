package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.execution.ExecutionStep;
import io.github.gromoff97.awium.execution.WaitCompletion;
import io.github.gromoff97.awium.execution.WaitTiming;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.github.gromoff97.awium.AwaitCondition.CollectingCondition;
import static io.github.gromoff97.awium.AwaitCondition.InputCondition;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.assertFailure;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.diagnosticCondition;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.failure;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.probe;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.condition;
import static io.github.gromoff97.awium.conditions.Conditions.equalTo;
import static io.github.gromoff97.awium.conditions.Conditions.isNotNull;
import static io.github.gromoff97.awium.execution.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.execution.WaitCompletion.*;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.interrupted;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConditionDiagnosticsTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void builtInOperandIsRetainedInDiagnostics() {
        var check = equalTo("expected");
        assertEquals(List.of(new Reference<>("Expected", "expected")), check.references());
        assertEquals("value equals expected", check.description());
    }

    @Test
    void capturedBuiltInOperandIdentifiesTheCurrentStage() {
        var run = ConditionExecution.<String, List<String>>start(captured(equalTo("first"), equalTo("second")), false);
        var evaluation = probe(run, "actual");
        var failure = renderFailure(run, TIMEOUT, timeout(evaluatedAttempt("actual", evaluation, 0)), timing(), 1);
        assertThat(failure).hasMessageContainingAll("Sequence: captured 0/2", "stage 1/2",
                "Condition: value equals expected", "Expected: \"first\"");
    }

    @Test
    void capturedMismatchIdentifiesTheCurrentStage() {
        var run = ConditionExecution.<String, List<String>>start(lifecycle(pending(), completed()), false);
        probe(run, "created");
        var evaluation = probe(run, "created");
        var failure = renderFailure(run, TIMEOUT, timeout(evaluatedAttempt("created", evaluation, 0)), timing(), 2);
        assertThat(failure).hasMessageContainingAll("Sequence: captured 1/3; stage 2/3",
                "Condition: payment status is pending", "Mismatch: payment status was created");
    }

    @Test
    void timeoutAfterIntermediateSuccessPointsToTheNextCondition() {
        var run = ConditionExecution.<String, List<String>>start(
                captured(equalTo("created"), equalTo("pending"), equalTo("completed")), false);
        var evaluation = probe(run, "created");
        var failure = renderFailure(run, TIMEOUT, timeout(evaluatedAttempt("created", evaluation, 0)), timing(), 1);
        assertThat(failure).hasMessageContainingAll("Sequence: captured 1/3", "stage 1/3",
                "Expected: \"created\"", "Next stage 2/3:", "Expected: \"pending\"");
    }

    @Test
    void capturedFinalStageMismatchKeepsTheEarlierProgress() {
        var run = ConditionExecution.<String, List<String>>start(lifecycle(pending(), completed()), false);
        for (String status : List.of("created", "pending", "completed")) probe(run, status);
        var evaluation = probe(run, "created");
        var failure = renderFailure(run, PERSISTENCE_FAILED,
                new PersistenceFailure(0, evaluatedAttempt("created", evaluation, 0)), timing(), 4);
        assertThat(failure).hasMessageContainingAll("Sequence: captured 2/3; stage 3/3",
                "Condition: payment status is completed", "Mismatch: payment status was created");
    }

    @Test
    void capturedNestedAssertionRetainsCauseAndStack() {
        var cause = new AssertionError("pending assertion failed");
        var frame = new StackTraceElement("Payments", "verifyPending", "Payments.java", 42);
        cause.setStackTrace(new StackTraceElement[]{frame});
        var run = ConditionExecution.<String, List<String>>start(lifecycle(
                condition("payment status is pending", value -> unsatisfied("payment status was created", cause)), completed()), false);
        probe(run, "created");
        var evaluation = probe(run, "created");
        var failure = renderFailure(run, TIMEOUT, timeout(evaluatedAttempt("created", evaluation, 0)), timing(), 2);
        assertSame(cause, failure.getCause());
        assertArrayEquals(new StackTraceElement[]{frame}, cause.getStackTrace());
        assertThat(failure).hasMessageContainingAll("Sequence: captured 1/3; stage 2/3", "pending assertion failed");
    }

    @Test
    void capturedNestedUncontrolledRetainsCauseAndStage() {
        var cause = new IllegalStateException("status callback failed");
        var run = ConditionExecution.<String, List<String>>start(lifecycle(
                condition("payment status is pending", value -> { throw cause; }), completed()), false);
        probe(run, "created");
        run.beginAttempt();
        assertSame(cause, assertThrows(Throwable.class, () -> run.probe("created")));
        var attempt = new Attempt.FailedAfterObservation<>("created", Attempt.Operation.CONDITION, cause, null);
        var failure = renderFailure(run, CONDITION_FAILED,
                new Uncontrolled(new ExecutionStep<>(false, ACQUISITION, 0, attempt)), timing(), 2);
        assertSame(cause, failure.getCause());
        assertThat(failure).hasMessageContainingAll("Sequence: captured 1/3; stage 2/3", "payment status is pending");
    }

    @Test
    void diagnosticFailureRetainsSequenceContext() throws Throwable {
        var cause = new IllegalArgumentException("actual toString failed");
        var stage = diagnosticCondition("inner second", null);
        var attempt = new ExecutionStep<>(false, ACQUISITION, 2,
                new Attempt.UnsatisfiedObservation<>(new ThrowingValue(cause), "inner mismatch", null));

        var failure = renderFailure(secondStage(stage), TIMEOUT, timeout(attempt), timing(), 2);

        assertThat(failure).hasMessageContainingAll("Sequence: captured 1/2",
                "Condition: inner second",
                "Mismatch: inner mismatch");
    }

    @Test
    void sequenceRenderingFailureDoesNotRepeatActualRendering() throws Throwable {
        int[] calls = {0};
        Object actual = mock();
        when(actual.toString()).thenAnswer(ignored -> {
            calls[0]++;
            return "observed value";
        });
        var cause = new IllegalStateException("expected toString failed");
        var expected = new ThrowingValue(cause);
        var stage = diagnosticCondition("second stage", new Reference<>("Expected", expected));
        var attempt = new ExecutionStep<>(false, ACQUISITION, 2,
                new Attempt.UnsatisfiedObservation<>(actual, "not ready", null));

        var failure = renderFailure(secondStage(stage), TIMEOUT, timeout(attempt), timing(), 2);

        assertNull(failure.getCause());
        assertArrayEquals(new Throwable[]{cause}, failure.getSuppressed());
        assertThat(failure).hasMessageContainingAll("Actual: observed value", "Sequence: captured 1/2",
                "Expected: <unprintable>", "Mismatch: not ready");
        assertEquals(1, calls[0]);
        assertEquals(1, expected.calls);
    }

    private static ConditionRun<Object, ?> secondStage(AwaitCondition second) throws Throwable {
        var run = ConditionExecution.<Object, Object>start(captured(isNotNull, second), false);
        run.beginAttempt();
        run.probe("ready");
        run.beginAttempt();
        return run;
    }

    private static Throwable renderFailure(AwaitCondition condition, AwaitFailure.Reason reason, WaitCompletion outcome,
            WaitTiming configuration, long attemptNumber) {
        return renderFailure(ConditionExecution.start(condition, false), reason, outcome, configuration, attemptNumber);
    }

    private static Throwable renderFailure(ConditionRun<?, ?> condition,
            AwaitFailure.Reason reason, WaitCompletion outcome, WaitTiming configuration, long attemptNumber) {
        for (long number = 0; number < attemptNumber; number++) {
            condition.record(new Attempt.SatisfiedObservation<>(null));
        }
        var failure = failure(condition.finish(outcome, configuration));
        return assertFailure(reason, () -> new AwaitResult.Failed<>(List.of(), failure).orThrow());
    }

    private static Timeout timeout(ExecutionStep<?> step) {
        return new Timeout(step.completedAfterNanos(), step);
    }

    private static CollectingCondition<InputCondition<String>> lifecycle(InputCondition<String> pending, InputCondition<String> completed) {
        return captured(status("created", "payment status is created"),
                pending, completed);
    }

    private static Condition<String, String> pending() {
        return status("pending", "payment status is pending");
    }

    private static Condition<String, String> completed() {
        return status("completed", "payment status is completed");
    }

    private static Condition<String, String> status(String expected, String description) {
        return condition(description,
                value -> value.equals(expected)
                        ? satisfied(value) : unsatisfied("payment status was " + value));
    }

    private static <OBSERVED> ExecutionStep<OBSERVED> evaluatedAttempt(OBSERVED observed, ConditionResult<?> result, long completedNanos) {
        return new ExecutionStep<>(false, ACQUISITION, completedNanos,
                switch (result) {
                    case ConditionResult.Satisfied<?> ignored -> new Attempt.SatisfiedObservation<>(observed);
                    case ConditionResult.Unsatisfied<?> mismatch -> new Attempt.UnsatisfiedObservation<>(observed, mismatch.mismatch(), mismatch.cause());
                });
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

    @Test
    void causeMessageFailureDoesNotRepeatValueRendering() {
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
        var first = diagnosticCondition("first stage", new Reference<>("Expected", values[0]));
        var second = new DefinedCondition<Object, Object>("second stage",
                new Reference<>("Expected", values[2]),
                () -> actual -> unsatisfied("not ready", original));
        var check = captured(first, second);
        var run = ConditionExecution.<Object, List<Object>>start(check, false);
        probe(run, values[1]);
        var evaluation = probe(run, values[1]);
        var failure = assertInstanceOf(AwaitResult.Failed.class,
                run.finish(timeout(evaluatedAttempt(values[1], evaluation, 0)), timing())).failure();

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

    @Test
    void failuresInSeveralFieldsDoNotHideLaterFieldsOrTheOriginalCause() {
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
        var second = new DefinedCondition<Object, Object>("second stage",
                new Reference<>("Expected", expected),
                () -> value -> unsatisfied("not ready", original));
        var check = captured(condition("condition", value -> satisfied(value)), second);
        var run = ConditionExecution.<Object, List<Object>>start(check, false);
        probe(run, actual);
        var evaluation = probe(run, actual);
        var failure = assertInstanceOf(AwaitResult.Failed.class,
                run.finish(timeout(evaluatedAttempt(actual, evaluation, 0)), timing())).failure();

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
    private static WaitTiming timing() {
        return mock(WaitTiming.class);
    }

    @ParameterizedTest
    @CsvSource({"1,SOURCE", "1,WAITING", "2,SOURCE", "2,WAITING"})
    void externalFailuresKeepTheStageAndCollectedProgress(int completed, Attempt.Operation operation) {
        var first = equalTo("ready");
        var last = equalTo("done");
        var run = ConditionExecution.<String, List<String>>start(captured(first, last), true);
        for (String actual : List.of("ready", "done").subList(0, completed)) {
            probe(run, actual);
            run.record(new Attempt.SatisfiedObservation<>(actual));
        }
        run.beginAttempt();
        var cause = new IllegalStateException("external failure");
        var attempt = new Attempt.FailedBeforeObservation<String>(operation, cause);
        run.record(attempt);
        var result = run.finish(new Uncontrolled(new ExecutionStep<>(false, ACQUISITION, 0, attempt)), timing());
        var failure = failure(result);
        assertSame(cause, failure.cause());
        assertThat(failure.message()).contains("Sequence: captured " + completed + "/2; stage 2/2");
        assertEquals(List.of("ready", "done").subList(0, completed), run.result());
        assertSame(first, result.conditions().getFirst().condition());
        assertSame(last, result.conditions().getLast().condition());
        assertSame(attempt, result.conditions().getLast().attempts().lastEntry().getValue());
    }

    @Test
    void completionAfterTheDeadlineStillDescribesTheFinalStage() {
        var run = ConditionExecution.<String, List<String>>start(captured(equalTo("ready"), equalTo("done")), false);
        probe(run, "ready");
        var evaluation = probe(run, "done");
        var failure = renderFailure(run, TIMEOUT, timeout(evaluatedAttempt("done", evaluation, 0)), timing(), 2);
        assertThat(failure).hasMessageContainingAll("Sequence: captured 2/2; stage 2/2", "Expected: \"done\"");
    }
}

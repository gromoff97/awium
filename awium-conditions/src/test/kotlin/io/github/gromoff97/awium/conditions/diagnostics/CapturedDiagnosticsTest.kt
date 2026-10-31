package io.github.gromoff97.awium.conditions.diagnostics

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.AwaitCondition.Reference
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.conditions.ConditionAssertions.diagnosticCondition
import io.github.gromoff97.awium.conditions.ConditionAssertions.failure
import io.github.gromoff97.awium.conditions.ConditionAssertions.probe
import io.github.gromoff97.awium.conditions.Conditions.captured
import io.github.gromoff97.awium.conditions.Conditions.condition
import io.github.gromoff97.awium.conditions.Conditions.equalTo
import io.github.gromoff97.awium.conditions.Conditions.isNotNull
import io.github.gromoff97.awium.execution.ConditionExecution.start
import io.github.gromoff97.awium.execution.ConditionRun
import io.github.gromoff97.awium.execution.DefinedCondition
import io.github.gromoff97.awium.execution.ExecutionStep
import io.github.gromoff97.awium.execution.ExecutionStep.Phase.ACQUISITION
import io.github.gromoff97.awium.execution.WaitCompletion
import io.github.gromoff97.awium.execution.WaitCompletion.PersistenceFailure
import io.github.gromoff97.awium.execution.WaitCompletion.Timeout
import io.github.gromoff97.awium.execution.WaitCompletion.Uncontrolled
import io.github.gromoff97.awium.execution.WaitTiming
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.result.Attempt
import io.github.gromoff97.awium.result.Attempt.FailedAfterObservation
import io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation
import io.github.gromoff97.awium.result.Attempt.Operation
import io.github.gromoff97.awium.result.Attempt.Operation.CONDITION
import io.github.gromoff97.awium.result.Attempt.SatisfiedObservation
import io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation
import io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import io.github.gromoff97.awium.result.ConditionResult.satisfied
import io.github.gromoff97.awium.result.ConditionResult.unsatisfied
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock

class CapturedDiagnosticsTest {
    @Test
    fun `a rejected first stage reports its expected operand`() {
        val run = start<String, List<String>>(captured(equalTo("first"), equalTo("second")), false)
        val failure = diagnose(run, Timeout(step(run, "actual")))
        assertThat(failure.message()).contains("Sequence: captured 0/2", "stage 1/2",
            "Condition: value equals expected", "Expected: \"first\"")
    }

    @Test
    fun `a mismatch identifies the current stage`() {
        val run = start<String, List<String>>(lifecycle(), false)
        step(run, "created")
        val failure = diagnose(run, Timeout(step(run, "created")))
        assertThat(failure.message()).contains("Sequence: captured 1/3; stage 2/3",
            "Condition: payment status is pending", "Mismatch: payment status was created")
    }

    @Test
    fun `timeout after an intermediate success points to the next stage`() {
        val run = start<String, List<String>>(captured(equalTo("created"), equalTo("pending"), equalTo("completed")), false)
        val failure = diagnose(run, Timeout(step(run, "created")))
        assertThat(failure.message()).contains("Sequence: captured 1/3", "stage 1/3", "Expected: \"created\"",
            "Next stage 2/3:", "Expected: \"pending\"")
    }

    @Test
    fun `a final stage mismatch reports the earlier captures`() {
        val run = start<String, List<String>>(lifecycle(), false)
        listOf("created", "pending", "completed").forEach { step(run, it) }
        val failure = diagnose(run, PersistenceFailure(0, step(run, "created")))
        assertThat(failure.message()).contains("Sequence: captured 2/3; stage 3/3",
            "Condition: payment status is completed", "Mismatch: payment status was created")
    }

    @Test
    fun `a returned assertion failure keeps its cause and stack trace`() {
        val cause = AssertionError("pending assertion failed")
        val frames = arrayOf(StackTraceElement("Payments", "verifyPending", "Payments.java", 42))
        cause.stackTrace = frames
        val pending = condition<String, String>("payment status is pending") { unsatisfied("payment status was created", cause) }
        val run = start<String, List<String>>(lifecycle(pending), false)
        step(run, "created")
        val failure = diagnose(run, Timeout(step(run, "created")))
        assertSame(cause, failure.cause())
        assertArrayEquals(frames, cause.stackTrace)
        assertThat(failure.message()).contains("Sequence: captured 1/3; stage 2/3", "pending assertion failed")
    }

    @Test
    fun `a thrown failure identifies the current stage`() {
        val cause = IllegalStateException("status callback failed")
        val pending = condition<String, String>("payment status is pending") { throw cause }
        val run = start<String, List<String>>(lifecycle(pending), false)
        step(run, "created")
        assertSame(cause, assertThrows(Throwable::class.java) { probe(run, "created") })
        val attempt = FailedAfterObservation("created", CONDITION, cause, null)
        run.record(attempt)
        val failure = diagnose(run, Uncontrolled(ExecutionStep(false, ACQUISITION, 0, attempt)))
        assertSame(cause, failure.cause())
        assertThat(failure.message()).contains("Sequence: captured 1/3; stage 2/3", "payment status is pending")
    }

    @Test
    fun `an observation rendering failure keeps the sequence context`() {
        val actual = RenderingValue { throw IllegalArgumentException("actual toString failed") }
        val run = secondStage(condition<Any, Any>("inner second") { unsatisfied("inner mismatch") })
        val failure = diagnose(run, Timeout(step(run, actual)))
        assertThat(failure.message()).contains("Sequence: captured 1/2", "Condition: inner second", "Mismatch: inner mismatch")
    }

    @Test
    fun `a stage operand rendering failure does not repeat observation rendering`() {
        val actual = RenderingValue { "observed value" }
        val cause = IllegalStateException("expected toString failed")
        val expected = RenderingValue { throw cause }
        val second = DefinedCondition<Any, Any>("second stage", Reference("Expected", expected), AwaitAttempt { unsatisfied("not ready") })
        val run = secondStage(second)
        val failure = diagnose(run, Timeout(step(run, actual)))

        assertNull(failure.cause())
        assertEquals(listOf(cause), failure.suppressed())
        assertThat(failure.message()).contains("Actual: observed value", "Sequence: captured 1/2",
            "Expected: <unprintable>", "Mismatch: not ready")
        assertEquals(1, actual.calls)
        assertEquals(1, expected.calls)
    }

    @Test
    fun `a broken cause message renders only the current stage operands`() {
        val root = RenderingValue { "root operand" }
        val actual = RenderingValue { "observed value" }
        val operand = RenderingValue { "stage operand" }
        val diagnostic = IllegalStateException("cause message failed")
        var messages = 0
        val original = object : AssertionError("not ready") {
            override val message: String get() { messages++; throw diagnostic }
        }
        val first = diagnosticCondition("first stage", Reference("Expected", root))
        val second = DefinedCondition<Any, Any>("second stage", Reference("Expected", operand),
            AwaitAttempt { unsatisfied("not ready", original) })
        val run = start<Any, List<Any>>(captured(first, second), false)
        step(run, actual)
        val failure = diagnose(run, Timeout(step(run, actual)))

        assertEquals(TIMEOUT, failure.reason())
        assertSame(original, failure.cause())
        assertEquals(listOf(diagnostic), failure.suppressed())
        assertThat(failure.message()).contains("Sequence: captured 1/2; stage 2/2", "Actual: observed value",
            "Condition: second stage\n    Expected: stage operand")
        assertEquals(listOf(0, 1, 1), listOf(root.calls, actual.calls, operand.calls))
        assertEquals(1, messages)
    }

    @Test
    fun `several rendering failures do not hide the stage or the original cause`() {
        val expectedFailure = IllegalStateException("expected rendering failed")
        val actualFailure = InternalError("actual rendering failed")
        val causeFailure = IllegalArgumentException("cause message failed")
        val expected = RenderingValue { throw expectedFailure }
        val actual = RenderingValue { throw actualFailure }
        var messages = 0
        val original = object : AssertionError() {
            override val message: String get() { messages++; throw causeFailure }
        }
        val second = DefinedCondition<Any, Any>("second stage", Reference("Expected", expected),
            AwaitAttempt { unsatisfied("not ready", original) })
        val run = start<Any, List<Any>>(captured(condition<Any, Any>("condition") { satisfied(it) }, second), false)
        step(run, actual)
        val failure = diagnose(run, Timeout(step(run, actual)))

        assertSame(original, failure.cause())
        assertEquals(listOf(expectedFailure, actualFailure, causeFailure), failure.suppressed())
        assertThat(failure.message()).contains("Condition: second stage\n    Expected: <unprintable>",
            "Mismatch: not ready", ": <unprintable>")
        assertEquals(listOf(1, 1, 1), listOf(expected.calls, actual.calls, messages))
    }

    @ParameterizedTest
    @CsvSource("1,SOURCE", "1,WAITING", "2,SOURCE", "2,WAITING")
    fun `an external failure keeps the current stage and captures`(completed: Int, operation: Operation) {
        val first = equalTo("ready")
        val last = equalTo("done")
        val run = start<String, List<String>>(captured(first, last), true)
        val values = listOf("ready", "done").take(completed)
        values.forEach { step(run, it) }
        run.beginAttempt()
        val cause = IllegalStateException("external failure")
        val attempt = FailedBeforeObservation<String>(operation, cause)
        run.record(attempt)
        val result = run.finish(Uncontrolled(ExecutionStep(false, ACQUISITION, 0, attempt)), mock(WaitTiming::class.java))

        assertSame(cause, failure(result).cause())
        assertThat(failure(result).message()).contains("Sequence: captured $completed/2; stage 2/2")
        assertEquals(values, run.result())
        assertEquals(listOf(first, last), result.conditions().map { it.condition() })
        assertSame(attempt, result.conditions().last().attempts().lastEntry().value)
    }

    @Test
    fun `completion after the deadline still describes the final stage`() {
        val run = start<String, List<String>>(captured(equalTo("ready"), equalTo("done")), false)
        step(run, "ready")
        val failure = diagnose(run, Timeout(step(run, "done")))
        assertThat(failure.message()).contains("Sequence: captured 2/2; stage 2/2", "Expected: \"done\"")
    }

    private fun lifecycle(pending: Condition<String, String> = status("pending")) =
        captured(status("created"), pending, status("completed"))

    private fun status(expected: String) = condition<String, String>("payment status is $expected") {
        if (it == expected) satisfied(it) else unsatisfied("payment status was $it")
    }

    private fun secondStage(second: AwaitCondition): ConditionRun<Any, *> {
        val run = start<Any, List<Any>>(captured(isNotNull, second), false)
        step(run, "ready")
        return run
    }

    private fun diagnose(run: ConditionRun<*, *>, outcome: WaitCompletion) =
        failure(run.finish(outcome, mock(WaitTiming::class.java)))

    private fun <OBSERVED> step(run: ConditionRun<OBSERVED, *>, actual: OBSERVED): ExecutionStep<OBSERVED> {
        val attempt: Attempt<OBSERVED> = when (val checked = probe(run, actual)) {
            is Satisfied -> SatisfiedObservation(actual)
            is Unsatisfied -> UnsatisfiedObservation(actual, checked.mismatch(), checked.cause())
        }
        run.record(attempt)
        return ExecutionStep(run.complete(), ACQUISITION, 0, attempt)
    }

    private class RenderingValue(val render: () -> String) {
        var calls = 0
            private set

        override fun toString(): String { calls++; return render() }
    }
}

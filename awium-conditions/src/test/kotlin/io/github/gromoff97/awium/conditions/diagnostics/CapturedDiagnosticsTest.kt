package io.github.gromoff97.awium.conditions.diagnostics

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.Await.tryAwait
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.newRun
import io.github.gromoff97.awium.AwaitCondition.Reference
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.failure
import io.github.gromoff97.awium.conditions.common.CommonConditions.captured
import io.github.gromoff97.awium.conditions.common.CommonConditions.condition
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.isNotNull
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation
import io.github.gromoff97.awium.result.Attempt.Operation
import io.github.gromoff97.awium.result.Attempt.SatisfiedObservation
import io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT
import io.github.gromoff97.awium.result.AttemptResult.satisfied
import io.github.gromoff97.awium.result.AttemptResult.unsatisfied
import java.time.Duration.ofMillis
import java.time.Duration.ofNanos
import java.util.concurrent.Callable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CapturedDiagnosticsTest {
    @Test
    fun `a rejected first stage reports its expected operand`() {
        val run = newRun<String, List<String>>(captured(equalTo("first"), equalTo("second")))
        val failure = diagnose(run, "actual")
        assertThat(failure.message()).contains("Sequence: \"captured 0/2", "stage 1/2",
            "Current stage: value equals expected", "Expected: \"first\"")
    }

    @Test
    fun `a mismatch identifies the current stage`() {
        val run = newRun<String, List<String>>(lifecycle())
        run.probe("created")
        val failure = diagnose(run, "created")
        assertThat(failure.message()).contains("Sequence: \"captured 1/3; stage 2/3\"",
            "Current stage: payment status is pending", "Mismatch: payment status was created")
    }

    @Test
    fun `an intermediate success points to the next stage`() {
        val run = newRun<String, List<String>>(captured(equalTo("created"), equalTo("pending"), equalTo("completed")))
        run.probe("created")
        assertEquals(listOf(
            Reference("Sequence", "captured 1/3; stage 1/3"),
            Reference("Current stage", run.historyConditions(run)[0]),
            Reference("Next stage 2/3", run.historyConditions(run)[1])),
            run.diagnostics(SatisfiedObservation("created")).fields())
    }

    @Test
    fun `a final stage mismatch reports the earlier captures`() {
        val run = newRun<String, List<String>>(lifecycle())
        listOf("created", "pending", "completed").forEach { run.probe(it) }
        val failure = diagnose(run, "created")
        assertThat(failure.message()).contains("Sequence: \"captured 2/3; stage 3/3\"",
            "Current stage: payment status is completed", "Mismatch: payment status was created")
    }

    @Test
    fun `a returned assertion failure keeps its cause and stack trace`() {
        val cause = AssertionError("pending assertion failed")
        val frames = arrayOf(StackTraceElement("Payments", "verifyPending", "Payments.java", 42))
        cause.stackTrace = frames
        val pending = condition<String, String>("payment status is pending") { unsatisfied("payment status was created", cause) }
        val run = newRun<String, List<String>>(lifecycle(pending))
        run.probe("created")
        val failure = diagnose(run, "created")
        assertSame(cause, failure.cause())
        assertArrayEquals(frames, cause.stackTrace)
        assertThat(failure.message()).contains("Sequence: \"captured 1/3; stage 2/3\"", "pending assertion failed")
    }

    @Test
    fun `a thrown failure identifies the current stage`() {
        val cause = IllegalStateException("status callback failed")
        val pending = condition<String, String>("payment status is pending") { throw cause }
        val run = newRun<String, List<String>>(lifecycle(pending))
        run.probe("created")
        val failure = diagnose(run, "created")
        assertSame(cause, failure.cause())
        assertThat(failure.message()).contains("Sequence: \"captured 1/3; stage 2/3\"", "payment status is pending")
    }

    @Test
    fun `an observation rendering failure keeps the sequence context`() {
        val actual = RenderingValue { throw IllegalArgumentException("actual toString failed") }
        val run = secondStage(condition<Any, Any>("inner second") { unsatisfied("inner mismatch") })
        val failure = diagnose(run, actual)
        assertThat(failure.message()).contains("Sequence: \"captured 1/2", "Current stage: inner second", "Mismatch: inner mismatch")
    }

    @Test
    fun `a stage operand rendering failure does not repeat observation rendering`() {
        val actual = RenderingValue { "observed value" }
        val cause = IllegalStateException("expected toString failed")
        val expected = RenderingValue { throw cause }
        val second = withOperand("second stage", Reference("Expected", expected), AwaitAttempt { unsatisfied("not ready") })
        val run = secondStage(second)
        val failure = diagnose(run, actual)

        assertNull(failure.cause())
        assertEquals(listOf(cause), failure.suppressed())
        assertThat(failure.message()).contains("Actual: observed value", "Sequence: \"captured 1/2",
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
        val first = withOperand("first stage", Reference("Expected", root), AwaitAttempt { satisfied(it) })
        val second = withOperand("second stage", Reference("Expected", operand),
            AwaitAttempt { unsatisfied("not ready", original) })
        val run = newRun<Any, List<Any>>(captured(first, second))
        run.probe(actual)
        val failure = diagnose(run, actual)

        assertEquals(TIMEOUT, failure.reason())
        assertSame(original, failure.cause())
        assertEquals(listOf(diagnostic), failure.suppressed())
        assertThat(failure.message()).contains("Sequence: \"captured 1/2; stage 2/2\"", "Actual: observed value",
            "Current stage: second stage\n        Expected: stage operand")
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
        val second = withOperand("second stage", Reference("Expected", expected),
            AwaitAttempt { unsatisfied("not ready", original) })
        val run = newRun<Any, List<Any>>(captured(condition<Any, Any>("condition") { satisfied(it) }, second))
        run.probe(actual)
        val failure = diagnose(run, actual)

        assertSame(original, failure.cause())
        assertEquals(listOf(expectedFailure, actualFailure, causeFailure), failure.suppressed())
        assertThat(failure.message()).contains("Current stage: second stage\n        Expected: <unprintable>",
            "Mismatch: not ready", ": <unprintable>")
        assertEquals(listOf(1, 1, 1), listOf(expected.calls, actual.calls, messages))
    }

    @ParameterizedTest
    @CsvSource("1,SOURCE", "1,WAITING", "2,SOURCE", "2,WAITING")
    fun `an external failure keeps the current stage and captures`(completed: Int, operation: Operation) {
        val first = equalTo("ready")
        val last = equalTo("done")
        val definition = captured(first, last)
        val run = newRun<String, List<String>>(definition)
        listOf("ready", "done").take(completed).forEach { run.probe(it) }
        val attempt = FailedBeforeObservation<String>(operation, IllegalStateException("external failure"))

        assertEquals(2, run.historySlot(attempt))
        assertEquals(listOf(first, last), run.historyConditions(definition))
        assertEquals(listOf(Reference("Sequence", "captured $completed/2; stage 2/2"),
            Reference("Current stage", last)), run.diagnostics(attempt).fields())
    }

    @Test
    fun `a complete sequence still describes its final stage`() {
        val last = equalTo("done")
        val run = newRun<String, List<String>>(captured(equalTo("ready"), last))
        run.probe("ready")
        run.probe("done")
        assertEquals(listOf(Reference("Sequence", "captured 2/2; stage 2/2"),
            Reference("Current stage", last)), run.diagnostics(SatisfiedObservation("done")).fields())
    }

    private fun withOperand(name: String, operand: Reference<*>, check: AwaitAttempt<Any, Any>) = object : Condition<Any, Any> {
        override fun description() = name
        override fun references() = listOf(operand)
        override fun probe(actual: Any) = check.probe(actual).mapValue { it }
    }

    private fun lifecycle(pending: Condition<String, String> = status("pending")) =
        captured(status("created"), pending, status("completed"))

    private fun status(expected: String) = condition<String, String>("payment status is $expected") {
        if (it == expected) satisfied(it) else unsatisfied("payment status was $it")
    }

    private fun secondStage(second: AwaitCondition): Condition<Any, List<Any>> {
        val run = newRun<Any, List<Any>>(captured(isNotNull, second))
        run.probe("ready")
        return run
    }

    private fun <INPUT, RESULT> diagnose(run: Condition<INPUT, RESULT>, actual: INPUT) =
        failure(tryAwait(Callable { actual }).every(ofNanos(1)).upTo(ofMillis(100)).until(run))

    private class RenderingValue(val render: () -> String) {
        var calls = 0
            private set

        override fun toString(): String { calls++; return render() }
    }
}

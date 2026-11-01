package io.github.gromoff97.awium.conditions.history

import io.github.gromoff97.awium.Await.tryAwait
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.attempts
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.failure
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.observation
import io.github.gromoff97.awium.conditions.common.CommonConditions.captured
import io.github.gromoff97.awium.conditions.common.CommonConditions.condition
import io.github.gromoff97.awium.conditions.common.CommonConditions.conditionPerWait
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.isNull
import io.github.gromoff97.awium.conditions.common.CommonConditions.matches
import io.github.gromoff97.awium.result.Attempt.FailedAfterObservation
import io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation
import io.github.gromoff97.awium.result.Attempt.Operation.CONDITION
import io.github.gromoff97.awium.result.Attempt.Operation.SOURCE
import io.github.gromoff97.awium.result.Attempt.Operation.WAITING
import io.github.gromoff97.awium.result.Attempt.SatisfiedObservation
import io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation
import io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED
import io.github.gromoff97.awium.result.AwaitFailure.Reason.SOURCE_FAILED
import io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED
import io.github.gromoff97.awium.result.AttemptResult.satisfied
import io.github.gromoff97.awium.result.AttemptResult.unsatisfied
import io.github.gromoff97.awium.result.AttemptResult.Unsatisfied
import io.github.gromoff97.awium.function.AwaitAttempt
import java.io.IOException
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.newRun
import java.time.Duration.ofMillis
import java.time.Duration.ofNanos
import java.util.concurrent.Callable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CapturedHistoryTest {
    @AfterEach
    fun `clear the caller interrupt flag`() {
        Thread.interrupted()
    }

    @Test
    fun `a sequence failure identifies the pending stage and both relevant reasons`() {
        val first = equalTo("created").because("payment must be created")
        val second = equalTo("paid").because("payment must be completed")
        val result = tryAwait(Callable { "created" }).every(ofNanos(1)).upTo(ofMillis(100))
            .until(captured(first, second).because("checkout lifecycle"))

        assertThat(failure(result).message()).contains("stage 2/2", "Expected: \"paid\"",
            "Because: payment must be completed", "Because: checkout lifecycle", "Mismatch: value was not equal")
            .doesNotContain("payment must be created")
        assertSame(second, result.conditions().last().condition())
    }

    @Test
    fun `the failure message keeps rejections from completed and pending stages`() {
        val paid = condition<String, String>("paid") {
            if (it == "paid") satisfied(it) else unsatisfied("payment pending")
        }
        val shipped = condition<String, String>("shipped") { unsatisfied("tracking number missing") }
        val source = listOf("created", "paid", "paid").iterator()
        val result = tryAwait(Callable { source.next() }).every(ofNanos(1)).until(captured(paid, shipped))

        assertThat(failure(result).message()).contains("""
            Unsatisfied attempts (condition 1):
                Through #1: payment pending

            Unsatisfied attempts (condition 2):
                Through #3: tracking number missing
        """.trimIndent())
    }

    @Test
    fun `an unfinished stage keeps every observation change`() {
        val observations = listOf(4 to 0, 2 to 10, 6 to 20, 12 to 30, 6 to 40)
            .flatMap { (count, value) -> List(count) { value } } + listOf(100, 200)
        val source = observations.iterator()
        val result = tryAwait(Callable { source.next() }).every(ofNanos(1))
            .until(captured(equalTo(100), equalTo(200)))

        assertEquals(listOf(4L, 6L, 12L, 24L, 30L, 31L), result.conditions().first().attempts().keys.toList())
        assertEquals(listOf(0, 10, 20, 30, 40, 100, 200), attempts(result).map(::observation))
        assertEquals(listOf(32L), result.conditions().last().attempts().keys.toList())
    }

    @Test
    fun `reusing the same condition still creates separate stage histories`() {
        var calls = 0
        val stage = condition<Int, Int>("same description") { satisfied(it) }
        val result = tryAwait(Callable { ++calls }).every(ofNanos(1)).until(captured(stage, stage, stage))

        assertEquals(listOf(1, 2, 3), result.orThrow())
        assertEquals(listOf(1L, 2L, 3L), result.conditions().map { it.attempts().lastKey() })
        result.conditions().forEach { assertSame(stage, it.condition()) }
    }

    @Test
    fun `a failed predicate between comparisons retains its cause`() {
        val cause = IOException("callback failed")
        val result = tryAwait(Callable { "created" }).every(ofNanos(1)).until(captured(
            equalTo("created"), matches { _: String -> throw cause }, equalTo("ready")))

        assertSame(cause, failure(CONDITION_FAILED, result).cause())
        assertEquals(2, attempts(result).size)
        assertTrue(result.conditions().last().attempts().isEmpty())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `failure before the first check belongs to the first stage`(interrupted: Boolean) {
        val first = equalTo("ready")
        val cause = IllegalStateException("source unavailable")
        if (interrupted) Thread.currentThread().interrupt()
        val result = tryAwait(Callable<String> { throw cause })
            .until(captured(first, equalTo("done")).because("lifecycle"))

        assertSame(first, result.conditions().first().condition())
        assertTrue(result.conditions().last().attempts().isEmpty())
        val attempt = assertInstanceOf(FailedBeforeObservation::class.java, attempts(result).single())
        assertEquals(if (interrupted) WAITING else SOURCE, attempt.operation())
        val failure = failure(result)
        assertEquals(if (interrupted) INTERRUPTED else SOURCE_FAILED, failure.reason())
        if (interrupted) assertInstanceOf(InterruptedException::class.java, failure.cause())
        else assertSame(cause, failure.cause())
        assertThat(failure.message()).contains("Because: lifecycle", "stage 1/2")
    }

    @Test
    fun `each attempt is recorded under the stage that checked it`() {
        val source = listOf(0, 0, 10, 10, 20, 20, 30).iterator()
        val stages = listOf(equalTo(10), equalTo(20), equalTo(30))
        val result = tryAwait(Callable { source.next() }).every(ofNanos(1)).until(captured(*stages.toTypedArray()))

        assertEquals(stages, result.conditions().map { it.condition() })
        assertEquals(listOf(listOf(2L, 3L), listOf(4L, 5L), listOf(6L, 7L)),
            result.conditions().map { it.attempts().keys.toList() })
        assertEquals(List(3) { listOf(UnsatisfiedObservation::class.java, SatisfiedObservation::class.java) }.flatten(),
            attempts(result).map { it.javaClass })
        assertEquals(listOf(listOf(0, 10), listOf(10, 20), listOf(20, 30)),
            result.conditions().map { it.attempts().values.map(::observation) })
        assertEquals(listOf(10, 20, 30), result.orThrow())
    }

    @Test
    fun `an incomplete run retains unreached stages without initializing them`() {
        var initialized = 0
        val unreached = conditionPerWait<Int, Int>("third") {
            initialized++
            AwaitAttempt { satisfied(it) }
        }.because("last check")
        val source = listOf(1, 0, 0, 2, 2).iterator()
        val result = tryAwait(Callable { source.next() }).every(ofNanos(1))
            .until(captured(equalTo(1), equalTo(3), unreached))

        assertEquals(3, result.conditions().size)
        assertEquals(listOf(0, 2), result.conditions()[1].attempts().values.filterIsInstance<UnsatisfiedObservation<Int>>().map { it.observed() })
        val last = result.conditions().last()
        assertSame(unreached, last.condition())
        assertTrue(last.attempts().isEmpty())
        assertEquals(0, initialized)
        assertThat(last.toString()).contains("not attempted", "last check")
    }

    @Test
    fun `rechecking a complete run records only its final stage`() {
        val run = newRun<Int, List<Int>>(captured(equalTo(1), equalTo(2)))
        val slots = listOf(1, 2, 2, 2).map { actual ->
            run.probe(actual)
            run.historySlot(SatisfiedObservation(actual))
        }
        assertEquals(listOf(1, 2, 2, 2), slots)
    }

    @Test
    fun `the report labels each stage and its observation changes`() {
        val source = listOf("created", "pending", "pending", "paid").iterator()
        val result = tryAwait(Callable { source.next() }).every(ofNanos(1)).until(captured(
            equalTo("created"), equalTo("paid").because("payment accepted")).because("order lifecycle"))
        val report = result.toString()
        assertEquals(2, report.lineSequence().count { it.startsWith("Condition ") })
        assertThat(report).contains("Condition 1:", "Condition 2:", "Expected: \"created\"", "Expected: \"paid\"",
            "Because: payment accepted", "Through #3", "Observed now: \"pending\"")
    }

    @Test
    fun `returning to an earlier condition repeats its definition in the report`() {
        val ready = equalTo("ready")
        val source = listOf("ready", "retry", "ready").iterator()
        val result = tryAwait(Callable { source.next() }).every(ofNanos(1)).until(captured(ready, equalTo("retry"), ready))
        val lines = result.toString().lines()
        assertEquals(3, lines.count { it.startsWith("Condition ") })
        assertEquals(2, lines.count { it.contains("Expected: \"ready\"") })
    }

    @Test
    fun `a source failure is recorded in the next unfinished stage`() {
        val cause = IllegalStateException("source failed")
        var reads = 0
        val result = tryAwait(Callable<String?> { if (reads++ == 0) null else throw cause })
            .every(ofNanos(1)).until(captured(isNull, isNull, isNull))

        assertInstanceOf(SatisfiedObservation::class.java, result.conditions().first().attempts().firstEntry().value)
        val pending = result.conditions()[1]
        val attempt = assertInstanceOf(FailedBeforeObservation::class.java, pending.attempts().lastEntry().value)
        assertSame(cause, attempt.cause())
        assertEquals(SOURCE, attempt.operation())
        assertTrue(result.conditions().last().attempts().isEmpty())
        assertThat(pending.toString()).contains("Observed: unavailable")
    }

    @Test
    fun `a source that returns while interrupted belongs to the next stage`() {
        var reads = 0
        val result = tryAwait(Callable {
            if (++reads == 2) Thread.currentThread().interrupt()
            "ready"
        }).every(ofNanos(1)).until(captured(equalTo("ready"), equalTo("done"), equalTo("closed")))

        val pending = result.conditions()[1]
        val attempt = assertInstanceOf(FailedAfterObservation::class.java, pending.attempts().lastEntry().value)
        assertEquals(2L, pending.attempts().lastKey())
        assertEquals("ready", attempt.observed())
        assertEquals(SOURCE, attempt.operation())
        assertTrue(result.conditions().last().attempts().isEmpty())
        assertThat(failure(INTERRUPTED, result).message()).contains("captured 1/3; stage 2/3")
    }

    @Test
    fun `a condition failure retains the null returned by the source`() {
        val cause = IllegalStateException("check failed")
        val result = tryAwait(Callable<String?> { null }).every(ofNanos(1))
            .until(captured(isNull, condition<String?, String?>("broken") { throw cause }))

        val pending = result.conditions().last()
        assertEquals(2L, pending.attempts().lastKey())
        val attempt = assertInstanceOf(FailedAfterObservation::class.java, pending.attempts().lastEntry().value)
        assertNull(attempt.observed())
        assertSame(cause, attempt.cause())
        assertEquals(CONDITION, attempt.operation())
        assertNull(attempt.location())
        assertSame(cause, failure(result).cause())
        assertThat(pending.toString()).contains("Observed now: null")
    }
}

package io.github.gromoff97.awium.conditions.history

import io.github.gromoff97.awium.Await.tryAwait
import io.github.gromoff97.awium.conditions.ConditionAssertions.attempts
import io.github.gromoff97.awium.conditions.ConditionAssertions.failure
import io.github.gromoff97.awium.conditions.ConditionAssertions.observation
import io.github.gromoff97.awium.conditions.Conditions.captured
import io.github.gromoff97.awium.conditions.Conditions.condition
import io.github.gromoff97.awium.conditions.Conditions.equalTo
import io.github.gromoff97.awium.conditions.Conditions.isNull
import io.github.gromoff97.awium.conditions.Conditions.matches
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
import io.github.gromoff97.awium.result.ConditionResult.satisfied
import java.io.IOException
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
        val source = listOf("created", "created", "processing", "completed").iterator()
        val stages = listOf(equalTo("created"), equalTo("processing"), equalTo("completed"))
        val result = tryAwait(Callable { source.next() }).every(ofNanos(1)).until(captured(*stages.toTypedArray()))

        assertEquals(stages, result.conditions().map { it.condition() })
        assertEquals(listOf(listOf(1L), listOf(2L, 3L), listOf(4L)),
            result.conditions().map { it.attempts().keys.toList() })
        assertEquals(listOf(SatisfiedObservation::class.java, UnsatisfiedObservation::class.java,
            SatisfiedObservation::class.java, SatisfiedObservation::class.java), attempts(result).map { it.javaClass })
        assertEquals(listOf("created", "created", "processing", "completed"), attempts(result).map(::observation))
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

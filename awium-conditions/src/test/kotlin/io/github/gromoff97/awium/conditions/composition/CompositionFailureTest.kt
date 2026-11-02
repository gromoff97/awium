package io.github.gromoff97.awium.conditions.composition

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.Await.tryAwait
import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.attempts
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.failure
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.uncontrolled
import io.github.gromoff97.awium.conditions.common.CommonConditions.captured
import io.github.gromoff97.awium.conditions.common.CommonConditions.condition
import io.github.gromoff97.awium.conditions.common.CommonConditions.conditionPerWait
import io.github.gromoff97.awium.conditions.common.CommonConditions.isNotNull
import io.github.gromoff97.awium.conditions.map.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue
import io.github.gromoff97.awium.function.AwaitAttempt.bind
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.capturedRun
import io.github.gromoff97.awium.execution.ProbeFailure
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.result.Attempt.Operation.CONDITION
import io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED
import io.github.gromoff97.awium.result.AwaitResult
import io.github.gromoff97.awium.result.AttemptResult.unsatisfied
import io.github.gromoff97.awium.result.AttemptResult.Unsatisfied
import io.github.gromoff97.awium.result.AttemptResult.satisfied
import java.io.IOException
import java.time.Duration.ofNanos
import java.util.Optional
import java.util.concurrent.Callable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class CompositionFailureTest {
    @AfterEach
    fun `clear the caller interrupt flag`() { Thread.interrupted() }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `interruption in a nested factory skips its evaluator`(sequence: Boolean) {
        val events = mutableListOf<String>()
        val nested = conditionPerWait<String, String>("lazy") {
            events += "factory"
            Thread.currentThread().interrupt()
            AwaitAttempt { actual -> events += "probe"; satisfied(actual) }
        }
        val check = if (sequence) captured(nested, isNotNull) else hasValue(nested)
        val actual = if (sequence) "ready" else Optional.of("ready")
        assertInstanceOf<InterruptedException>(thrown(check, actual))
        assertEquals(listOf("factory"), events)
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @ParameterizedTest
    @MethodSource("throwingCompositions")
    fun `composition preserves callback and factory failures`(cause: Throwable, wait: () -> AwaitResult<*, *>) {
        val result = wait()
        assertSame(cause, failure(CONDITION_FAILED, result).cause())
        assertSame(cause, uncontrolled(CONDITION, attempts(result).last()).cause())
        assertFalse(Thread.currentThread().isInterrupted)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `an interrupted nested check retains the original cause and location`(throwing: Boolean) {
        val cause = IllegalStateException("not ready")
        val check = hasValue(condition<String, String>("ready") {
            Thread.currentThread().interrupt()
            if (throwing) throw cause else unsatisfied("not ready", cause)
        })
        val failure = assertThrows<ProbeFailure> { bind(check).probe(Optional.of("actual")) }
        assertSame(cause, failure.cause)
        assertEquals("optional value", failure.location())
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @ParameterizedTest
    @MethodSource("nullResults")
    fun `a missing nested result is rejected without clearing interruption`(check: AwaitCondition, actual: Any, interrupted: Boolean) {
        assertThat(thrown(check, actual)).isInstanceOf(NullPointerException::class.java)
            .hasMessage("condition returned null AttemptResult")
        assertEquals(interrupted, Thread.currentThread().isInterrupted)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `a missing captured result never completes the stage`(interrupted: Boolean) {
        var brokenResult = true
        val broken = condition<String, String>("first stage") {
            if (brokenResult) {
                if (interrupted) Thread.currentThread().interrupt()
                null
            } else satisfied(it)
        }
        val run = capturedRun<String>(captured(broken, isNotNull))
        assertThat(assertThrows<NullPointerException> { run.probe("actual") })
            .hasMessage("condition returned null AttemptResult")
        assertEquals(interrupted, Thread.currentThread().isInterrupted)
        Thread.interrupted()
        brokenResult = false
        assertInstanceOf<Unsatisfied<*>>(run.probe("first"))
        assertEquals(listOf("first", "second"), result(run.probe("second")))
    }

    companion object {
        @JvmStatic
        fun throwingCompositions() = listOf(IOException("checked"), AssertionError("assertion"),
            InterruptedException("interrupted"), Throwable("direct")).flatMap { cause ->
            listOf("callback" to condition<String, String>("callback") { throw cause },
                "factory" to conditionPerWait<String, String>("factory") { throw cause }).flatMap { (origin, check) ->
                waits(check).map { (name, wait) ->
                    argumentSet("${cause.javaClass.simpleName} / $origin / $name", cause, wait)
                }
            }
        }

        @JvmStatic
        fun nullResults() = listOf(false, true).flatMap { interrupted ->
            val check = condition<String, String>("broken") {
                if (interrupted) Thread.currentThread().interrupt()
                null
            }
            compositions(check).map { (name, composed, actual) ->
                argumentSet("$name / interrupted=$interrupted", composed, actual, interrupted)
            }
        }

        private fun compositions(check: Condition<String, String>) = listOf(
            Triple("optional", hasValue(check), Optional.of("actual")),
            Triple("collection", singleElement(check), listOf("actual")),
            Triple("map value", valueFor("key", check), mapOf("key" to "actual")),
            Triple("captured", captured(check, check), "actual")
        )

        private fun waits(check: Condition<String, String>): List<Pair<String, () -> AwaitResult<*, *>>> = listOf(
            "optional" to { tryAwait(Callable { Optional.of("actual") }).until(hasValue(check)) },
            "collection" to { tryAwait(Callable { listOf("actual") }).until(singleElement(check)) },
            "map value" to { tryAwait(Callable { mapOf("key" to "actual") }).until(valueFor("key", check)) },
            "captured" to { tryAwait(Callable { "actual" }).every(ofNanos(1)).until(captured(isNotNull, check)) }
        )
    }
}

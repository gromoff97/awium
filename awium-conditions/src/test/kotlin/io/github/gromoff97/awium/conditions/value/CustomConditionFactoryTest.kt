package io.github.gromoff97.awium.conditions.value

import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.common.CommonConditions.condition
import io.github.gromoff97.awium.conditions.common.CommonConditions.conditionPerWait
import io.github.gromoff97.awium.execution.ConditionExecution.bind
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import io.github.gromoff97.awium.result.ConditionResult.satisfied
import io.github.gromoff97.awium.result.ConditionResult.unsatisfied
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class CustomConditionFactoryTest {
    @Test
    fun `delegates once and returns the selected result`() {
        val evaluation: AwaitAttempt<String, Int> = mock()
        `when`(evaluation.probe("ready")).thenReturn(satisfied(5))
        val condition = condition("text length", evaluation)

        assertEquals(5, result(evaluate(condition, "ready")))
        verify(evaluation).probe("ready")
    }

    @Test
    fun `a direct probe returns the selected value`() {
        val check = condition("length") { value: String -> satisfied(value.length) }
        assertEquals(5, result(check.probe("ready")))
    }

    @Test
    fun `direct probes create fresh evaluator state`() {
        val check = conditionPerWait<String, Int>("call count") {
            var calls = 0
            AwaitAttempt { satisfied(++calls) }
        }
        assertEquals(listOf(1, 1), List(2) { result(check.probe("ready")) })
    }

    @Test
    fun `keeps the supplied description`() {
        val condition = condition("text length") { value: String -> satisfied(value.length) }
        assertEquals("text length", condition.description())
    }

    @Test
    fun `keeps the callback mismatch separate from its description`() {
        val condition = condition<Any, Any>("status is ready") { unsatisfied("status was pending") }
        assertEquals("status was pending", rejection(condition, "pending").mismatch())
    }

    @Test
    fun `a null selected value is a successful result`() {
        val condition = condition<Any, String?>("no label") { satisfied(null) }
        assertNull(result(evaluate(condition, Any())))
    }

    @Test
    fun `preserves thrown assertions and their stack traces`() {
        val cause = AssertionError("selection failed")
        val frames = arrayOf(StackTraceElement("PaymentSelector", "select", "PaymentSelector.java", 42))
        cause.stackTrace = frames
        var calls = 0
        val condition = condition<String, String>("checked value") { calls++; throw cause }

        assertSame(cause, thrown(condition, "42"))
        assertEquals(1, calls)
        assertArrayEquals(frames, cause.stackTrace)
    }

    @Test
    fun `the evaluator factory is lazy`() {
        var factories = 0
        val condition: Condition<Any, Any> = conditionPerWait("lazy") {
            factories++
            AwaitAttempt { satisfied(it) }
        }
        bind(condition)

        assertEquals(0, factories)
    }

    @Test
    fun `each execution gets independent evaluator state`() {
        var factories = 0
        val condition: Condition<Any, Any> = conditionPerWait("second observation") {
            factories++
            var calls = 0
            AwaitAttempt { actual -> if (++calls == 2) satisfied(actual) else unsatisfied("first observation") }
        }
        val actual = Any()

        repeat(2) {
            val execution = bind(condition)
            assertInstanceOf(Unsatisfied::class.java, execution.probe(actual))
            assertSame(actual, result(execution.probe(actual)))
        }
        assertEquals(2, factories)
    }

    @Test
    fun `factory failures escape on the first probe`() {
        val cause = IllegalStateException("factory failed")
        var calls = 0
        val condition = conditionPerWait<Any, Any>("lazy") { calls++; throw cause }

        assertEquals(0, calls)
        assertSame(cause, thrown(condition, Any()))
        assertEquals(1, calls)
    }

    @Test
    fun `rejects a factory that returns no evaluator`() {
        val failure = thrown(conditionPerWait<Any, Any>("lazy") { null }, Any())
        assertThat(failure).isInstanceOf(NullPointerException::class.java).hasMessage("evaluator must not be null")
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    fun `rejects invalid arguments immediately`(factory: Executable, type: Class<out Throwable>, argument: String) {
        val failure = assertThrows(type, factory)
        assertThat(failure.message).contains(argument)
    }

    companion object {
        @JvmStatic
        fun invalidArguments() = listOf(
            argumentSet("null description", Executable { condition<Any, Any>(null) { satisfied(it) } },
                NullPointerException::class.java, "description"),
            argumentSet("null description and callback", Executable { condition<Any, Any>(null, null) },
                NullPointerException::class.java, "description"),
            argumentSet("blank description", Executable { condition<Any, Any>(" \n ") { satisfied(it) } },
                IllegalArgumentException::class.java, "description"),
            argumentSet("null evaluation", Executable { condition<Any, Any>("payment", null) },
                NullPointerException::class.java, "evaluation"),
            argumentSet("null evaluator factory", Executable { conditionPerWait<Any, Any>("payment", null) },
                NullPointerException::class.java, "evaluator factory")
        )
    }
}

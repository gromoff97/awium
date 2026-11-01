package io.github.gromoff97.awium.conditions.value

import consumer.value.ComparableConsumer.missingLowerBound
import consumer.value.ComparableConsumer.missingUpperBound
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.Conditions.between
import io.github.gromoff97.awium.conditions.Conditions.strictlyBetween
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class RangeConditionTest {
    @ParameterizedTest
    @MethodSource("boundaries")
    fun `checks both range boundaries`(condition: PreservingCondition<Int>, actual: Int, matches: Boolean) {
        assertEquals(matches, evaluate(condition, actual) is Satisfied<*>)
    }

    @ParameterizedTest
    @MethodSource("nonUnitComparisons")
    fun `uses the sign of each bound comparison`(condition: PreservingCondition<String>) {
        assertSame("m", result(evaluate(condition, "m")))
    }

    @Test
    fun `returns the observed instance when comparison ignores scale`() {
        val actual = BigDecimal("5.00")
        val condition: PreservingCondition<BigDecimal> = between(BigDecimal("5"), BigDecimal("7"))
        assertSame(actual, result(evaluate(condition, actual)))
    }

    @Test
    fun `rejects a missing observation`() {
        assertEquals("value was null", rejection(between<Int, Int>(5, 7), null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("missingBounds")
    fun `rejects a missing bound before evaluation`(factory: Executable) {
        assertThrows(NullPointerException::class.java, factory)
    }

    @ParameterizedTest
    @MethodSource("reversedBounds")
    fun `rejects reversed bounds before evaluation`(factory: Executable) {
        assertThrows(IllegalArgumentException::class.java, factory)
    }

    companion object {
        @JvmStatic
        fun nonUnitComparisons() = listOf(
            named("inclusive", between<String, String>("a", "z")),
            named("exclusive", strictlyBetween<String, String>("a", "z"))
        )

        @JvmStatic
        fun boundaries() = listOf(
            boundary(between(5, 7), 5, true), boundary(between(5, 7), 7, true),
            boundary(between(5, 7), 4, false), boundary(between(5, 7), 8, false),
            boundary(strictlyBetween(5, 7), 6, true), boundary(strictlyBetween(5, 7), 5, false),
            boundary(strictlyBetween(5, 7), 7, false),
            boundary(between(5, 5), 5, true), boundary(strictlyBetween(5, 5), 5, false)
        )

        @JvmStatic
        fun missingBounds() = listOf(
            named("lower bound", Executable { missingLowerBound() }), named("upper bound", Executable { missingUpperBound() })
        )

        @JvmStatic
        fun reversedBounds() = listOf(
            named("inclusive", Executable { between<Int, Int>(2, 1) }), named("exclusive", Executable { strictlyBetween<Int, Int>(2, 1) })
        )

        private fun boundary(condition: PreservingCondition<Int>, actual: Int, matches: Boolean) =
            argumentSet("${condition.description()} ${condition.references()} / actual=$actual", condition, actual, matches)
    }
}

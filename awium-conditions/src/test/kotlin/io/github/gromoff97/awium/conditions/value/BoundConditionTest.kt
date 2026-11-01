package io.github.gromoff97.awium.conditions.value

import consumer.value.ComparableConsumer.missingBound
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.Conditions.atLeast
import io.github.gromoff97.awium.conditions.Conditions.atMost
import io.github.gromoff97.awium.conditions.Conditions.greaterThan
import io.github.gromoff97.awium.conditions.Conditions.lessThan
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class BoundConditionTest {
    @ParameterizedTest
    @MethodSource("boundaries")
    fun `compares the observation with the bound`(condition: PreservingCondition<Int>, actual: Int, matches: Boolean) {
        assertEquals(matches, evaluate(condition, actual) is Satisfied<*>)
    }

    @ParameterizedTest
    @MethodSource("nonUnitComparisons")
    fun `uses the sign of the comparison`(condition: PreservingCondition<String>) {
        assertSame("m", result(evaluate(condition, "m")))
    }

    @Test
    fun `returns the observed instance when comparison ignores scale`() {
        val actual = BigDecimal("5.00")
        val condition: PreservingCondition<BigDecimal> = atLeast(BigDecimal("5"))
        assertSame(actual, result(evaluate(condition, actual)))
    }

    @Test
    fun `rejects a missing observation`() {
        assertEquals("value was null", rejection(greaterThan<Int, Int>(5), null).mismatch())
    }

    @Test
    fun `rejects a missing bound before evaluation`() {
        assertThrows(NullPointerException::class.java) { missingBound() }
    }

    companion object {
        @JvmStatic
        fun nonUnitComparisons() = listOf(
            named("greater than", greaterThan<String, String>("a")),
            named("at least", atLeast<String, String>("a")),
            named("less than", lessThan<String, String>("z")),
            named("at most", atMost<String, String>("z"))
        )

        @JvmStatic
        fun boundaries() = listOf(
            boundary(greaterThan(5), 4, false), boundary(greaterThan(5), 5, false), boundary(greaterThan(5), 6, true),
            boundary(atLeast(5), 4, false), boundary(atLeast(5), 5, true), boundary(atLeast(5), 6, true),
            boundary(lessThan(5), 4, true), boundary(lessThan(5), 5, false), boundary(lessThan(5), 6, false),
            boundary(atMost(5), 4, true), boundary(atMost(5), 5, true), boundary(atMost(5), 6, false)
        )

        private fun boundary(condition: PreservingCondition<Int>, actual: Int, matches: Boolean) =
            arguments(condition, actual, matches)
    }
}

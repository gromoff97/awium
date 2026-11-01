package io.github.gromoff97.awium.conditions.value

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.AwaitCondition.Reference
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.notEqualTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.notSameAs
import io.github.gromoff97.awium.conditions.common.CommonConditions.sameAs
import io.github.gromoff97.awium.conditions.support.Directional
import io.github.gromoff97.awium.conditions.support.ThrowingEquals
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class EqualityConditionTest {
    @Test
    fun `retains the expected operand for diagnostics`() {
        assertEquals(listOf(Reference("Expected", "expected")), equalTo("expected").references())
    }

    @ParameterizedTest
    @MethodSource("successfulComparisons")
    fun `returns the original observation`(condition: AwaitCondition, actual: Any?) {
        assertSame(actual, result(evaluate(condition, actual)))
    }

    @ParameterizedTest
    @MethodSource("failedComparisons")
    fun `rejects an observation that fails the comparison`(condition: AwaitCondition, actual: Any?) {
        rejection(condition, actual)
    }

    @ParameterizedTest
    @MethodSource("arrayComparisons")
    fun `compares array contents`(actual: Any, expected: Any, matches: Boolean) {
        assertEquals(matches, equal(actual, expected))
    }

    @Test
    fun `calls equals on the observation`() {
        val actual = Directional(true)
        assertTrue(equal(actual, Directional(false)))
        assertEquals(1, actual.equalsCalls)
    }

    @Test
    fun `calls equals on observed array elements`() {
        val actual = Directional(true)
        assertTrue(equal(arrayOf<Any>(intArrayOf(1, 2), arrayOf(actual)),
            arrayOf<Any>(intArrayOf(1, 2), arrayOf(Directional(false)))))
        assertEquals(1, actual.equalsCalls)
    }

    @Test
    fun `stops at the first unequal array element`() {
        val unreachable = ThrowingEquals { throw AssertionError("comparison continued") }
        assertFalse(equal(arrayOf("different", unreachable), arrayOf("expected", Any())))
    }

    @Test
    fun `reports a comparison failure for distinct cyclic arrays`() {
        val actual = cycle()
        val expected = cycle()
        assertInstanceOf(StackOverflowError::class.java, thrown(equalTo(expected), actual))
    }

    @Test
    fun `accepts the same cyclic array without traversing it`() {
        val actual = cycle()
        assertTrue(equal(actual, actual))
    }

    @Test
    fun `rejects unequal cyclic arrays before entering the cycle`() {
        assertFalse(equal(cycle(), cycle("different")))
    }

    @Test
    fun `compares deeply nested arrays`() {
        var actual: Any = "leaf"
        var expected: Any = "leaf"
        repeat(64) {
            actual = arrayOf(actual)
            expected = arrayOf(expected)
        }
        assertTrue(equal(actual, expected))
    }

    private fun equal(actual: Any?, expected: Any?) = evaluate(equalTo(expected), actual) is Satisfied<*>

    private fun cycle(second: String = "second"): Array<Any?> {
        val first = arrayOf<Any?>("first", null)
        first[1] = arrayOf(second, first)
        return first
    }

    companion object {
        @JvmStatic
        fun successfulComparisons(): List<Arguments> {
            val expected = String(charArrayOf('1'))
            val actual = String(charArrayOf('1'))
            return listOf(
                argumentSet("equal values", equalTo(expected), actual),
                argumentSet("unequal values", notEqualTo(expected), "2"),
                argumentSet("same instance", sameAs(actual), actual),
                argumentSet("different instances", notSameAs(expected), actual),
                argumentSet("equal nulls", equalTo(null), null),
                argumentSet("same null", sameAs(null), null),
                argumentSet("value differs from null", notEqualTo(null), actual),
                argumentSet("null differs from value", notEqualTo(actual), null)
            )
        }

        @JvmStatic
        fun failedComparisons(): List<Arguments> {
            val expected = String(charArrayOf('1'))
            val actual = String(charArrayOf('1'))
            return listOf(
                argumentSet("notEqualTo with equal values", notEqualTo(expected), actual),
                argumentSet("equalTo with different values", equalTo(expected), "2"),
                argumentSet("sameAs with equal distinct instances", sameAs(expected), actual),
                argumentSet("notSameAs with the same instance", notSameAs(actual), actual),
                argumentSet("null compared to a value", equalTo(actual), null),
                argumentSet("value compared to null", equalTo(null), actual)
            )
        }

        @JvmStatic
        fun arrayComparisons() = listOf(
            argumentSet("equal primitive arrays", intArrayOf(1, 2), intArrayOf(1, 2), true),
            argumentSet("different primitive arrays", intArrayOf(1), intArrayOf(2), false),
            argumentSet("different primitive types", intArrayOf(1, 2), longArrayOf(1, 2), false),
            argumentSet("object and primitive arrays", arrayOf(1), intArrayOf(1), false),
            argumentSet("array and scalar", arrayOf(1), 1, false),
            argumentSet("nested array mismatch", arrayOf(intArrayOf(1, 2)), arrayOf(intArrayOf(1, 3)), false),
            argumentSet("different array lengths", arrayOf(1), arrayOf(1, 2), false)
        )
    }
}

package io.github.gromoff97.awium.conditions.value

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.AwaitCondition.Reference
import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.Conditions.equalTo
import io.github.gromoff97.awium.conditions.Conditions.notEqualTo
import io.github.gromoff97.awium.conditions.Conditions.notSameAs
import io.github.gromoff97.awium.conditions.Conditions.sameAs
import io.github.gromoff97.awium.conditions.support.Directional
import io.github.gromoff97.awium.conditions.support.ThrowingEquals
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class EqualityConditionTest {
    @Test
    fun `retains the expected operand for diagnostics`() {
        assertEquals(listOf(Reference("Expected", "expected")), equalTo("expected").references())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("successfulComparisons")
    fun `returns the original observation`(condition: AwaitCondition, actual: Any?) {
        assertSame(actual, result(evaluate(condition, actual)))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failedComparisons")
    fun `rejects an observation that fails the comparison`(condition: AwaitCondition, actual: Any?) {
        rejection(condition, actual)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("arrayComparisons")
    fun `compares array contents`(label: String, actual: Any, expected: Any, matches: Boolean) {
        assertEquals(matches, equal(actual, expected), label)
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
                arguments(named("equal values", equalTo(expected)), actual),
                arguments(named("unequal values", notEqualTo(expected)), "2"),
                arguments(named("same instance", sameAs(actual)), actual),
                arguments(named("different instances", notSameAs(expected)), actual),
                arguments(named("equal nulls", equalTo(null)), null),
                arguments(named("same null", sameAs(null)), null),
                arguments(named("value differs from null", notEqualTo(null)), actual),
                arguments(named("null differs from value", notEqualTo(actual)), null)
            )
        }

        @JvmStatic
        fun failedComparisons(): List<Arguments> {
            val expected = String(charArrayOf('1'))
            val actual = String(charArrayOf('1'))
            return listOf(
                arguments(named("notEqualTo with equal values", notEqualTo(expected)), actual),
                arguments(named("equalTo with different values", equalTo(expected)), "2"),
                arguments(named("sameAs with equal distinct instances", sameAs(expected)), actual),
                arguments(named("notSameAs with the same instance", notSameAs(actual)), actual),
                arguments(named("null compared to a value", equalTo(actual)), null),
                arguments(named("value compared to null", equalTo(null)), actual)
            )
        }

        @JvmStatic
        fun arrayComparisons() = listOf(
            arguments("equal primitive arrays", intArrayOf(1, 2), intArrayOf(1, 2), true),
            arguments("different primitive arrays", intArrayOf(1), intArrayOf(2), false),
            arguments("different primitive types", intArrayOf(1, 2), longArrayOf(1, 2), false),
            arguments("object and primitive arrays", arrayOf(1), intArrayOf(1), false),
            arguments("array and scalar", arrayOf(1), 1, false),
            arguments("nested array mismatch", arrayOf(intArrayOf(1, 2)), arrayOf(intArrayOf(1, 3)), false),
            arguments("different array lengths", arrayOf(1), arrayOf(1, 2), false)
        )
    }
}

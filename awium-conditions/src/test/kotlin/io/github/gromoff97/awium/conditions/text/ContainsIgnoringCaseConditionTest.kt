package io.github.gromoff97.awium.conditions.text

import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.StringConditions.containsIgnoringCase
import io.github.gromoff97.awium.conditions.support.Characters
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class ContainsIgnoringCaseConditionTest {
    @ParameterizedTest
    @MethodSource("examples")
    fun `matches literal text with Unicode case folding`(expected: String, actual: String, matches: Boolean) {
        assertEquals(matches, evaluate(containsIgnoringCase(expected), actual) is Satisfied<*>)
    }

    @Test
    fun `matches characters without rendering the observation`() {
        val actual = Characters("Ready 42")
        assertSame(actual, result(evaluate(containsIgnoringCase("READY"), actual)))
    }

    @Test
    fun `freezes mutable expected text`() {
        val expected = StringBuilder("ready")
        val condition = containsIgnoringCase(expected)
        expected.replace(0, expected.length, "changed")

        val actual = StringBuilder("READY")
        assertSame(actual, result(evaluate(condition, actual)))
    }

    @Test
    fun `rejects null`() {
        assertEquals("string was null", rejection(containsIgnoringCase("ready"), null).mismatch())
    }

    companion object {
        @JvmStatic
        fun examples() = listOf(
            arguments("READY", "ready", true), arguments("READY", "failed", false),
            arguments("ς", "Σ", true),
            arguments("a+b[.]", "xA+B[.]y", true), arguments("a+b[.]", "aaabxy", false),
            arguments("\uD800", "\uD800\uDC00", false), arguments("\uD800", "\uD800", true)
        )
    }
}

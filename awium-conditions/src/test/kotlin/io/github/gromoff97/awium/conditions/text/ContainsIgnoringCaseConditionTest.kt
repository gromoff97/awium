package io.github.gromoff97.awium.conditions.text

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.preservesInstance
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.string.StringConditions.containsIgnoringCase
import io.github.gromoff97.awium.conditions.support.Characters
import io.github.gromoff97.awium.result.AttemptResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class ContainsIgnoringCaseConditionTest {
    @ParameterizedTest
    @MethodSource("examples")
    fun `finds literal text without case sensitivity`(expected: String, actual: String, matches: Boolean) {
        assertEquals(matches, evaluate(containsIgnoringCase(expected), actual) is Satisfied<*>)
    }

    @Test
    fun `matches characters without rendering the observation`() {
        val actual = Characters("Ready 42")
        preservesInstance(containsIgnoringCase("READY"), actual)
    }

    @Test
    fun `freezes mutable expected text`() {
        val expected = StringBuilder("ready")
        val condition = containsIgnoringCase(expected)
        expected.replace(0, expected.length, "changed")

        val actual = StringBuilder("READY")
        preservesInstance(condition, actual)
    }

    @Test
    fun `rejects null`() {
        assertEquals("string was null", rejection(containsIgnoringCase("ready"), null).mismatch())
    }

    companion object {
        @JvmStatic
        fun examples() = listOf(
            argumentSet("different case", "READY", "ready", true),
            argumentSet("different text", "READY", "failed", false),
            argumentSet("empty text", "", "", true),
            argumentSet("empty fragment", "", "ready", true),
            argumentSet("final sigma", "ς", "Σ", true),
            argumentSet("supplementary letter", "\uD801\uDC00", "x\uD801\uDC28y", true),
            argumentSet("literal regex characters", "a+b[.]", "xA+B[.]y", true),
            argumentSet("no regex interpretation", "a+b[.]", "aaabxy", false),
            argumentSet("surrogate pair stays whole", "\uD800", "\uD800\uDC00", false),
            argumentSet("lone surrogate", "\uD800", "\uD800", true)
        )
    }
}

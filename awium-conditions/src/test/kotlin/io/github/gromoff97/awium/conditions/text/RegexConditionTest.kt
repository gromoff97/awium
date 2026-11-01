package io.github.gromoff97.awium.conditions.text

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.StringConditions.doesNotMatchRegex
import io.github.gromoff97.awium.conditions.StringConditions.matchesRegex
import io.github.gromoff97.awium.conditions.support.Characters
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import java.util.regex.Pattern
import java.util.regex.Pattern.CASE_INSENSITIVE
import java.util.regex.Pattern.compile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class RegexConditionTest {
    @Test
    fun `rejects a pattern whose toString returns null`() {
        val pattern: CharSequence = mock()
        `when`(pattern.toString()).thenReturn(null)
        assertThrows(NullPointerException::class.java) { matchesRegex(pattern) }
    }

    @ParameterizedTest
    @MethodSource("examples")
    fun `matches the whole character sequence`(condition: PreservingCondition<CharSequence>, matches: Boolean) {
        val actual = Characters("Ready 42")
        val outcome = evaluate(condition, actual)
        assertEquals(matches, outcome is Satisfied<*>)
        if (matches) assertSame(actual, result(outcome))
    }

    @Test
    fun `freezes a mutable pattern`() {
        val pattern = StringBuilder("ready")
        val condition = matchesRegex(pattern)
        pattern.replace(0, pattern.length, "changed")

        val actual = StringBuilder("ready")
        assertSame(actual, result(evaluate(condition, actual)))
    }

    @Test
    fun `rejects null`() {
        assertEquals("string was null", rejection(matchesRegex("ready"), null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("missingPatterns")
    fun `rejects a missing pattern before evaluation`(factory: Executable) {
        assertThrows(NullPointerException::class.java, factory)
    }

    companion object {
        @JvmStatic
        fun examples() = listOf(
            argumentSet("whole text matches", matchesRegex("Ready \\d+"), true),
            argumentSet("pattern flags apply", matchesRegex(compile("ready \\d+", CASE_INSENSITIVE)), true),
            argumentSet("matching prefix is insufficient", matchesRegex("Ready"), false),
            argumentSet("text does not match", matchesRegex("failed.*"), false),
            argumentSet("text avoids forbidden pattern", doesNotMatchRegex("failed.*"), true),
            argumentSet("text avoids compiled pattern", doesNotMatchRegex(compile("failed.*")), true),
            argumentSet("forbidden pattern matches", doesNotMatchRegex("Ready \\d+"), false)
        )

        @JvmStatic
        fun missingPatterns() = listOf(
            argumentSet("required compiled pattern", Executable { matchesRegex(null as Pattern?) }),
            argumentSet("forbidden compiled pattern", Executable { doesNotMatchRegex(null as Pattern?) }),
            argumentSet("required text pattern", Executable { matchesRegex(null as CharSequence?) }),
            argumentSet("forbidden text pattern", Executable { doesNotMatchRegex(null as CharSequence?) })
        )
    }
}

package io.github.gromoff97.awium.conditions.text

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.StringConditions
import io.github.gromoff97.awium.conditions.support.Characters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class TextMatchConditionTest {
    @ParameterizedTest
    @MethodSource("examples")
    fun `matches a snapshot of the expected text`(example: Example) {
        val expected = StringBuilder("ready")
        val condition = example.factory(expected)
        expected.replace(0, expected.length, "changed")
        val actual = StringBuilder(example.matching)

        assertSame(actual, result(evaluate(condition, actual)))
    }

    @ParameterizedTest
    @MethodSource("examples")
    fun `rejects text that does not meet the condition`(example: Example) {
        rejection(example.factory("ready"), example.mismatch)
    }

    @ParameterizedTest
    @MethodSource("examples")
    fun `rejects missing expected text immediately`(example: Example) {
        assertThrows(NullPointerException::class.java) { example.factory(null) }
    }

    @ParameterizedTest
    @MethodSource("examples")
    fun `rejects expected text whose toString returns null`(example: Example) {
        val expected: CharSequence = mock()
        `when`(expected.toString()).thenReturn(null)

        val failure = assertThrows(NullPointerException::class.java) { example.factory(expected) }
        assertEquals("text.toString() must not return null", failure.message)
    }

    @Test
    fun `contentEquals reads characters without rendering the observation`() {
        val actual = Characters("ready")
        assertSame(actual, result(evaluate(StringConditions.contentEquals("ready"), actual)))
    }

    @Test
    fun `contentEquals accepts empty text`() {
        val actual = StringBuilder()
        assertSame(actual, result(evaluate(StringConditions.contentEquals(""), actual)))
    }

    @Test
    fun `rejects a null observation`() {
        assertEquals("string was null", rejection(StringConditions.contentEquals("ready"), null).mismatch())
    }

    data class Example(
        val factory: (CharSequence?) -> PreservingCondition<CharSequence>,
        val matching: String,
        val mismatch: String
    )

    companion object {
        @JvmStatic
        fun examples() = listOf(
            named("contains", Example(StringConditions::contains, "already now", "READY now")),
            named("doesNotContain", Example(StringConditions::doesNotContain, "READY now", "already now")),
            named("startsWith", Example(StringConditions::startsWith, "ready now", "not ready")),
            named("doesNotStartWith", Example(StringConditions::doesNotStartWith, "not ready", "ready now")),
            named("endsWith", Example(StringConditions::endsWith, "already", "ready now")),
            named("doesNotEndWith", Example(StringConditions::doesNotEndWith, "ready now", "already")),
            named("contentEquals", Example(StringConditions::contentEquals, "ready", "READY")),
            named("equalToIgnoringCase", Example(StringConditions::equalToIgnoringCase, "READY", "failed")),
            named("notEqualToIgnoringCase", Example(StringConditions::notEqualToIgnoringCase, "failed", "READY"))
        )
    }
}

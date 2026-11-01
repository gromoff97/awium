package io.github.gromoff97.awium.conditions.text

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.StringConditions.blank
import io.github.gromoff97.awium.conditions.StringConditions.nonBlank
import io.github.gromoff97.awium.conditions.support.Characters
import java.util.stream.IntStream.range
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class BlankConditionTest {
    @ParameterizedTest
    @MethodSource("examples")
    fun `recognizes whitespace without rendering text`(value: String, isBlank: Boolean) {
        val actual = Characters(value)
        val matching = if (isBlank) blank else nonBlank
        val mismatching = if (isBlank) nonBlank else blank

        assertSame(actual, result(evaluate(matching, actual)))
        assertNull(rejection(mismatching, actual).cause())
    }

    @Test
    fun `reads a parallel code point stream on the calling thread`() {
        val caller = Thread.currentThread()
        val actual: CharSequence = mock()
        `when`(actual.codePoints()).thenAnswer {
            range(0, 4096).parallel().map {
                assertSame(caller, Thread.currentThread())
                ' '.code
            }
        }

        assertSame(actual, result(evaluate(blank, actual)))
    }

    @Test
    fun `rejects null`() {
        assertEquals("string was null", rejection(blank, null).mismatch())
    }

    companion object {
        @JvmStatic
        fun examples() = listOf(
            argumentSet("empty text", "", true), argumentSet("whitespace", " \t\n\u2000", true),
            argumentSet("non-breaking space", "\u00a0", false), argumentSet("figure space", "\u2007", false),
            argumentSet("narrow non-breaking space", "\u202f", false), argumentSet("emoji", "\uD83D\uDE00", false),
            argumentSet("lone surrogate", "\uD800", false), argumentSet("letters", "ready", false)
        )
    }
}

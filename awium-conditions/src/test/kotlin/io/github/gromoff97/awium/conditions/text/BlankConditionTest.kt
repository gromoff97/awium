package io.github.gromoff97.awium.conditions.text

import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.StringConditions.blank
import io.github.gromoff97.awium.conditions.StringConditions.nonBlank
import io.github.gromoff97.awium.conditions.support.Characters
import java.util.stream.IntStream.range
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
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
            arguments("", true), arguments(" \t\n\u2000", true),
            arguments("\u00a0", false), arguments("\u2007", false), arguments("\u202f", false),
            arguments("\uD83D\uDE00", false), arguments("\uD800", false), arguments("ready", false)
        )
    }
}

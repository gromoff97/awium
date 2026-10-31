package io.github.gromoff97.awium.conditions.text

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.StringConditions.containsAll
import io.github.gromoff97.awium.conditions.StringConditions.doesNotContainAny
import java.nio.CharBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock

class TextSetConditionTest {
    @ParameterizedTest
    @MethodSource("matchingInputs")
    fun `returns text that meets the fragment requirement`(condition: PreservingCondition<CharSequence>, actual: String) {
        assertSame(actual, result(evaluate(condition, actual)))
    }

    @ParameterizedTest
    @MethodSource("mismatchingInputs")
    fun `rejects text that fails the fragment requirement`(condition: PreservingCondition<CharSequence>, actual: String) {
        rejection(condition, actual)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `freezes the expected list and its mutable fragments`(negated: Boolean) {
        val prefix = StringBuilder("rea")
        val suffix = charArrayOf('d', 'y')
        val expected = mutableListOf<CharSequence>(prefix, CharBuffer.wrap(suffix))
        val condition = if (negated) doesNotContainAny(expected) else containsAll(expected)
        prefix.replace(0, prefix.length, "changed")
        suffix[0] = 'x'
        expected[1] = "changed"

        val actual = if (negated) "failed" else "ready"
        assertSame(actual, result(evaluate(condition, actual)))
        assertEquals(listOf("rea", "dy"), condition.references().first().value())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `an empty fragment set does not read the observation`(negated: Boolean) {
        val actual = mock(CharSequence::class.java) { throw AssertionError("text must not be read") }
        val condition = if (negated) doesNotContainAny(emptyList()) else containsAll(emptyList())
        assertSame(actual, result(evaluate(condition, actual)))
    }

    @Test
    fun `an empty fragment set still rejects null`() {
        assertEquals("string was null", rejection(containsAll(emptyList()), null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("invalidExpectations")
    fun `rejects invalid fragment sets immediately`(factory: Executable) {
        assertThrows(NullPointerException::class.java, factory)
    }

    companion object {
        @JvmStatic
        fun matchingInputs() = listOf(
            arguments(containsAll(listOf("a", "b")), "abc"),
            arguments(doesNotContainAny(listOf("x", "y")), "abc"),
            arguments(containsAll(emptyList()), ""), arguments(containsAll(emptyList()), "ready"),
            arguments(doesNotContainAny(emptyList()), ""), arguments(doesNotContainAny(emptyList()), "ready")
        )

        @JvmStatic
        fun mismatchingInputs() = listOf(
            arguments(containsAll(listOf("a", "b")), "ac"),
            arguments(doesNotContainAny(listOf("x", "y")), "ayc"),
            arguments(doesNotContainAny(listOf("")), ""), arguments(doesNotContainAny(listOf("")), "ready")
        )

        @JvmStatic
        fun invalidExpectations() = listOf(
            Executable { containsAll(null) }, Executable { doesNotContainAny(null) },
            Executable { containsAll(listOf("ready", null)) }
        )
    }
}

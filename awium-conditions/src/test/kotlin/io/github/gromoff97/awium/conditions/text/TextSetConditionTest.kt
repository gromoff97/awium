package io.github.gromoff97.awium.conditions.text

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.preservesInstance
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.string.StringConditions.containsAll
import io.github.gromoff97.awium.conditions.string.StringConditions.doesNotContainAny
import java.nio.CharBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock

class TextSetConditionTest {
    @ParameterizedTest
    @MethodSource("matchingInputs")
    fun `returns text that meets the fragment requirement`(condition: PreservingCondition<CharSequence>, actual: String) {
        preservesInstance(condition, actual)
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

        val actual = if (negated) "changed xy" else "ready"
        preservesInstance(condition, actual)
        assertEquals(listOf("rea", "dy"), condition.references().first().value())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `an empty fragment set does not read the observation`(negated: Boolean) {
        val actual = mock(CharSequence::class.java) { throw AssertionError("text must not be read") }
        val condition = if (negated) doesNotContainAny(emptyList()) else containsAll(emptyList())
        preservesInstance(condition, actual)
    }

    @Test
    fun `an empty fragment set still rejects null`() {
        assertEquals("string was null", rejection(containsAll(emptyList()), null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("invalidExpectations")
    fun `rejects invalid fragment sets immediately`(factory: Executable) {
        assertThrows<NullPointerException>(factory::execute)
    }

    companion object {
        @JvmStatic
        fun matchingInputs() = listOf(
            argumentSet("all fragments present", containsAll(listOf("a", "b")), "abc"),
            argumentSet("all forbidden fragments absent", doesNotContainAny(listOf("x", "y")), "abc"),
            argumentSet("no requirements / empty text", containsAll(emptyList()), ""),
            argumentSet("no requirements / nonempty text", containsAll(emptyList()), "ready"),
            argumentSet("no exclusions / empty text", doesNotContainAny(emptyList()), ""),
            argumentSet("no exclusions / nonempty text", doesNotContainAny(emptyList()), "ready")
        )

        @JvmStatic
        fun mismatchingInputs() = listOf(
            argumentSet("required fragment missing", containsAll(listOf("a", "b")), "ac"),
            argumentSet("forbidden fragment present", doesNotContainAny(listOf("x", "y")), "ayc"),
            argumentSet("empty fragment forbidden / empty text", doesNotContainAny(listOf("")), ""),
            argumentSet("empty fragment forbidden / nonempty text", doesNotContainAny(listOf("")), "ready")
        )

        @JvmStatic
        fun invalidExpectations() = listOf(
            argumentSet("missing required fragments", Executable { containsAll(null) }),
            argumentSet("missing forbidden fragments", Executable { doesNotContainAny(null) }),
            argumentSet("null fragment", Executable { containsAll(listOf("ready", null)) })
        )
    }
}

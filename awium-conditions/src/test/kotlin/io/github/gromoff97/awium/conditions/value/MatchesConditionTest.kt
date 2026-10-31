package io.github.gromoff97.awium.conditions.value

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.Conditions.matches
import io.github.gromoff97.awium.function.CheckedPredicate
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MatchesConditionTest {
    @Test
    fun `checks the observation once and returns it`() {
        val actual = Any()
        val predicate: CheckedPredicate<Any> = mock()
        `when`(predicate.test(actual)).thenReturn(true)

        assertSame(actual, result(evaluate(matches(predicate), actual)))
        verify(predicate).test(actual)
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["ready"])
    fun `the predicate decides whether null or any other value matches`(actual: String?) {
        assertSame(actual, result(evaluate(matches<String?> { it === actual }, actual)))
    }

    @Test
    fun `false means the value did not match`() {
        assertEquals("value did not match", rejection(matches<Any> { false }, Any()).mismatch())
    }

    @ParameterizedTest
    @MethodSource("failures")
    fun `predicate failures escape unchanged`(cause: Throwable) {
        var calls = 0
        assertSame(cause, thrown(matches<Any> { calls++; throw cause }, Any()))
        assertEquals(1, calls)
    }

    @Test
    fun `rejects a missing predicate immediately`() {
        val failure = assertThrows(NullPointerException::class.java) { matches<Any>(null) }
        assertEquals("predicate must not be null", failure.message)
    }

    companion object {
        @JvmStatic
        fun failures() = listOf(IOException("offline"), AssertionError("broken assertion"))
    }
}

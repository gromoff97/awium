package io.github.gromoff97.awium.function.checked

import io.github.gromoff97.awium.function.checked.CheckedPredicate.not
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class CheckedPredicateTest {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `negation calls the predicate once with the original arguments`(answer: Boolean) {
        val predicate: CheckedPredicate<Any> = mock()
        val actual = Any()
        `when`(predicate.test(actual)).thenReturn(answer)

        assertEquals(!answer, not(predicate).test(actual))
        verify(predicate).test(actual)
    }

    @Test
    fun `negation passes null arguments to the predicate`() {
        val predicate: CheckedPredicate<Any> = mock()
        not(predicate).test(null)
        verify(predicate).test(null)
    }

    @Test
    fun `negation rejects a missing predicate`() {
        assertThrows(NullPointerException::class.java) { not<Any>(null) }
    }

    @ParameterizedTest
    @MethodSource("failures")
    fun `negation propagates the original failure`(cause: Throwable) {
        val predicate = not { _: Any -> throw cause }
        assertSame(cause, assertThrows(Throwable::class.java) { predicate.test("value") })
    }

    companion object {
        @JvmStatic
        fun failures() = listOf(IOException(), AssertionError(), Throwable(), InterruptedException())
    }
}

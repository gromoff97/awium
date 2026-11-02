package io.github.gromoff97.awium.function.checked

import io.github.gromoff97.awium.function.checked.CheckedBiPredicate.not
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class CheckedBiPredicateTest {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `negation calls the predicate once with the original arguments`(answer: Boolean) {
        val predicate: CheckedBiPredicate<Any, Any> = mock()
        val first = Any()
        val second = Any()
        `when`(predicate.test(first, second)).thenReturn(answer)

        assertEquals(!answer, not(predicate).test(first, second))
        verify(predicate).test(first, second)
    }

    @Test
    fun `negation passes null arguments to the predicate`() {
        val predicate: CheckedBiPredicate<Any, Any> = mock()
        not(predicate).test(null, null)
        verify(predicate).test(null, null)
    }

    @Test
    fun `negation rejects a missing predicate`() {
        assertThrows<NullPointerException> { not<Any, Any>(null) }
    }

    @ParameterizedTest
    @MethodSource("failures")
    fun `negation propagates the original failure`(cause: Throwable) {
        val predicate = not { _: Any, _: Any -> throw cause }
        assertSame(cause, assertThrows<Throwable> { predicate.test("first", "second") })
    }

    companion object {
        @JvmStatic
        fun failures() = listOf(IOException(), AssertionError(), Throwable(), InterruptedException())
    }
}

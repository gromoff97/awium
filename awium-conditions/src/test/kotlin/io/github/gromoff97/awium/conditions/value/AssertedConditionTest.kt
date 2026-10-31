package io.github.gromoff97.awium.conditions.value

import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.Conditions.asserted
import io.github.gromoff97.awium.function.CheckedConsumer
import io.github.gromoff97.awium.result.ConditionResult.unsatisfied
import java.io.IOException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class AssertedConditionTest {
    @AfterEach
    fun clearInterruptFlag() { Thread.interrupted() }

    @Test
    fun `passes the observation to the assertion once and returns it`() {
        val assertion: CheckedConsumer<Any> = mock()
        val actual = Any()

        assertSame(actual, result(evaluate(asserted(assertion), actual)))
        verify(assertion).accept(actual)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `an assertion failure becomes a mismatch without clearing interruption`(interrupt: Boolean) {
        val cause = AssertionError("payment is not ready")
        val condition = asserted<String> {
            if (interrupt) Thread.currentThread().interrupt()
            throw cause
        }

        assertEquals(unsatisfied<String>("value did not satisfy assertion", cause), condition.newAttempt().probe("pending"))
        assertEquals(interrupt, Thread.interrupted())
    }

    @Test
    fun `a checked exception escapes the assertion adapter`() {
        val cause = IOException("offline")
        assertSame(cause, thrown(asserted<String> { throw cause }, "pending"))
    }

    @Test
    fun `a non-assertion error escapes the assertion adapter`() {
        val cause = LinkageError("missing class")
        assertSame(cause, thrown(asserted<String> { throw cause }, "pending"))
    }

    @Test
    fun `rejects a missing assertion immediately`() {
        val failure = assertThrows(NullPointerException::class.java) { asserted<String>(null) }
        assertEquals("assertion must not be null", failure.message)
    }
}

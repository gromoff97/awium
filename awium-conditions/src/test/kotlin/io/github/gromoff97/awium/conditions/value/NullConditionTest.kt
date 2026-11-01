package io.github.gromoff97.awium.conditions.value

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.common.Conditions.isNotNull
import io.github.gromoff97.awium.conditions.common.Conditions.isNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class NullConditionTest {
    @Test
    fun `isNull accepts null`() {
        assertNull(result(evaluate(isNull, null)))
    }

    @Test
    fun `isNull rejects a value`() {
        assertEquals("value was not null", rejection(isNull, Any()).mismatch())
    }

    @Test
    fun `isNotNull returns the original value`() {
        val actual = Any()
        assertSame(actual, result(evaluate(isNotNull, actual)))
    }

    @Test
    fun `isNotNull rejects null`() {
        assertEquals("value was null", rejection(isNotNull, null).mismatch())
    }
}

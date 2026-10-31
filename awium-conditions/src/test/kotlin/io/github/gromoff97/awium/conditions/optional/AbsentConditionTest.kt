package io.github.gromoff97.awium.conditions.optional

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.OptionalConditions.absent
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class AbsentConditionTest {
    @Test
    fun `returns the empty optional`() {
        val actual = Optional.empty<String>()
        assertSame(actual, result(evaluate(absent, actual)))
    }

    @Test
    fun `rejects a present optional`() {
        assertEquals("optional was present", rejection(absent, Optional.of("value")).mismatch())
    }

    @Test
    fun `rejects null`() {
        assertEquals("optional was null", rejection(absent, null).mismatch())
    }
}

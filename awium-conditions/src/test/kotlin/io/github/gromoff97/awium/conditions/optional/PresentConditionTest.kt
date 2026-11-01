package io.github.gromoff97.awium.conditions.optional

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.present
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PresentConditionTest {
    @Test
    fun `returns the contained value`() {
        val actual = Any()
        assertSame(actual, result(evaluate(present, Optional.of(actual))))
    }

    @Test
    fun `rejects an empty optional`() {
        assertEquals("optional was empty", rejection(present, Optional.empty<Any>()).mismatch())
    }

    @Test
    fun `distinguishes null from an empty optional`() {
        assertEquals("optional was null", rejection(present, null).mismatch())
    }

    @Test
    fun `describes presence`() {
        assertEquals("optional is present", present.description())
    }
}

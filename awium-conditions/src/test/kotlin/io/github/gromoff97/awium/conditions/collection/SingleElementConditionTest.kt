package io.github.gromoff97.awium.conditions.collection

import io.github.gromoff97.awium.conditions.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class SingleElementConditionTest {
    @Test
    fun `returns the only element`() {
        val actual = Any()
        assertSame(actual, result(evaluate(singleElement, listOf(actual))))
    }

    @Test
    fun `returns a null element`() {
        assertNull(result(evaluate(singleElement, listOf(null))))
    }

    @ParameterizedTest
    @MethodSource("invalidCollections")
    fun `reports why no single element exists`(actual: Collection<*>?, expected: String) {
        assertEquals(expected, rejection(singleElement, actual).mismatch())
    }

    companion object {
        @JvmStatic
        fun invalidCollections() = listOf(
            arguments(null, "collection was null"),
            arguments(emptyList<Any>(), "collection size was 0"),
            arguments(listOf("first", "second"), "collection size was greater than 1")
        )
    }
}

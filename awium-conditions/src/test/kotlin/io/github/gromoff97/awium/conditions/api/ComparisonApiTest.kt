package io.github.gromoff97.awium.conditions.api

import consumer.value.ComparisonConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class ComparisonApiTest {
    @ParameterizedTest
    @MethodSource("comparedValues")
    fun `narrower comparisons preserve the observed number type and identity`(invoke: (Number) -> List<Number>, number: Int, count: Int) {
        val actual: Number = number
        val values = invoke(actual)
        assertEquals(count, values.size)
        values.forEach { assertSame(actual, it) }
    }

    @Test
    fun `map entry selection keeps both key and value types`() {
        val actual: Number = 1
        ComparisonConsumer.mapEntries(actual).forEach {
            assertEquals("key", it.key)
            assertSame(actual, it.value)
        }
    }

    companion object {
        @JvmStatic
        fun comparedValues() = listOf(
            argumentSet("value", ComparisonConsumer::values, 1000, 48),
            argumentSet("optional", ComparisonConsumer::optionalValues, 1, 8),
            argumentSet("collection", ComparisonConsumer::collectionElements, 1, 11),
            argumentSet("map value", ComparisonConsumer::mapValues, 1, 6)
        )
    }
}

package io.github.gromoff97.awium.conditions.map

import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.MapConditions.singleEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class SingleEntryConditionTest {
    @Test
    fun `returns the only entry`() {
        val actual = mapOf("key" to 42)
        assertEquals(actual.entries.single(), result(evaluate(singleEntry, actual)))
    }

    @ParameterizedTest
    @MethodSource("invalidMaps")
    fun `reports why no single entry exists`(actual: Map<*, *>?, expected: String) {
        assertEquals(expected, rejection(singleEntry, actual).mismatch())
    }

    companion object {
        @JvmStatic
        fun invalidMaps() = listOf(
            arguments(null, "map was null"),
            arguments(emptyMap<Any, Any>(), "map size was 0"),
            arguments(mapOf("first" to 1, "second" to 2), "map size was greater than 1")
        )
    }
}

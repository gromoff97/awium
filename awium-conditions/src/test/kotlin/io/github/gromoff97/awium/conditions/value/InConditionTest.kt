package io.github.gromoff97.awium.conditions.value

import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.preservesInstance
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.common.CommonConditions.`in`
import io.github.gromoff97.awium.conditions.common.CommonConditions.notIn
import io.github.gromoff97.awium.conditions.support.Directional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class InConditionTest {
    @ParameterizedTest
    @MethodSource("values")
    fun `membership compares complete values including null and arrays`(actual: Any?, expected: Any?) {
        preservesInstance(`in`(listOf(expected)), actual)
        assertEquals("value was in the unexpected values", rejection(notIn(listOf(expected)), actual).mismatch())
    }

    @Test
    fun `missing candidates fail inclusion and satisfy exclusion`() {
        val actual = "ready"
        val candidates = listOf("pending", "failed")
        assertEquals("value was not in the expected values", rejection(`in`(candidates), actual).mismatch())
        preservesInstance(notIn(candidates), actual)
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["ready"])
    fun `an empty candidate set excludes every value`(actual: String?) {
        rejection(`in`(emptyList<String>()), actual)
        preservesInstance(notIn(emptyList<String>()), actual)
    }

    @ParameterizedTest
    @MethodSource("factories")
    fun `rejects a missing candidate set immediately`(create: (Iterable<Any?>?) -> ComparisonCondition<Any?>) {
        assertThrows<NullPointerException> { create(null) }
    }

    @Test
    fun `equality is invoked on the observation`() {
        val actual = Directional(true)
        val candidate = Directional(false)

        preservesInstance(`in`(listOf(candidate)), actual)
        assertEquals(1, actual.equalsCalls)
        assertEquals(0, candidate.equalsCalls)
    }

    companion object {
        @JvmStatic
        fun values() = listOf(
            argumentSet("null", null, null),
            argumentSet("text", String(charArrayOf('a')), "a"),
            argumentSet("list", listOf("a"), listOf("a")),
            argumentSet("map", mapOf("a" to 1), mapOf("a" to 1)),
            argumentSet("int array", intArrayOf(1, 2), intArrayOf(1, 2)),
            argumentSet("object array", arrayOf("a", null), arrayOf("a", null)),
            argumentSet("nested array", arrayOf<Any>(intArrayOf(1)), arrayOf<Any>(intArrayOf(1)))
        )

        @JvmStatic
        fun factories(): List<Named<(Iterable<Any?>?) -> ComparisonCondition<Any?>>> = listOf(
            named("in", { `in`(it) }), named("not in", { notIn(it) })
        )
    }
}

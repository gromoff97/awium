package io.github.gromoff97.awium.conditions.size

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.CollectionConditions
import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.MapConditions
import io.github.gromoff97.awium.conditions.ProbeContainers.sizeOnlyCollection
import io.github.gromoff97.awium.conditions.ProbeContainers.sizeOnlyMap
import io.github.gromoff97.awium.conditions.StringConditions
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class EmptyConditionTest {
    @ParameterizedTest
    @MethodSource("textLengths")
    fun `text emptiness reads only length`(condition: AwaitCondition, length: Int, matches: Boolean) {
        val actual = object : CharSequence {
            override val length = length
            override fun get(index: Int): Char = throw AssertionError("characters are not needed")
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = throw AssertionError("slice is not needed")
            override fun toString(): String = throw AssertionError("rendering is not needed")
        }
        assertEquals(matches, evaluate(condition, actual) is Satisfied<*>)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matchingInputs")
    fun `returns the observation`(condition: AwaitCondition, actual: Any) {
        assertSame(actual, result(evaluate(condition, actual)))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mismatchingInputs")
    fun `reports why the observation failed`(condition: AwaitCondition, actual: Any?, expected: String) {
        assertEquals(expected, rejection(condition, actual).mismatch())
    }

    @Test
    fun `checks collection emptiness without traversing elements`() {
        val actual = sizeOnlyCollection<Any>(1)
        assertEquals("collection was not empty", rejection(CollectionConditions.empty, actual).mismatch())
        verify(actual).size
    }

    @Test
    fun `checks map emptiness without traversing entries`() {
        val actual = sizeOnlyMap<Any, Any>()
        assertEquals("map was not empty", rejection(MapConditions.empty, actual).mismatch())
        verify(actual).size
    }

    @Test
    fun `propagates a collection failure`() {
        val actual = sizeOnlyCollection<Any>(0)
        val cause = IllegalStateException("collection size failed")
        `when`(actual.size).thenThrow(cause)
        assertSame(cause, thrown(CollectionConditions.nonEmpty, actual))
    }

    @Test
    fun `propagates a map failure`() {
        val actual = sizeOnlyMap<Any, Any>()
        val cause = IllegalStateException("map size failed")
        `when`(actual.size).thenThrow(cause)
        assertSame(cause, thrown(MapConditions.nonEmpty, actual))
    }

    companion object {
        @JvmStatic
        fun textLengths() = listOf(
            arguments(StringConditions.empty, 0, true), arguments(StringConditions.empty, 3, false),
            arguments(StringConditions.nonEmpty, 3, true), arguments(StringConditions.nonEmpty, 0, false)
        )

        @JvmStatic
        fun matchingInputs() = listOf(
            arguments(named("empty collection", CollectionConditions.empty), emptyList<Any>()),
            arguments(named("nonempty collection", CollectionConditions.nonEmpty), listOf("value")),
            arguments(named("empty map", MapConditions.empty), emptyMap<Any, Any>()),
            arguments(named("nonempty map", MapConditions.nonEmpty), mapOf("key" to "value")),
            arguments(named("empty text", StringConditions.empty), ""),
            arguments(named("nonempty text", StringConditions.nonEmpty), "value")
        )

        @JvmStatic
        fun mismatchingInputs() = listOf(
            arguments(CollectionConditions.empty, listOf("value"), "collection was not empty"),
            arguments(CollectionConditions.nonEmpty, emptyList<Any>(), "collection was empty"),
            arguments(MapConditions.empty, mapOf("key" to "value"), "map was not empty"),
            arguments(MapConditions.nonEmpty, emptyMap<Any, Any>(), "map was empty"),
            arguments(StringConditions.empty, "value", "string was not empty"),
            arguments(StringConditions.nonEmpty, "", "string was empty"),
            arguments(CollectionConditions.empty, null, "collection was null"),
            arguments(CollectionConditions.nonEmpty, null, "collection was null"),
            arguments(MapConditions.empty, null, "map was null"),
            arguments(MapConditions.nonEmpty, null, "map was null"),
            arguments(StringConditions.empty, null, "string was null"),
            arguments(StringConditions.nonEmpty, null, "string was null")
        )
    }
}

package io.github.gromoff97.awium.conditions.size

import io.github.gromoff97.awium.dsl.AwaitCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.preservesInstance
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.map.MapConditions
import io.github.gromoff97.awium.conditions.support.ProbeContainers.sizeOnlyCollection
import io.github.gromoff97.awium.conditions.support.ProbeContainers.sizeOnlyMap
import io.github.gromoff97.awium.conditions.string.StringConditions
import io.github.gromoff97.awium.result.AttemptResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
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

    @ParameterizedTest
    @MethodSource("matchingInputs")
    fun `returns the observation`(condition: AwaitCondition, actual: Any) {
        preservesInstance(condition, actual)
    }

    @ParameterizedTest
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
            argumentSet("empty / length 0", StringConditions.empty, 0, true),
            argumentSet("empty / length 3", StringConditions.empty, 3, false),
            argumentSet("nonempty / length 3", StringConditions.nonEmpty, 3, true),
            argumentSet("nonempty / length 0", StringConditions.nonEmpty, 0, false)
        )

        @JvmStatic
        fun matchingInputs() = listOf(
            argumentSet("empty collection", CollectionConditions.empty, emptyList<Any>()),
            argumentSet("nonempty collection", CollectionConditions.nonEmpty, listOf("value")),
            argumentSet("empty map", MapConditions.empty, emptyMap<Any, Any>()),
            argumentSet("nonempty map", MapConditions.nonEmpty, mapOf("key" to "value")),
            argumentSet("empty text", StringConditions.empty, ""),
            argumentSet("nonempty text", StringConditions.nonEmpty, "value")
        )

        @JvmStatic
        fun mismatchingInputs() = listOf(
            argumentSet("empty collection", CollectionConditions.empty, listOf("value"), "collection was not empty"),
            argumentSet("nonempty collection", CollectionConditions.nonEmpty, emptyList<Any>(), "collection was empty"),
            argumentSet("empty map", MapConditions.empty, mapOf("key" to "value"), "map was not empty"),
            argumentSet("nonempty map", MapConditions.nonEmpty, emptyMap<Any, Any>(), "map was empty"),
            argumentSet("empty text", StringConditions.empty, "value", "string was not empty"),
            argumentSet("nonempty text", StringConditions.nonEmpty, "", "string was empty"),
            argumentSet("empty collection / null", CollectionConditions.empty, null, "collection was null"),
            argumentSet("nonempty collection / null", CollectionConditions.nonEmpty, null, "collection was null"),
            argumentSet("empty map / null", MapConditions.empty, null, "map was null"),
            argumentSet("nonempty map / null", MapConditions.nonEmpty, null, "map was null"),
            argumentSet("empty text / null", StringConditions.empty, null, "string was null"),
            argumentSet("nonempty text / null", StringConditions.nonEmpty, null, "string was null")
        )
    }
}

package io.github.gromoff97.awium.conditions.size

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.map.MapConditions
import io.github.gromoff97.awium.conditions.support.ProbeContainers.sizeOnlyCollection
import io.github.gromoff97.awium.conditions.support.ProbeContainers.sizeOnlyMap
import io.github.gromoff97.awium.conditions.string.StringConditions
import io.github.gromoff97.awium.result.AttemptResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class HasSizeConditionTest {
    @Test
    fun `a mismatch keeps the size observed during its probe`() {
        val values = mutableListOf<Int>()
        val check = CollectionConditions.hasSize(4)
        val sizes = (1..3).map { value ->
            values += value
            rejection(check, values).diagnostic().fields().single().value()
        }
        values += 99
        assertEquals(listOf(1, 2, 3), sizes)
    }

    @Test
    fun `describes the length unit and bound`() {
        val check = StringConditions.hasLength(5)
        assertEquals("string length: value equals expected", check.description())
        assertEquals(listOf(5), check.references().map { it.value() })
    }

    @ParameterizedTest
    @MethodSource("boundaries")
    fun `checks the size boundary`(condition: AwaitCondition, actual: Any, expected: Boolean) {
        val outcome = evaluate(condition, actual)
        assertEquals(expected, outcome is Satisfied<*>)
        if (expected) assertSame(actual, result(outcome))
    }

    @ParameterizedTest
    @MethodSource("diagnostics")
    fun `reports the observed size`(condition: AwaitCondition, actual: Any, label: String, expected: Int) {
        val field = rejection(condition, actual).diagnostic().fields().single()
        assertEquals(label, field.label())
        assertEquals(expected, field.value())
    }

    @ParameterizedTest
    @MethodSource("nullInputs")
    fun `rejects a missing observation`(condition: AwaitCondition, expected: String) {
        assertEquals(expected, rejection(condition, null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("invalidBounds")
    fun `rejects invalid bounds before evaluation`(factory: Executable, expected: String) {
        assertEquals(expected, assertThrows(IllegalArgumentException::class.java, factory).message)
    }

    @Test
    fun `reads collection size once without traversing elements`() {
        val actual = sizeOnlyCollection<Any>(2)
        result(evaluate(CollectionConditions.hasSize(2), actual))
        verify(actual).size
    }

    @Test
    fun `reads map size once without traversing entries`() {
        val actual = sizeOnlyMap<Any, Any>()
        result(evaluate(MapConditions.hasSize(1), actual))
        verify(actual).size
    }

    @Test
    fun `propagates a failure reading collection size`() {
        val actual = sizeOnlyCollection<Any>(0)
        val cause = IllegalStateException("size failed")
        `when`(actual.size).thenThrow(cause)
        assertSame(cause, thrown(CollectionConditions.hasSize(1), actual))
    }

    @Test
    fun `propagates a failure reading map size`() {
        val actual = sizeOnlyMap<Any, Any>()
        val cause = IllegalStateException("size failed")
        `when`(actual.size).thenThrow(cause)
        assertSame(cause, thrown(MapConditions.hasSize(1), actual))
    }

    companion object {
        @JvmStatic
        fun boundaries() = listOf(
            boundary(CollectionConditions.hasSize(2), listOf(1, 2), listOf(1)),
            boundary(CollectionConditions.hasSizeNotEqualTo(2), listOf(1), listOf(1, 2)),
            boundary(CollectionConditions.hasSizeGreaterThan(2), listOf(1, 2, 3), listOf(1, 2)),
            boundary(CollectionConditions.hasSizeAtLeast(2), listOf(1, 2), listOf(1)),
            boundary(CollectionConditions.hasSizeLessThan(2), listOf(1), listOf(1, 2)),
            boundary(CollectionConditions.hasSizeAtMost(2), listOf(1, 2), listOf(1, 2, 3)),
            boundary(CollectionConditions.hasSizeBetween(2, 4), listOf(1, 2), listOf(1)),
            boundary(CollectionConditions.hasSizeBetween(2, 4), listOf(1, 2, 3, 4), listOf(1, 2, 3, 4, 5)),
            boundary(CollectionConditions.hasSizeBetween(0, 0), emptyList<Int>(), listOf(1)),
            boundary(MapConditions.hasSize(2), map(2), map(1)),
            boundary(MapConditions.hasSizeNotEqualTo(2), map(1), map(2)),
            boundary(MapConditions.hasSizeGreaterThan(2), map(3), map(2)),
            boundary(MapConditions.hasSizeAtLeast(2), map(2), map(1)),
            boundary(MapConditions.hasSizeLessThan(2), map(1), map(2)),
            boundary(MapConditions.hasSizeAtMost(2), map(2), map(3)),
            boundary(MapConditions.hasSizeBetween(2, 4), map(2), map(1)),
            boundary(MapConditions.hasSizeBetween(2, 4), map(4), map(5)),
            boundary(MapConditions.hasSizeBetween(0, 0), map(0), map(1)),
            boundary(StringConditions.hasLength(5), "ready", "read"),
            boundary(StringConditions.hasLengthNotEqualTo(4), "ready", "read"),
            boundary(StringConditions.hasLengthGreaterThan(4), "ready", "read"),
            boundary(StringConditions.hasLengthAtLeast(5), "ready", "read"),
            boundary(StringConditions.hasLengthLessThan(6), "ready", "failed"),
            boundary(StringConditions.hasLengthAtMost(5), "ready", "failed"),
            boundary(StringConditions.hasLengthBetween(4, 6), "read", "hey"),
            boundary(StringConditions.hasLengthBetween(4, 6), "failed", "failure"),
            boundary(StringConditions.hasLength(0), "", "x"),
            boundary(StringConditions.hasLengthAtMost(0), "", "x")
        ).flatten() + listOf(
            argumentSet("collection equality / above bound", CollectionConditions.hasSize(2), listOf(1, 2, 3), false),
            argumentSet("collection inequality / above bound", CollectionConditions.hasSizeNotEqualTo(2), listOf(1, 2, 3), true),
            argumentSet("map equality / above bound", MapConditions.hasSize(2), map(3), false),
            argumentSet("map inequality / above bound", MapConditions.hasSizeNotEqualTo(2), map(3), true),
            argumentSet("text equality / above bound", StringConditions.hasLength(5), "longer", false),
            argumentSet("text inequality / below bound", StringConditions.hasLengthNotEqualTo(4), "hey", true)
        )

        @JvmStatic
        fun diagnostics() = listOf(
            argumentSet("collection", CollectionConditions.hasSize(2), listOf("value"), "Actual size", 1),
            argumentSet("map", MapConditions.hasSize(2), mapOf("key" to "value"), "Actual size", 1),
            argumentSet("text", StringConditions.hasLength(5), "read", "Actual length", 4)
        )

        @JvmStatic
        fun nullInputs() = listOf(
            argumentSet("collection", CollectionConditions.hasSize(2), "collection was null"),
            argumentSet("map", MapConditions.hasSize(2), "map was null"),
            argumentSet("text", StringConditions.hasLength(2), "string was null")
        )

        @JvmStatic
        fun invalidBounds() = listOf(
            invalid("collection negative size", "size must be non-negative") { CollectionConditions.hasSize(-1) },
            invalid("map negative size", "size must be non-negative") { MapConditions.hasSize(-1) },
            invalid("text negative length", "length must be non-negative") { StringConditions.hasLength(-1) },
            invalidSizeRange("negative collection range") { CollectionConditions.hasSizeBetween(-1, 1) },
            invalidSizeRange("reversed collection range") { CollectionConditions.hasSizeBetween(2, 1) },
            invalidSizeRange("negative map range") { MapConditions.hasSizeBetween(-1, 1) },
            invalidSizeRange("reversed map range") { MapConditions.hasSizeBetween(2, 1) },
            invalid("negative text range", "length range must be non-negative and ordered") {
                StringConditions.hasLengthBetween(-1, 1)
            },
            invalid("reversed text range", "length range must be non-negative and ordered") {
                StringConditions.hasLengthBetween(2, 1)
            }
        )

        private fun boundary(condition: AwaitCondition, matching: Any, mismatching: Any) = listOf(
            argumentSet("${condition.description()} / observed $matching", condition, matching, true),
            argumentSet("${condition.description()} / observed $mismatching", condition, mismatching, false)
        )

        private fun invalidSizeRange(name: String, factory: Executable) =
            invalid(name, "size range must be non-negative and ordered", factory)

        private fun invalid(name: String, message: String, factory: Executable) = argumentSet(name, factory, message)

        private fun map(size: Int) = (0 until size).associateWith { it }
    }
}

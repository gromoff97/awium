package io.github.gromoff97.awium.conditions.collection

import io.github.gromoff97.awium.conditions.CollectionConditions.hasNoDuplicates
import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.Directional
import io.github.gromoff97.awium.conditions.support.smallLists
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class HasNoDuplicatesConditionTest {
    @Test
    fun `uniqueness includes null and empty collections`() {
        for (actual in smallLists()) {
            assertEquals(actual.distinct().size == actual.size, evaluate(hasNoDuplicates, actual) is Satisfied<*>, "actual=$actual")
        }
    }

    @Test
    fun `returns the original collection`() {
        val actual = listOf("a", "b", null)
        assertSame(actual, result(evaluate(hasNoDuplicates, actual)))
    }

    @Test
    fun `a null collection is a mismatch`() {
        assertEquals("collection was null", rejection(hasNoDuplicates, null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("duplicates")
    fun `identifies the first repeated element`(actual: List<Any?>, index: Int) {
        assertEquals("duplicate collection element at index $index", rejection(hasNoDuplicates, actual).mismatch())
    }

    @Test
    fun `compares earlier observations without hashing`() {
        val first = Directional(true)
        val second = Directional(false)
        rejection(hasNoDuplicates, listOf(first, second))
        assertEquals(1, first.equalsCalls)
        assertEquals(0, second.equalsCalls)
    }

    companion object {
        @JvmStatic
        fun duplicates() = listOf(
            argumentSet("text", listOf("a", "b", "a", "b"), 2),
            argumentSet("null", listOf(null, "a", null), 2),
            argumentSet("array", listOf(intArrayOf(1), intArrayOf(1)), 1)
        )
    }
}

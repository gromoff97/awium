package io.github.gromoff97.awium.conditions.collection

import io.github.gromoff97.awium.conditions.CollectionConditions.sorted
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import java.util.Comparator.naturalOrder
import java.util.Comparator.nullsFirst
import java.util.Comparator.nullsLast
import java.util.Comparator.reverseOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class SortedConditionTest {
    @ParameterizedTest
    @MethodSource("orderedValues")
    fun `uses the supplied order and permits equal neighbours`(actual: List<Int?>, comparator: Comparator<Int?>) {
        assertSame(actual, result(evaluate(sorted(comparator), actual)))
    }

    @ParameterizedTest
    @MethodSource("inversions")
    fun `compares consecutive pairs and stops at the first inversion`(actual: List<Int>, lastIndex: Int) {
        val comparisons = mutableListOf<Pair<Int, Int>>()
        val condition = sorted<Int> { first, second -> comparisons.add(first to second); first.compareTo(second) }

        assertEquals("collection was not sorted at indices ${lastIndex - 1} and $lastIndex", rejection(condition, actual).mismatch())
        assertEquals(actual.zipWithNext().take(lastIndex), comparisons)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun `zero or one element needs no comparison`(size: Int) {
        val comparator: Comparator<Int> = mock()
        val actual = List(size) { 1 }
        assertSame(actual, result(evaluate(sorted(comparator), actual)))
        verifyNoInteractions(comparator)
    }

    @Test
    fun `a null collection is a mismatch`() {
        assertEquals("collection was null", rejection(sorted(naturalOrder<Int>()), null).mismatch())
    }

    @Test
    fun `comparator failures escape unchanged`() {
        val cause = IllegalStateException("comparison failed")
        assertSame(cause, thrown(sorted<Int> { _, _ -> throw cause }, listOf(1, 2)))
    }

    @Test
    fun `rejects a missing comparator immediately`() {
        val cause = assertThrows(NullPointerException::class.java) { sorted<Int>(null) }
        assertEquals("comparator must not be null", cause.message)
    }

    companion object {
        @JvmStatic
        fun inversions() = listOf(
            argumentSet("first pair", listOf(3, 1, 2), 1),
            argumentSet("later pair", listOf(1, 3, 2, 4), 2)
        )

        @JvmStatic
        fun orderedValues() = listOf(
            argumentSet("ascending", listOf(1, 2, 3), naturalOrder<Int>()),
            argumentSet("descending", listOf(3, 2, 1), reverseOrder<Int>()),
            argumentSet("nulls first", listOf(null, 1, 2), nullsFirst(naturalOrder<Int>())),
            argumentSet("nulls last", listOf(1, 2, null), nullsLast(naturalOrder<Int>())),
            argumentSet("equal neighbours", listOf(1, 1), naturalOrder<Int>())
        )
    }
}

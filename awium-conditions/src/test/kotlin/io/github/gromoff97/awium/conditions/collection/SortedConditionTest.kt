package io.github.gromoff97.awium.conditions.collection

import io.github.gromoff97.awium.conditions.CollectionConditions.sorted
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import java.util.Comparator.naturalOrder
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
    @Test
    fun `later mutations do not change the reported out of order pairs`() {
        var comparisons = 0
        val check = sorted<Int> { left, right -> comparisons++; left.compareTo(right) }
        val values = arrayListOf(3, 1, 2)
        val mismatches = mutableListOf(rejection(check, null).mismatch(), rejection(check, values).mismatch())
        values[0] = 1
        values[1] = 3
        mismatches += rejection(check, values).mismatch()
        values[1] = 2
        values[2] = 3

        assertSame(values, result(evaluate(check, values)))
        assertEquals(5, comparisons)
        assertEquals(listOf("collection was null", "collection was not sorted at indices 0 and 1",
            "collection was not sorted at indices 1 and 2"), mismatches)
    }

    @ParameterizedTest
    @MethodSource("orderedValues")
    fun `uses the supplied order and permits equal neighbours`(actual: List<Int>, comparator: Comparator<Int>) {
        assertSame(actual, result(evaluate(sorted(comparator), actual)))
    }

    @Test
    fun `compares consecutive pairs and stops at the first inversion`() {
        val comparisons = mutableListOf<Pair<Int, Int>>()
        val condition = sorted<Int> { first, second -> comparisons.add(first to second); first.compareTo(second) }

        assertEquals("collection was not sorted at indices 1 and 2", rejection(condition, listOf(1, 3, 2, 4)).mismatch())
        assertEquals(listOf(1 to 3, 3 to 2), comparisons)
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
        fun orderedValues() = listOf(
            argumentSet("ascending", listOf(1, 2, 3), naturalOrder<Int>()),
            argumentSet("descending", listOf(3, 2, 1), reverseOrder<Int>()),
            argumentSet("equal neighbours", listOf(1, 1), naturalOrder<Int>())
        )
    }
}

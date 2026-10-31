package io.github.gromoff97.awium.conditions.api

import consumer.collection.ContentConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CollectionContentApiTest {
    @Test
    fun `a list argument is one complete element`() {
        val actual = listOf(listOf("a", "b"))
        assertSame(actual, ContentConsumer.nestedLists(actual, listOf("a", "b")))
    }

    @Test
    fun `an array argument is one complete element`() {
        val actual = listOf(arrayOf("a", "b"))
        assertSame(actual, ContentConsumer.arrays(actual, arrayOf("a", "b")))
    }

    @Test
    fun `containsOnly keeps the array element type`() {
        val actual = listOf(arrayOf("a", "b"))
        assertSame(actual, ContentConsumer.onlyArrays(actual, arrayOf("a", "b")))
    }

    @Test
    fun `iterable expectations retain primitive array elements after until`() {
        assertEquals(2, ContentConsumer.iterableArrayElements(intArrayOf(1, 2)))
    }

    @Test
    fun `iterable expectations retain nested list elements after until`() {
        assertEquals(5, ContentConsumer.iterableListElements(listOf("ready")))
    }

    @Test
    fun `list exclusion keeps the nested element type`() {
        val actual = listOf(listOf("a"))
        assertSame(actual, ContentConsumer.excludedLists(actual, listOf("b")))
    }

    @Test
    fun `array exclusion keeps the element type`() {
        val actual = listOf(arrayOf("a"))
        assertSame(actual, ContentConsumer.excludedArrays(actual, arrayOf("b")))
    }

    @Test
    fun `narrower expected elements preserve the source collection type`() {
        val actual = arrayListOf<Number>(1, 2)
        assertSame(actual, ContentConsumer.narrowerExpectedElements(actual))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `empty expectations preserve the concrete collection type in both terminals`(empty: Boolean) {
        val actual = if (empty) arrayListOf() else arrayListOf("a")
        ContentConsumer.emptyExpectations(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `subsets retain the concrete source through selection capture and both terminals`() {
        val actual = arrayListOf("ready")
        ContentConsumer.subsets(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `subset comparisons accept narrower expected types`() {
        val actual = listOf<CharSequence>("ready")
        assertSame(actual, ContentConsumer.narrowerSubset(actual))
    }

    @Test
    fun `ordered checks preserve a stored collection source type`() {
        val actual = arrayListOf(1, 2, 3, 5)
        ContentConsumer.orderedChecks(actual).forEach { assertSame(actual, it) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `empty ordered patterns preserve concrete collections in both terminals`(empty: Boolean) {
        val actual = if (empty) arrayListOf() else arrayListOf("a", "b")
        ContentConsumer.emptyOrderedPatterns(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `ordered patterns retain array element types`() {
        val actual = listOf(arrayOf("a", "b"))
        ContentConsumer.arrayPatterns(actual).forEach { assertSame(actual, it) }
    }
}

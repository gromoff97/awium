package io.github.gromoff97.awium.conditions.api

import consumer.map.SelectionConsumer
import consumer.map.SelectionConsumer.ReceiptEntry
import consumer.map.SelectionConsumer.User
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapSelectionApiTest {
    @Test
    fun `entry comparison preserves the original entry`() {
        val entry = ReceiptEntry("receipt", BigDecimal("42.00"))
        val expected = ReceiptEntry("receipt", BigDecimal("42.00"))
        assertSame(entry, SelectionConsumer.comparedEntry(EntryMap(listOf(entry)), expected))
    }

    @Test
    fun `instanceOf narrows the entry type`() {
        val entry = ReceiptEntry("receipt", BigDecimal("42.00"))
        assertSame(entry, SelectionConsumer.narrowedEntry(EntryMap(listOf(entry))))
    }

    @Test
    fun `tryAwait retains the narrowed entry type`() {
        val entry = ReceiptEntry("receipt", BigDecimal("42.00"))
        assertSame(entry, SelectionConsumer.recordedNarrowedEntry(EntryMap(listOf(entry))))
    }

    @Test
    fun `entryFor preserves types after because`() {
        val entry = ReceiptEntry("receipt", BigDecimal("42.00"))
        assertSame(entry, SelectionConsumer.explainedEntry(EntryMap(listOf(entry))))
    }

    @Test
    fun `instanceOf narrows the selected value`() {
        val value = BigDecimal("42.00")
        assertSame(value, SelectionConsumer.narrowedValue(mapOf("receipt" to value)))
    }

    @Test
    fun `entry and value selectors infer Integer without a target type`() {
        assertEquals(listOf(2, 2, 2, 2), SelectionConsumer.selectedValues(mapOf("second" to 2)))
    }

    @Test
    fun `nested entry lambdas infer the key type`() {
        assertEquals("second", SelectionConsumer.selectedKey(mapOf("second" to 2)))
    }

    @Test
    fun `until returns a present null value`() {
        assertNull(SelectionConsumer.nullableValue(mapOf(arrayOf("nullable") to null)))
    }

    @Test
    fun `tryAwait returns a present null value`() {
        assertNull(SelectionConsumer.recordedNullableValue(mapOf(arrayOf("nullable") to null)))
    }

    @Test
    fun `entry predicates preserve concrete maps in both terminals`() {
        val actual = linkedMapOf("user" to User(true))
        SelectionConsumer.entryPredicates(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `single entry predicates preserve entries in both terminals`() {
        val actual = linkedMapOf("user" to User(true))
        SelectionConsumer.singleEntryPredicates(actual).forEach { assertSame(actual.entries.single(), it) }
    }

    @Test
    fun `captured entry predicates keep typed source observations`() {
        val actual = linkedMapOf("user" to User(true))
        val observations = SelectionConsumer.capturedEntryPredicates(actual)
        assertEquals(listOf(actual, actual), observations)
        observations.forEach { assertSame(actual, it) }
    }

    @Test
    fun `captured entry selectors keep typed source observations`() {
        val actual = linkedMapOf("user" to User(true))
        val observations = SelectionConsumer.capturedSingleEntries(actual)
        assertEquals(listOf(actual, actual), observations)
        observations.forEach { assertSame(actual, it) }
    }

    @Test
    fun `typed entry predicates may replace map values`() {
        val actual = linkedMapOf("user" to User(false))
        val replacement = User(true)
        assertSame(actual.entries.single(), SelectionConsumer.replacedValue(actual, replacement))
        assertSame(replacement, actual["user"])
    }

    @Test
    fun `recorded entry replacement retains the selected entry`() {
        val actual = linkedMapOf("user" to User(false))
        val replacement = User(true)
        val result = SelectionConsumer.recordedReplacement(actual, replacement)
        assertSame(actual.entries.single(), result.orThrow())
        assertSame(replacement, result.orThrow().value)
    }

    @Test
    fun `paired predicates accept covariant maps after because`() {
        assertTrue(SelectionConsumer.covariantSingleEntry(mapOf("user" to User(true))))
    }

    @Test
    fun `entry quantifiers retain covariant maps`() {
        val actual = mapOf("user" to User(true))
        assertSame(actual, SelectionConsumer.covariantAllEntries(actual))
    }

    @Test
    fun `maps implementing Iterable keep map selection types`() {
        assertEquals(listOf(10, 10, 10, 10, 10), SelectionConsumer.mapWithDifferentIterableElements())
    }
    @Test
    fun `a typed null key keeps the selected value type`() {
        assertEquals(5, SelectionConsumer.nullableKeyLength())
    }

}

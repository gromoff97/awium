package io.github.gromoff97.awium.conditions.api

import consumer.map.ContentConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class MapContentApiTest {
    @Test
    fun `array operands remain whole keys and values`() {
        val actual = mapOf(arrayOf("a", "b") to arrayOf("x", "y"))
        ContentConsumer.arrayOperands(actual, arrayOf("a", "b"), arrayOf("x", "y")).forEach { assertSame(actual, it) }
    }

    @Test
    fun `empty array operands remain whole keys and values`() {
        val actual = mapOf(emptyArray<String>() to emptyArray<String>())
        ContentConsumer.arrayOperands(actual, emptyArray(), emptyArray()).forEach { assertSame(actual, it) }
    }

    @Test
    fun `null array operands remain legal keys and values`() {
        val actual = mapOf<Array<String>?, Array<String>?>(null to null)
        ContentConsumer.arrayOperands(actual, null, null).forEach { assertSame(actual, it) }
    }

    @Test
    fun `entry overloads preserve the concrete map in both terminals`() {
        val actual = linkedMapOf("pending" to "0", "ready" to "1")
        ContentConsumer.entryChecks(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `captured entry checks retain typed source observations`() {
        val actual = linkedMapOf("ready" to "1", "alsoReady" to "2")
        val observations = ContentConsumer.capturedEntries(actual)
        assertEquals(listOf(actual, actual), observations)
        observations.forEach { assertSame(actual, it) }
    }

    @ParameterizedTest
    @MethodSource("noFailedEntry")
    fun `entry absence overloads preserve the concrete map`(actual: LinkedHashMap<String, String>) {
        ContentConsumer.absenceChecks(actual).forEach { assertSame(actual, it) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `empty expectations preserve the concrete map in both terminals`(empty: Boolean) {
        val actual = if (empty) linkedMapOf() else linkedMapOf("key" to "value")
        ContentConsumer.emptyExpectations(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `map quantifiers and bulk checks preserve the concrete map`() {
        val actual = linkedMapOf("a" to 1, "b" to 2)
        ContentConsumer.quantifiersAndBulkChecks(actual).forEach { assertSame(actual, it) }
    }

    companion object {
        @JvmStatic
        fun noFailedEntry() = listOf(
            argumentSet("empty", linkedMapOf<String, String>()),
            argumentSet("other key", linkedMapOf("ready" to "1")),
            argumentSet("other value", linkedMapOf("failed" to "0")))
    }
}

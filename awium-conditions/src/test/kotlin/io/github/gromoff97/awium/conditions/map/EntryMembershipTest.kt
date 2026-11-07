package io.github.gromoff97.awium.conditions.map

import io.github.gromoff97.awium.dsl.AwaitCondition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.preservesInstance
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.map.MapConditions.hasEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.hasNoEntry
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.conditions.support.ProbeContainers.ProbeEntry
import io.github.gromoff97.awium.conditions.support.Directional
import io.github.gromoff97.awium.conditions.support.ThrowingEquals
import io.github.gromoff97.awium.result.AttemptResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class EntryMembershipTest {
    @ParameterizedTest
    @MethodSource("entries")
    fun `requires both the key and its value`(actual: Map<String?, String?>, found: Boolean) {
        assertEquals(found, evaluate(hasEntry("ready", "1"), actual) is Satisfied<*>)
    }

    @ParameterizedTest
    @MethodSource("entries")
    fun `absence accepts a missing key or a different value`(actual: Map<String?, String?>, found: Boolean) {
        assertEquals(!found, evaluate(hasNoEntry("ready", "1"), actual) is Satisfied<*>)
    }

    @ParameterizedTest
    @MethodSource("factories")
    fun `a null map is a mismatch`(factory: (Any?, Any?) -> AwaitCondition) {
        assertEquals("map was null", rejection(factory(null, null), null).mismatch())
    }

    @Test
    fun `returns the observed map`() {
        val actual = linkedMapOf("ready" to "1")
        preservesInstance(hasEntry("ready", "1"), actual)
    }

    @Test
    fun `null keys and values are ordinary entry components`() {
        val actual = EntryMap(listOf(ProbeEntry(null, null)))
        preservesInstance(hasEntry<Any?, Any?>(null, null), actual)
    }

    @ParameterizedTest
    @MethodSource("factories")
    fun `a null expected value still requires its key`(factory: (Any?, Any?) -> AwaitCondition, negated: Boolean) {
        assertEquals(negated, evaluate(factory("missing", null), mapOf("present" to null)) is Satisfied<*>)
    }

    @Test
    fun `compares array keys and values by content`() {
        val actual = EntryMap(listOf(ProbeEntry(intArrayOf(1), arrayOf("value"))))
        preservesInstance(hasEntry(intArrayOf(1), arrayOf("value")), actual)
    }

    @Test
    fun `compares from the observation without entry equality or hashing`() {
        val key = Directional(true)
        val value = Directional(true)
        val expectedKey = Directional(false)
        val expectedValue = Directional(false)
        val actual = EntryMap(listOf(ProbeEntry(key, value)))

        preservesInstance(hasEntry(expectedKey, expectedValue), actual)
        assertEquals(listOf(1, 1, 0, 0), listOf(key.equalsCalls, value.equalsCalls, expectedKey.equalsCalls, expectedValue.equalsCalls))
    }

    @Test
    fun `a different key skips value equality`() {
        val actual = EntryMap(listOf(ProbeEntry("actual", ThrowingEquals { error("value must not be compared") })))
        rejection(hasEntry("expected", ThrowingEquals {}), actual)
    }

    @Test
    fun `absence preserves equality failures`() {
        val cause = IllegalStateException("equals failed")
        val actual = EntryMap(listOf(ProbeEntry("key", ThrowingEquals { throw cause })))
        assertSame(cause, thrown(hasNoEntry("key", ThrowingEquals {}), actual))
    }

    @Test
    fun `absence preserves traversal failures`() {
        val cause = IllegalStateException("entries failed")
        val actual = EntryMap(listOf(ProbeEntry("key", "value")))
        actual.beforeEntrySet = Runnable { throw cause }
        assertSame(cause, thrown(hasNoEntry("key", "value"), actual))
    }

    @Test
    fun `a different key skips reading the entry value`() {
        val entry = object : Map.Entry<String, String> {
            override val key = "other"
            override val value: String get() = error("unmatched entry value must not be read")
        }
        val actual = EntryMap(listOf(entry))
        rejection(hasEntry("wanted", "ready"), actual)
    }

    companion object {
        @JvmStatic
        fun entries() = listOf(
            argumentSet("empty", emptyMap<String, String>(), false),
            argumentSet("other key", mapOf("other" to "1"), false),
            argumentSet("other value", mapOf("ready" to "0"), false),
            argumentSet("key and value in different entries", mapOf("ready" to "0", "other" to "1"), false),
            argumentSet("null value", mapOf("ready" to null), false),
            argumentSet("null key", mapOf(null to "1"), false),
            argumentSet("matching entry", linkedMapOf("pending" to "0", "ready" to "1"), true))

        @JvmStatic
        fun factories() = listOf(
            argumentSet("present", { key: Any?, value: Any? -> hasEntry(key, value) }, false),
            argumentSet("absent", { key: Any?, value: Any? -> hasNoEntry(key, value) }, true))
    }
}

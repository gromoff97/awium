package io.github.gromoff97.awium.conditions.map

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.map.MapConditions.entryFor
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.conditions.support.ProbeContainers.ProbeEntry
import io.github.gromoff97.awium.conditions.support.Directional
import io.github.gromoff97.awium.conditions.support.ThrowingEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class EntryForConditionTest {
    @Test
    fun `returns the original entry for the selected key`() {
        val selected = ProbeEntry("key", "value")
        val actual = EntryMap(listOf(ProbeEntry("other", "skip"), selected))
        assertSame(selected, result(evaluate(entryFor("key"), actual)))
    }

    @Test
    fun `a null key can select an entry`() {
        val selected = ProbeEntry(null, "value")
        assertSame(selected, result(evaluate(entryFor<String>(null), EntryMap(listOf(selected)))))
    }

    @Test
    fun `compares array keys by content`() {
        val selected = ProbeEntry(intArrayOf(1, 2), "value")
        assertSame(selected, result(evaluate(entryFor(intArrayOf(1, 2)), EntryMap(listOf(selected)))))
    }

    @Test
    fun `compares from the observation without hashing`() {
        val key = Directional(true)
        val expected = Directional(false)
        val selected = ProbeEntry(key, "value")
        assertSame(selected, result(evaluate(entryFor(expected), EntryMap(listOf(selected)))))
        assertEquals(1, key.equalsCalls)
        assertEquals(0, expected.equalsCalls)
    }

    @Test
    fun `stops after the first matching entry`() {
        val selected = ProbeEntry<Any, String>("key", "value")
        val neverCompared = ThrowingEquals { error("later entry must not be checked") }
        val actual = EntryMap(listOf(selected, ProbeEntry(neverCompared, "skip")))
        assertSame(selected, result(evaluate(entryFor("key"), actual)))
    }

    @Test
    fun `preserves key comparison failures`() {
        val cause = IllegalStateException("key failed")
        val actual = EntryMap(listOf(ProbeEntry(ThrowingEquals { throw cause }, "value")))
        assertSame(cause, thrown(entryFor(ThrowingEquals {}), actual))
    }

    @ParameterizedTest
    @MethodSource("missingEntries")
    fun `explains why no entry was selected`(actual: Map<String, String>?, message: String) {
        assertEquals(message, rejection(entryFor("key"), actual).mismatch())
    }

    companion object {
        @JvmStatic
        fun missingEntries() = listOf(
            argumentSet("null map", null, "map was null"),
            argumentSet("empty map", emptyMap<String, String>(), "map did not contain the expected key"),
            argumentSet("other key", mapOf("other" to "value"), "map did not contain the expected key"))
    }
}

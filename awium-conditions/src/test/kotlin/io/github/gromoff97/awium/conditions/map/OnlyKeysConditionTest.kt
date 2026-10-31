package io.github.gromoff97.awium.conditions.map

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.MapConditions.containsOnlyKeys
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.conditions.support.ProbeContainers.ProbeEntry
import io.github.gromoff97.awium.conditions.support.Directional
import io.github.gromoff97.awium.conditions.support.smallLists
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OnlyKeysConditionTest {
    @Test
    fun `compares key sets regardless of order and expected duplicates`() {
        val inputs = smallLists()
        for (expected in inputs) {
            val condition = containsOnlyKeys(expected)
            for (keys in inputs) {
                val actual = keys.associateWith { "value" }
                assertEquals(keys.toSet() == expected.toSet(), evaluate(condition, actual) is Satisfied<*>,
                    "keys=$keys, expected=$expected")
            }
        }
    }

    @Test
    fun `a null map is a mismatch`() {
        assertEquals("map was null", rejection(containsOnlyKeys(emptyList<String>()), null).mismatch())
    }

    @Test
    fun `rejects missing expected keys`() {
        assertThrows(NullPointerException::class.java) { containsOnlyKeys<String>(null) }
    }

    @Test
    fun `copies expected keys`() {
        val expected = mutableListOf("a", "b")
        val condition = containsOnlyKeys(expected)
        expected.clear()
        val actual = mapOf("a" to 1, "b" to 2)

        assertSame(actual, result(evaluate(condition, actual)))
    }

    @Test
    fun `uses observation-first equality without hashing`() {
        val key = Directional(true)
        val expected = Directional(false)
        val actual = EntryMap(listOf(ProbeEntry(key, "value")))

        assertSame(actual, result(evaluate(containsOnlyKeys(listOf(expected)), actual)))
        assertEquals(2, key.equalsCalls)
        assertEquals(0, expected.equalsCalls)
    }

    @Test
    fun `compares array keys by content`() {
        val actual = EntryMap(listOf(ProbeEntry(intArrayOf(1), "value")))
        assertSame(actual, result(evaluate(containsOnlyKeys(listOf(intArrayOf(1))), actual)))
    }
}

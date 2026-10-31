package io.github.gromoff97.awium.conditions.map

import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.MapConditions.singleEntry
import io.github.gromoff97.awium.conditions.MapConditions.allEntries
import io.github.gromoff97.awium.conditions.ProbeContainers.EntryMap
import io.github.gromoff97.awium.conditions.ProbeContainers.ProbeEntry
import io.github.gromoff97.awium.execution.ConditionExecution.bind
import io.github.gromoff97.awium.function.CheckedBiPredicate
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EntryMatchesConditionTest {
    @Test
    fun `passes key and value to the predicate and preserves the entry`() {
        val entry = ProbeEntry("key", 42)
        val checked = mutableListOf<Pair<String, Int>>()
        val condition = singleEntry { key: String, value: Int -> checked.add(key to value); true }

        assertSame(entry, result(evaluate(condition, EntryMap(listOf(entry)))))
        assertEquals(listOf("key" to 42), checked)
    }

    @Test
    fun `false is a mismatch`() {
        val condition = singleEntry { _: String, value: Int -> value == 42 }
        assertEquals("single entry: value did not match", rejection(condition, mapOf("key" to 1)).mismatch())
    }

    @Test
    fun `checks the next observation after a mismatch`() {
        val condition = singleEntry { _: String, value: Int -> value == 42 }
        val execution = bind<Map<String, Int>, Map.Entry<String, Int>>(condition)
        assertInstanceOf(Unsatisfied::class.java, execution.probe(mapOf("key" to 1)))
        assertEquals(42, result(execution.probe(mapOf("key" to 42))).value)
    }

    @Test
    fun `preserves checked predicate failures`() {
        val cause = IOException("entry failed")
        val condition = singleEntry { _: String, _: Int -> throw cause }
        assertSame(cause, thrown(condition, mapOf("key" to 1)))
    }

    @Test
    fun `rejects a missing predicate`() {
        assertThrows(NullPointerException::class.java) { singleEntry(null as CheckedBiPredicate<String, Int>?) }
    }

    @Test
    fun `entry quantifiers reject a missing paired predicate`() {
        assertThrows(NullPointerException::class.java) { allEntries(null as CheckedBiPredicate<String, Int>?) }
    }
}

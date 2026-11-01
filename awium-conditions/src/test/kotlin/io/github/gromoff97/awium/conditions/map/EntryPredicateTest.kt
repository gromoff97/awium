package io.github.gromoff97.awium.conditions.map

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.allEntries
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.conditions.support.ProbeContainers.ProbeEntry
import io.github.gromoff97.awium.function.AwaitAttempt.bind
import io.github.gromoff97.awium.function.checked.CheckedBiPredicate
import io.github.gromoff97.awium.result.AttemptResult.Unsatisfied
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EntryPredicateTest {
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
        val failure = assertThrows(NullPointerException::class.java) { singleEntry(null as CheckedBiPredicate<String, Int>?) }
        assertEquals("predicate must not be null", failure.message)
    }

    @Test
    fun `entry quantifiers reject a missing paired predicate`() {
        assertThrows(NullPointerException::class.java) { allEntries(null as CheckedBiPredicate<String, Int>?) }
    }
}

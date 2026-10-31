package io.github.gromoff97.awium.conditions.composition

import io.github.gromoff97.awium.conditions.CollectionConditions.firstElement
import io.github.gromoff97.awium.conditions.CollectionConditions.lastElement
import io.github.gromoff97.awium.conditions.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.ConditionAssertions.probe
import io.github.gromoff97.awium.conditions.Conditions.captured
import io.github.gromoff97.awium.conditions.Conditions.conditionPerWait
import io.github.gromoff97.awium.conditions.Conditions.instanceOf
import io.github.gromoff97.awium.conditions.Conditions.matches
import io.github.gromoff97.awium.execution.ConditionExecution.start
import io.github.gromoff97.awium.function.CheckedFunction
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import io.github.gromoff97.awium.result.ConditionResult.satisfied
import io.github.gromoff97.awium.result.ConditionResult.unsatisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CapturedConditionTest {
    @Test
    fun `selector stages capture whole observations instead of their selected values`() {
        val first = listOf("first", 1)
        val last = listOf(2, "last")
        val run = start<List<Any>, List<List<Any>>>(captured(firstElement(instanceOf(String::class.java)),
            lastElement(instanceOf(String::class.java))), false)

        probe(run, first)
        probe(run, last)
        assertTrue(run.complete())
        assertSame(first, run.result().first())
        assertSame(last, run.result().last())
    }

    @Test
    fun `a pending nested stage preserves earlier captures`() {
        val pending = listOf(false)
        val run = start<List<Boolean>, List<List<Boolean>>>(captured(
            singleElement(matches { ready: Boolean -> !ready }), singleElement(matches { ready: Boolean -> ready })), false)
        probe(run, pending)

        repeat(2) { assertInstanceOf(Unsatisfied::class.java, probe(run, pending)) }
        assertEquals(listOf(pending), run.result())
        assertFalse(run.complete())
    }

    @Test
    fun `each nested selector stage initializes its own callback`() {
        var factories = 0
        val nested = conditionPerWait<String, String>("ready after retry") {
            factories++
            var calls = 0
            CheckedFunction { actual -> if (++calls == 2) satisfied(actual) else unsatisfied("retry") }
        }
        val run = start<List<String>, List<List<String>>>(captured(singleElement(nested), singleElement(nested)), false)

        repeat(4) { probe(run, listOf("ready")) }
        assertTrue(run.complete())
        assertEquals(2, factories)
    }

    @Test
    fun `captured results cannot be modified`() {
        val run = start<List<String>, List<List<String>>>(captured(singleElement, singleElement), false)
        repeat(2) { probe(run, listOf("ready")) }
        assertThrows(UnsupportedOperationException::class.java) { (run.result() as MutableList).clear() }
    }
}

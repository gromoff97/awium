package io.github.gromoff97.awium.conditions.composition

import consumer.collection.CapturedConsumer
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.firstElement
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.lastElement
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.probe
import io.github.gromoff97.awium.conditions.common.Conditions.captured
import io.github.gromoff97.awium.conditions.common.Conditions.conditionPerWait
import io.github.gromoff97.awium.conditions.common.Conditions.equalTo
import io.github.gromoff97.awium.conditions.common.Conditions.isNotNull
import io.github.gromoff97.awium.conditions.common.Conditions.instanceOf
import io.github.gromoff97.awium.conditions.common.Conditions.matches
import io.github.gromoff97.awium.conditions.map.MapConditions.valueFor
import io.github.gromoff97.awium.execution.ConditionExecution.start
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import io.github.gromoff97.awium.result.ConditionResult.satisfied
import io.github.gromoff97.awium.result.ConditionResult.unsatisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class CapturedConditionTest {
    @AfterEach
    fun `clear the caller interrupt flag`() {
        Thread.interrupted()
    }

    @Test
    fun `changing a reason creates an independent definition with the same stages`() {
        val original = captured(equalTo("ready"), equalTo("done"))
        val first = original.because("first reason")
        val second = first.because("second reason")

        assertNotSame(original, first)
        assertNotSame(first, second)
        assertNull(original.explanation())
        assertEquals("first reason", first.explanation())
        assertEquals("second reason", second.explanation())
        assertEquals(original.description(), second.description())
        assertEquals(original.references(), second.references())
        assertEquals(original.stages(), second.stages())
    }

    @Test
    fun `a null reason is rejected`() {
        assertThrows(NullPointerException::class.java) { captured(isNotNull).because(null) }
    }

    @Test
    fun `a blank reason is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { captured(isNotNull).because(" \n\t") }
    }

    @Test
    fun `the stage list exposes the conditions in declaration order`() {
        val first = equalTo("early")
        val last = equalTo("final")
        assertEquals(listOf(first, last), captured(first, last).stages())
    }

    @Test
    fun `the stage list cannot be modified`() {
        val stages = captured(equalTo("early"), equalTo("final")).stages()
        assertThrows(UnsupportedOperationException::class.java) { (stages as MutableList).clear() }
    }

    @Test
    fun `an empty stage array is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { captured(*emptyArray<PreservingCondition<String>>()) }
    }

    @Test
    fun `a null stage array is rejected`() {
        assertThrows(NullPointerException::class.java) { CapturedConsumer.nullStages() }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `a null stage is rejected before evaluation`(index: Int) {
        val stages = arrayOf<PreservingCondition<String>?>(matches { true }, matches { true }, matches { true })
        stages[index] = null
        assertThrows(NullPointerException::class.java) { captured(*stages) }
    }
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
            AwaitAttempt { actual -> if (++calls == 2) satisfied(actual) else unsatisfied("retry") }
        }
        val run = start<List<String>, List<List<String>>>(captured(singleElement(nested), singleElement(nested)), false)

        repeat(4) { probe(run, listOf("ready")) }
        assertTrue(run.complete())
        assertEquals(2, factories)
    }

    @Test
    fun `repeated factory stages stay independent when mixed with comparisons`() {
        var factories = 0
        val afterRetry = conditionPerWait<String, Int>("second invocation") {
            factories++
            var calls = 0
            AwaitAttempt { if (++calls == 2) satisfied(42) else unsatisfied("retry") }
        }
        val sequence = captured(afterRetry, equalTo("pending"), afterRetry)
        repeat(2) {
            val run = start<String, List<String>>(sequence, false)
            for (actual in listOf("created", "created", "pending", "created", "created")) probe(run, actual)
            assertEquals(listOf("created", "pending", "created"), run.result())
            assertTrue(run.complete())
        }
        assertEquals(4, factories)
    }

    @Test
    fun `captured results cannot be modified`() {
        val run = start<List<String>, List<List<String>>>(captured(singleElement, singleElement), false)
        repeat(2) { probe(run, listOf("ready")) }
        assertThrows(UnsupportedOperationException::class.java) { (run.result() as MutableList).clear() }
    }

    @Test
    fun `nullable map value captures remain intact when the last stage is refreshed`() {
        val first = mapOf<String, Number?>("first" to null)
        val acquired = mapOf<String, Number?>("last" to 2)
        val refreshed = mapOf<String, Number?>("last" to 3)
        val run = start<Map<String, Number?>, List<Map<String, Number?>>>(
            captured(valueFor("first"), valueFor("last", isNotNull)), false)

        probe(run, first)
        probe(run, acquired)
        val initial = run.result()
        probe(run, refreshed)
        assertTrue(run.complete())
        assertSame(first, run.result().first())
        assertSame(refreshed, run.result().last())
        assertSame(acquired, initial.last())
    }

    @Test
    fun `refreshing selector stages replaces only the last observation`() {
        val first = listOf("first", "ignored")
        val acquired = listOf("ignored", "acquired")
        val refreshed = listOf("ignored", "refreshed")
        val run = start<List<String>, List<List<String>>>(captured(firstElement, lastElement), false)
        for (actual in listOf(first, acquired, refreshed)) probe(run, actual)
        assertSame(first, run.result().first())
        assertSame(refreshed, run.result().last())
    }

    @ParameterizedTest
    @CsvSource("1,false", "1,true", "2,false", "2,true")
    fun `an interrupted stage never adds an observation`(stage: Int, matching: Boolean) {
        val first = matches { _: String ->
            if (stage == 1) Thread.currentThread().interrupt()
            stage == 2 || matching
        }
        val second = matches { _: String -> Thread.currentThread().interrupt(); matching }
        val run = start<String, List<String>>(captured(first, second), false)
        if (stage == 2) probe(run, "ready")

        assertThrows(InterruptedException::class.java) { probe(run, "ready") }
        assertEquals(stage - 1, run.result().size)
        assertFalse(run.complete())
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @Test
    fun `each probe advances at most one stage`() {
        var calls = 0
        val stage = matches { value: Int -> calls++; value == 1 }
        val run = start<Int, List<Int>>(captured(stage, stage, stage), false)
        for (count in 1..3) {
            assertInstanceOf(Satisfied::class.java, probe(run, 1))
            assertEquals(count, calls)
            assertEquals(count, run.result().size)
            assertEquals(count == 3, run.complete())
        }
    }

    @Test
    fun `a completed sequence rechecks only its final stage`() {
        var earlyCalls = 0
        var finalCalls = 0
        val run = start<String, List<String>>(captured(
            matches { value: String -> earlyCalls++; value == "early" },
            matches { value: String -> finalCalls++; value != "early" }), false)

        for (actual in listOf("early", "acquired", "refreshed", "latest")) probe(run, actual)
        assertEquals(listOf("early", "latest"), run.result())
        assertEquals(1, earlyCalls)
        assertEquals(3, finalCalls)
    }

    @Test
    fun `a final stage mismatch removes its previous capture`() {
        val run = start<String, List<String>>(captured(equalTo("early"), equalTo("final")), false)
        probe(run, "early")
        probe(run, "final")

        assertInstanceOf(Unsatisfied::class.java, probe(run, "rejected"))
        assertFalse(run.complete())
        assertEquals(listOf("early"), run.result())
        probe(run, "final")
        assertTrue(run.complete())
        assertEquals(listOf("early", "final"), run.result())
    }

    @Test
    fun `later states cannot skip an unfinished intermediate stage`() {
        val run = start<String, List<String>>(captured(equalTo("created"), equalTo("pending"), equalTo("finished")), false)
        probe(run, "created")
        repeat(2) { assertInstanceOf(Unsatisfied::class.java, probe(run, "finished")) }
        assertFalse(run.complete())
        assertEquals(listOf("created"), run.result())
    }

    @Test
    fun `new runs are independent of completed and incomplete executions`() {
        val condition = captured(equalTo("created"), equalTo("finished"))
        val complete = start<String, List<String>>(condition, false)
        probe(complete, "created")
        probe(complete, "finished")
        val incomplete = start<String, List<String>>(condition, false)
        probe(incomplete, "created")
        probe(incomplete, "pending")
        val next = start<String, List<String>>(condition, false)

        assertInstanceOf(Unsatisfied::class.java, probe(next, "finished"))
        assertTrue(next.result().isEmpty())
        probe(next, "created")
        probe(next, "finished")
        assertEquals(listOf("created", "finished"), next.result())
        assertEquals(next.result(), complete.result())
        assertEquals(listOf("created"), incomplete.result())
    }
}

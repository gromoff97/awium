package io.github.gromoff97.awium.conditions.composition

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import consumer.collection.CapturedConsumer
import io.github.gromoff97.awium.Await.tryAwait
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.firstElement
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.lastElement
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.failure
import io.github.gromoff97.awium.conditions.common.CommonConditions.captured
import io.github.gromoff97.awium.conditions.common.CommonConditions.conditionPerWait
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.isNotNull
import io.github.gromoff97.awium.conditions.common.CommonConditions.instanceOf
import io.github.gromoff97.awium.conditions.common.CommonConditions.matches
import io.github.gromoff97.awium.conditions.map.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.capturedRun
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.result.AttemptResult.Unsatisfied
import io.github.gromoff97.awium.result.AttemptResult.Satisfied
import io.github.gromoff97.awium.result.AttemptResult.satisfied
import io.github.gromoff97.awium.result.AttemptResult.unsatisfied
import java.time.Duration.ofNanos
import java.util.concurrent.Callable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class CapturedConditionTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `rebinding a captured run starts the sequence over`(completed: Boolean) {
        val run = captured(equalTo("ready"), equalTo("done")).newRun()
        val first = AwaitAttempt.bind<String>(run)
        first.probe("ready")
        if (completed) first.probe("done")

        val second = AwaitAttempt.bind<String>(run)

        assertInstanceOf<Unsatisfied<*>>(second.probe("done"))
    }

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
        assertThrows<NullPointerException> { captured(isNotNull).because(null) }
    }

    @Test
    fun `a blank reason is rejected`() {
        assertThrows<IllegalArgumentException> { captured(isNotNull).because(" \n\t") }
    }

    @Test
    fun `the stage list exposes the conditions in declaration order`() {
        val first = equalTo("early")
        val last = equalTo("final")
        assertEquals(listOf(first, last), CapturedConsumer.explainedStages(first, last))
    }

    @Test
    fun `the stage list cannot be modified`() {
        val stages = captured(equalTo("early"), equalTo("final")).stages()
        assertThrows<UnsupportedOperationException> { (stages as MutableList).clear() }
    }

    @Test
    fun `an empty stage array is rejected`() {
        assertThrows<IllegalArgumentException> { captured(*emptyArray<PreservingCondition<String>>()) }
    }

    @Test
    fun `a null stage array is rejected`() {
        assertThrows<NullPointerException> { CapturedConsumer.nullStages() }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `a null stage is rejected before evaluation`(index: Int) {
        val stages = arrayOf<PreservingCondition<String>?>(matches { true }, matches { true }, matches { true })
        stages[index] = null
        assertThrows<NullPointerException> { captured(*stages) }
    }
    @Test
    fun `selector stages capture whole observations instead of their selected values`() {
        val first = listOf("first", 1)
        val last = listOf(2, "last")
        val run = capturedRun<List<Any>>(captured(firstElement(instanceOf(String::class.java)),
            lastElement(instanceOf(String::class.java))))

        run.probe(first)
        val captured = result(run.probe(last))
        assertSame(first, captured.first())
        assertSame(last, captured.last())
    }

    @Test
    fun `a pending nested stage preserves earlier captures`() {
        val pending = listOf(false)
        val run = capturedRun<List<Boolean>>(captured(
            singleElement(matches { ready: Boolean -> !ready }), singleElement(matches { ready: Boolean -> ready })))
        run.probe(pending)

        repeat(2) { assertInstanceOf<Unsatisfied<*>>(run.probe(pending)) }
        val ready = listOf(true)
        assertEquals(listOf(pending, ready), result(run.probe(ready)))
    }

    @Test
    fun `each nested selector stage initializes its own callback`() {
        var factories = 0
        val nested = conditionPerWait<String, String>("ready after retry") {
            factories++
            var calls = 0
            AwaitAttempt { actual -> if (++calls == 2) satisfied(actual) else unsatisfied("retry") }
        }
        val run = capturedRun<List<String>>(captured(singleElement(nested), singleElement(nested)))

        repeat(3) { run.probe(listOf("ready")) }
        assertEquals(List(2) { listOf("ready") }, result(run.probe(listOf("ready"))))
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
            val run = capturedRun<String>(sequence)
            for (actual in listOf("created", "created", "pending", "created")) run.probe(actual)
            assertEquals(listOf("created", "pending", "created"), result(run.probe("created")))
        }
        assertEquals(4, factories)
    }

    @Test
    fun `captured results cannot be modified`() {
        val run = capturedRun<List<String>>(captured(singleElement, singleElement))
        run.probe(listOf("ready"))
        val captured = result(run.probe(listOf("ready")))
        assertThrows<UnsupportedOperationException> { (captured as MutableList).clear() }
    }

    @Test
    fun `nullable map value captures remain intact when the last stage is refreshed`() {
        val first = mapOf<String, Number?>("first" to null)
        val acquired = mapOf<String, Number?>("last" to 2)
        val refreshed = mapOf<String, Number?>("last" to 3)
        val run = capturedRun<Map<String, Number?>>(
            captured(valueFor("first"), valueFor("last", isNotNull)))

        run.probe(first)
        val initial = result(run.probe(acquired))
        val captured = result(run.probe(refreshed))
        assertSame(first, captured.first())
        assertSame(refreshed, captured.last())
        assertSame(acquired, initial.last())
    }

    @Test
    fun `refreshing selector stages replaces only the last observation`() {
        val first = listOf("first", "ignored")
        val acquired = listOf("ignored", "acquired")
        val refreshed = listOf("ignored", "refreshed")
        val run = capturedRun<List<String>>(captured(firstElement, lastElement))
        run.probe(first)
        run.probe(acquired)
        val captured = result(run.probe(refreshed))
        assertSame(first, captured.first())
        assertSame(refreshed, captured.last())
    }

    @ParameterizedTest
    @CsvSource("1,false", "1,true", "2,false", "2,true")
    fun `an interrupted stage never adds an observation`(stage: Int, matching: Boolean) {
        val first = matches { _: String ->
            if (stage == 1) Thread.currentThread().interrupt()
            stage == 2 || matching
        }
        val second = matches { _: String -> Thread.currentThread().interrupt(); matching }
        val result = tryAwait(Callable { "ready" }).every(ofNanos(1)).until(captured(first, second))

        assertInstanceOf<InterruptedException>(failure(result).cause())
        assertTrue(failure(result).message().contains("captured ${stage - 1}/2"))
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @Test
    fun `each probe advances at most one stage`() {
        var calls = 0
        val stage = matches { value: Int -> calls++; value == 1 }
        val run = capturedRun<Int>(captured(stage, stage, stage))
        for (count in 1..3) {
            val outcome = run.probe(1)
            assertEquals(count == 3, outcome is Satisfied<*>)
            assertInstanceOf<Satisfied<*>>(run.historyResult(outcome))
            assertEquals(count, calls)
            if (count == 3) assertEquals(listOf(1, 1, 1), result(outcome))
        }
    }

    @Test
    fun `a completed sequence rechecks only its final stage`() {
        var earlyCalls = 0
        var finalCalls = 0
        val run = capturedRun<String>(captured(
            matches { value: String -> earlyCalls++; value == "early" },
            matches { value: String -> finalCalls++; value != "early" }))

        for (actual in listOf("early", "acquired", "refreshed")) run.probe(actual)
        assertEquals(listOf("early", "latest"), result(run.probe("latest")))
        assertEquals(1, earlyCalls)
        assertEquals(3, finalCalls)
    }

    @Test
    fun `a final stage mismatch removes its previous capture`() {
        val run = capturedRun<String>(captured(equalTo("early"), equalTo("final")))
        run.probe("early")
        run.probe("final")

        assertInstanceOf<Unsatisfied<*>>(run.probe("rejected"))
        assertEquals(listOf("early", "final"), result(run.probe("final")))
    }

    @Test
    fun `later states cannot skip an unfinished intermediate stage`() {
        val run = capturedRun<String>(captured(equalTo("created"), equalTo("pending"), equalTo("finished")))
        run.probe("created")
        repeat(2) { assertInstanceOf<Unsatisfied<*>>(run.probe("finished")) }
        assertInstanceOf<Unsatisfied<*>>(run.probe("pending"))
        assertEquals(listOf("created", "pending", "finished"), result(run.probe("finished")))
    }

    @Test
    fun `new runs are independent of completed and incomplete executions`() {
        val condition = captured(equalTo("created"), equalTo("finished"))
        val complete = capturedRun<String>(condition)
        complete.probe("created")
        val completeResult = result(complete.probe("finished"))
        val incomplete = capturedRun<String>(condition)
        incomplete.probe("created")
        incomplete.probe("pending")
        val next = capturedRun<String>(condition)

        assertInstanceOf<Unsatisfied<*>>(next.probe("finished"))
        next.probe("created")
        val nextResult = result(next.probe("finished"))
        assertEquals(listOf("created", "finished"), nextResult)
        assertEquals(nextResult, completeResult)
        assertEquals(nextResult, result(incomplete.probe("finished")))
    }
}

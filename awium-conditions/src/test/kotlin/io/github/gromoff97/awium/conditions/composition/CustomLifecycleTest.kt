package io.github.gromoff97.awium.conditions.composition

import consumer.ArtifactConsumer.Length
import consumer.CountedCondition
import consumer.value.OpenConditionConsumer
import io.github.gromoff97.awium.Await.tryAwait
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.common.CommonConditions.captured
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.execution.ConditionExecution.bind
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.result.AttemptResult
import io.github.gromoff97.awium.result.AttemptResult.Unsatisfied
import io.github.gromoff97.awium.result.AttemptResult.satisfied
import io.github.gromoff97.awium.result.AttemptResult.unsatisfied
import java.time.Duration.ofNanos
import java.util.concurrent.Callable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class CustomLifecycleTest {
    @Test
    fun `selectors keep the result type of an explained user condition`() {
        assertEquals(listOf(5, 5, 5), OpenConditionConsumer.nestedResults(Length().because("measure text")))
    }

    @Test
    fun `captured keeps source observations when user conditions extract other values`() {
        val values = listOf("ready")
        val result = OpenConditionConsumer.mixedStages(values, Length().because("measure text"))
        assertEquals(3, result.conditions().size)
        assertEquals(3, result.orThrow().size)
        result.orThrow().forEach { assertSame(values, it) }
    }

    @Test
    fun `a nested user lifecycle waits for its verdict and starts fresh on reuse`() {
        val check = singleElement(CountedCondition(2).because("twice"))
        repeat(2) {
            val execution = bind(check)
            assertInstanceOf(Unsatisfied::class.java, execution.probe(listOf("ready")))
            assertEquals(10, result(execution.probe(listOf("ready"))))
        }
    }

    @Test
    fun `captured advances only after the user lifecycle completes`() {
        val check = CountedCondition(2).because("twice")
        val result = tryAwait(Callable { "ready" }).every(ofNanos(1)).until(captured(check, equalTo("ready")))
        assertEquals(listOf("ready", "ready"), result.orThrow())
        assertSame(check, result.conditions().first().condition())
        assertEquals(listOf(2L, 3L), result.conditions().map { it.attempts().lastKey() })
    }

    @Test
    fun `history projection does not change a nested selected result`() {
        val check = object : Condition<String, Int> {
            override fun description() = "measure text"
            override fun probe(actual: String) = satisfied(actual.length)
            override fun historyResult(attempt: AttemptResult<out Int>) = unsatisfied<Int>("observed")
        }
        assertEquals(5, result(evaluate(singleElement(check), listOf("ready"))))
    }

    @Test
    fun `captured initializes a user evaluator separately for each stage and run`() {
        var factories = 0
        val check = object : Condition<String, String> {
            override fun description() = "second observation"
            override fun probe(actual: String): AttemptResult<String> = error("factory must supply the evaluator")
            override fun newAttempt(): AwaitAttempt<String, String> {
                factories++
                var calls = 0
                return AwaitAttempt { if (++calls == 2) satisfied(it) else unsatisfied("waiting for a second observation") }
            }
        }.because("fresh state")
        val sequence = captured(check, check)
        assertEquals(0, factories)
        repeat(2) { completedRuns ->
            val result = tryAwait(Callable { "ready" }).every(ofNanos(1)).until(sequence)
            assertEquals(listOf("ready", "ready"), result.orThrow())
            assertEquals(4L, result.conditions().last().attempts().lastKey())
            assertEquals((completedRuns + 1) * 2, factories)
        }
    }
}

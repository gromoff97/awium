package io.github.gromoff97.awium.conditions.diagnostics

import io.github.gromoff97.awium.dsl.AwaitCondition.definitionOf

import io.github.gromoff97.awium.dsl.Await.tryAwait
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.all
import io.github.gromoff97.awium.conditions.common.CommonConditions.captured
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.execution.AwaitHandler
import io.github.gromoff97.awium.execution.AwaitHandler.AttemptFinished
import io.github.gromoff97.awium.execution.AwaitHandler.WaitFinished
import io.github.gromoff97.awium.execution.AwaitHandler.WaitStarted
import io.github.gromoff97.awium.execution.DefaultAwaitHandler
import io.github.gromoff97.awium.result.Attempt
import io.github.gromoff97.awium.result.Attempt.SatisfiedObservation
import io.github.gromoff97.awium.result.ConditionHistory
import java.time.Duration.ofNanos
import java.util.TreeMap
import java.util.concurrent.Callable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class AwaitHandlerTest {
    @Test
    fun `captured reports the root verdict and supplies its own stage history`() {
        val observations = listOf("created", "created", "paid", "shipped").iterator()
        val condition = captured(equalTo("created"), equalTo("paid"), equalTo("shipped"))
            .because("order must complete")
        val events = mutableListOf<AttemptFinished<String>>()
        var started: WaitStarted? = null
        val handler = object : DefaultAwaitHandler<String>() {
            override fun onStart(start: WaitStarted) {
                super.onStart(start)
                started = start
            }
            override fun onAttemptFinish(event: AttemptFinished<String>) {
                super.onAttemptFinish(event)
                events.add(event)
            }
        }

        val result = tryAwait(Callable { observations.next() }).handling { handler }
            .every(ofNanos(1)).until(condition)

        assertEquals(listOf("created", "paid", "shipped"), result.orThrow())
        assertSame(condition, definitionOf(started!!.condition()).orElseThrow())
        assertEquals(listOf(false, false, false, true), events.map { it.attempt() is SatisfiedObservation<*> })
        assertEquals(listOf(true, false, true, true), events.map { it.historyAttempt() is SatisfiedObservation<*> })
        assertEquals(listOf(1, 2, 2, 3), events.map { it.historySlot() })
        assertEquals(listOf(setOf(1L), setOf(2L, 3L), setOf(4L)), result.conditions().map { it.attempts().keys })
    }

    @Test
    fun `nested element checks do not create additional attempt events`() {
        data class User(val active: Boolean)
        val snapshots = listOf(listOf(User(true), User(false)), listOf(User(true), User(true))).iterator()
        val events = mutableListOf<AttemptFinished<List<User>>>()
        var checked = 0
        val condition = all<User> { user ->
            checked++
            user.active
        }
        val handler = object : DefaultAwaitHandler<List<User>>() {
            override fun onAttemptFinish(event: AttemptFinished<List<User>>) {
                super.onAttemptFinish(event)
                events.add(event)
            }
        }

        val result = tryAwait(Callable { snapshots.next() }).handling { handler }
            .every(ofNanos(1)).until(condition)

        assertThat(result.orThrow()).allMatch { it.active }
        assertEquals(4, checked)
        assertEquals(listOf(1L, 2L), events.map { it.number() })
        assertEquals(listOf(false, true), events.map { it.attempt() is SatisfiedObservation<*> })
    }

    @Test
    fun `a custom handler can keep one root history for a captured sequence`() {
        val observations = listOf("created", "paid").iterator()
        val condition = captured(equalTo("created"), equalTo("paid"))
        val history = TreeMap<Long, Attempt<String>>()
        val handler = object : AwaitHandler<String> {
            private lateinit var start: WaitStarted
            override fun onStart(event: WaitStarted) { start = event }
            override fun onAttemptFinish(event: AttemptFinished<String>) {
                history[event.number()] = event.attempt()
            }
            override fun onFinish(finish: WaitFinished) = listOf(ConditionHistory(start.condition(), history))
        }

        val result = tryAwait(Callable { observations.next() }).handling { handler }
            .every(ofNanos(1)).until(condition)

        assertEquals(listOf("created", "paid"), result.orThrow())
        assertEquals(1, result.conditions().size)
        assertSame(condition, definitionOf(result.conditions().single().condition()).orElseThrow())
        assertEquals(listOf(false, true), result.conditions().single().attempts().values.map { it is SatisfiedObservation<*> })
    }
}

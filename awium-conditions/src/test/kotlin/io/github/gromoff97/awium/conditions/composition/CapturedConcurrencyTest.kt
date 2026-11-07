package io.github.gromoff97.awium.conditions.composition

import io.github.gromoff97.awium.dsl.AwaitCondition.definitionOf

import io.github.gromoff97.awium.dsl.Await.tryAwait
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.attempts
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.observation
import io.github.gromoff97.awium.conditions.common.CommonConditions.captured
import io.github.gromoff97.awium.conditions.common.CommonConditions.conditionPerWait
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.result.AttemptResult.satisfied
import io.github.gromoff97.awium.result.AttemptResult.unsatisfied
import java.time.Duration.ofNanos
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.AutoClose
import org.junit.jupiter.api.Test

class CapturedConcurrencyTest {
    @AutoClose
    private val executor = newVirtualThreadPerTaskExecutor()

    @Test
    fun `concurrent runs keep their stage state observations and histories independent`() {
        val rendezvous = CyclicBarrier(2)
        val factories = AtomicInteger()
        val check = conditionPerWait<Thread, String>("two observations per stage") {
            factories.incrementAndGet()
            val owner = Thread.currentThread()
            var calls = 0
            AwaitAttempt { actual ->
                assertSame(owner, actual)
                if (++calls == 1) {
                    rendezvous.await(3, SECONDS)
                    unsatisfied("first observation")
                } else satisfied(actual.name)
            }
        }
        val sequence = captured(check, check)
        val futures = List(2) {
            executor.submit(Callable {
                tryAwait(Callable { Thread.currentThread() }).every(ofNanos(1)).until(sequence)
            })
        }

        val callers = futures.map { future ->
            val result = future.get(5, SECONDS)
            val caller = result.orThrow().first()
            assertEquals(listOf(caller, caller), result.orThrow())
            assertEquals(2, result.conditions().size)
            result.conditions().forEach { history ->
                assertSame(check, definitionOf(history.condition()).orElseThrow())
                assertEquals(2, history.attempts().size)
            }
            val attempts = attempts(result)
            assertEquals(4, attempts.size)
            attempts.forEach { assertSame(caller, observation(it)) }
            caller
        }
        assertEquals(2, callers.toSet().size)
        assertEquals(4, factories.get())
    }
}

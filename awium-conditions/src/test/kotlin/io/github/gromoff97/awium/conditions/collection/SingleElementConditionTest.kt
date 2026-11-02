package io.github.gromoff97.awium.conditions.collection

import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ProbeContainers.once
import io.github.gromoff97.awium.function.AwaitAttempt.bind
import io.github.gromoff97.awium.result.AttemptResult.Unsatisfied
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class SingleElementConditionTest {
    @Test
    fun `returns the only element`() {
        val actual = Any()
        assertSame(actual, result(evaluate(singleElement, listOf(actual))))
    }

    @Test
    fun `returns a null element`() {
        assertNull(result(evaluate(singleElement, listOf(null))))
    }

    @ParameterizedTest
    @MethodSource("invalidCollections")
    fun `reports why no single element exists`(actual: Collection<*>?, expected: String) {
        assertEquals(expected, rejection(singleElement, actual).mismatch())
    }

    @Test
    fun `a queue emptied before traversal can match on the next probe`() {
        val actual = object : ConcurrentLinkedQueue<String>() {
            val clearOnce = once { clear() }
            override fun iterator(): MutableIterator<String> {
                clearOnce.run()
                return super.iterator()
            }
        }
        actual.add("old")
        val execution = bind<Collection<String>>(singleElement)

        assertInstanceOf<Unsatisfied<*>>(execution.probe(actual))
        actual.add("ready")
        assertEquals("ready", result(execution.probe(actual)))
    }

    companion object {
        @JvmStatic
        fun invalidCollections() = listOf(
            arguments(null, "collection was null"),
            arguments(emptyList<Any>(), "collection size was 0"),
            arguments(listOf("first", "second"), "collection size was greater than 1")
        )
    }
}

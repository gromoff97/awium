package io.github.gromoff97.awium.conditions.collection

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.CollectionConditions.elementAt
import io.github.gromoff97.awium.conditions.CollectionConditions.firstElement
import io.github.gromoff97.awium.conditions.CollectionConditions.lastElement
import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.ProbeContainers.once
import io.github.gromoff97.awium.execution.ConditionExecution.bind
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import java.util.SequencedCollection
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class ElementAtConditionTest {
    @ParameterizedTest
    @MethodSource("positions")
    fun `returns the selected element by identity`(condition: AwaitCondition, index: Int) {
        val actual = List(3) { Any() }
        assertSame(actual[index], result(evaluate(condition, actual)))
    }

    @ParameterizedTest
    @MethodSource("selectors")
    fun `a null collection is a mismatch`(condition: AwaitCondition) {
        assertEquals("collection was null", rejection(condition, null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("selectors")
    fun `an empty collection reports its size`(condition: AwaitCondition) {
        assertEquals("collection size was 0", rejection(condition, emptyList<Any>()).mismatch())
    }

    @ParameterizedTest
    @MethodSource("selectors")
    fun `null is a valid selected element`(condition: AwaitCondition) {
        assertNull(result(evaluate(condition, listOf(null))))
    }

    @Test
    fun `an index at the end is out of bounds`() {
        assertEquals("collection size was 3", rejection(elementAt(3), listOf(1, 2, 3)).mismatch())
    }

    @Test
    fun `rejects a negative index immediately`() {
        val failure = assertThrows(IllegalArgumentException::class.java) { elementAt(-1) }
        assertEquals("index must be non-negative", failure.message)
    }

    @Test
    fun `a reused evaluator reads the latest last element`() {
        val execution = bind<List<Int>, Int>(lastElement)
        for (last in 1..3) assertEquals(last, result(execution.probe(listOf(last))))
    }

    @Test
    fun `the first-element description explains the selection`() {
        assertEquals("collection has a first element", firstElement.description())
    }

    @ParameterizedTest
    @MethodSource("changingContainers")
    fun `a collection emptied before traversal can match on the next probe`(
        condition: AwaitCondition, create: () -> SequencedCollection<String>
    ) {
        val actual = create()
        actual.add("old")
        val execution = bind<SequencedCollection<String>, String>(condition)

        assertInstanceOf(Unsatisfied::class.java, execution.probe(actual))
        actual.add("ready")
        assertEquals("ready", result(execution.probe(actual)))
    }

    companion object {
        @JvmStatic
        fun positions() = listOf(
            argumentSet("first", firstElement, 0), argumentSet("last", lastElement, 2),
            argumentSet("index", elementAt(1), 1)
        )

        @JvmStatic
        fun selectors() = listOf(firstElement, lastElement, elementAt(0))

        @JvmStatic
        fun changingContainers() = listOf(
            argumentSet("list index", elementAt(0), {
                object : CopyOnWriteArrayList<String>() {
                    val clearOnce = once { clear() }
                    override fun iterator(): MutableIterator<String> {
                        clearOnce.run()
                        return super.iterator()
                    }
                }
            }),
            argumentSet("deque first", firstElement, ::changingDeque),
            argumentSet("deque last", lastElement, ::changingDeque)
        )

        private fun changingDeque() = object : ConcurrentLinkedDeque<String>() {
            val clearOnce = once { clear() }
            override fun iterator(): MutableIterator<String> {
                clearOnce.run()
                return super.iterator()
            }
            override fun descendingIterator(): MutableIterator<String> {
                clearOnce.run()
                return super.descendingIterator()
            }
        }
    }
}

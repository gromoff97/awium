package io.github.gromoff97.awium.conditions.api

import consumer.value.OpenConditionConsumer
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.dsl.TypedCondition
import io.github.gromoff97.awium.result.AttemptResult
import io.github.gromoff97.awium.result.AttemptResult.satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CoreConditionInteropTest {
    private val length = object : Condition<CharSequence, Int> {
        override fun description() = "text length"
        override fun probe(actual: CharSequence) = satisfied(actual.length)
    }

    @Test
    fun `a core condition works through both entry points without adapting its type`() {
        assertEquals(listOf(5, 5, 5, 5), OpenConditionConsumer.terminalResults(length))
    }

    @Test
    fun `nested selectors accept a core condition and return its result`() {
        assertEquals(listOf(5, 5, 5), OpenConditionConsumer.nestedResults(length))
    }

    @Test
    fun `adapting a core condition allows mixed capture stages without losing observations`() {
        val actual = listOf("ready")
        val result = OpenConditionConsumer.adaptedStages(actual, length)
        assertEquals(3, result.conditions().size)
        result.orThrow().forEach { assertSame(actual, it) }
    }

    @Test
    fun `adapting a condition retains its exact probe result`() {
        val expected = satisfied("ready")
        val original = object : Condition<String, String> {
            override fun description() = "ready"
            override fun probe(actual: String) = expected
        }
        assertSame(expected, TypedCondition.from(original).probe("ready"))
    }

    @Test
    fun `an adapted reason replaces the old reason without changing the definition`() {
        val explained = TypedCondition.from(length).because("old").because("new")
        assertNull(length.explanation())
        assertEquals("new", explained.explanation())
        assertEquals(satisfied(5), explained.probe("ready"))
    }

    @Test
    fun `an adapted definition still creates a fresh core run`() {
        val original = object : Condition<String, Int> {
            override fun description() = "count observations"
            override fun probe(actual: String): AttemptResult<Int> = error("run required")
            override fun newRun(): Condition<String, Int> = object : Condition<String, Int> {
                private var count = 0
                override fun description() = "count observations"
                override fun probe(actual: String) = satisfied(++count)
            }
        }
        val adapted = TypedCondition.from(original).because("independent runs")
        val first = adapted.newRun()
        val second = adapted.newRun()
        assertNotSame(first, second)
        assertEquals(satisfied(1), first.probe("ready"))
        assertEquals(satisfied(2), first.probe("ready"))
        assertEquals(satisfied(1), second.probe("ready"))
    }

    @Test
    fun `adapting a missing condition fails immediately`() {
        assertEquals("condition must not be null",
            assertThrows<NullPointerException> { TypedCondition.from<String, String>(null) }.message)
    }
}

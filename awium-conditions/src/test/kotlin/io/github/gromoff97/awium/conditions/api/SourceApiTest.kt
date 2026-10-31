package io.github.gromoff97.awium.conditions.api
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.observation
import java.math.BigDecimal
import java.util.Optional
import java.util.concurrent.Callable
import org.junit.jupiter.api.Assertions.assertSame

import consumer.SourceConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SourceApiTest {
    @Test
    fun `covariant views retain selection bounds and accept broader callbacks`() {
        assertEquals(listOf(1, 1, 1, 1, 1), SourceConsumer.typedViews())
    }
    @Test
    fun `covariant collection predicates retain selected values and captured containers`() {
        val actual: Number = BigDecimal("42")
        val expected: Number = BigDecimal("42")
        val values: List<Number> = listOf(actual)
        var calls = 0
        val source = Callable<List<Number>> { if (++calls == 1) listOf(0) else values }
        assertSame(actual, SourceConsumer.selectedCollectionView(source, expected))
        assertEquals(2, calls)
        val result = SourceConsumer.recordedCollectionView(source, expected)
        assertSame(actual, result.orThrow())
        assertSame(values, observation(result.conditions().single().attempts().firstEntry().value))
        assertEquals(3, calls)
        val captured = SourceConsumer.capturedCollectionView(source, expected)
        assertEquals(2, captured.size)
        captured.forEach { assertSame(values, it) }
        assertEquals(5, calls)
    }

    @Test
    fun `covariant map predicates retain selected values and captured containers`() {
        val actual: Number = BigDecimal("42")
        val expected: Number = BigDecimal("42")
        val values: Map<String, Number> = mapOf("answer" to actual)
        val source = Callable { values }
        assertSame(actual, SourceConsumer.selectedMapView(source, expected))
        val result = SourceConsumer.recordedMapView(source, expected)
        assertSame(actual, result.orThrow())
        assertSame(values, observation(result.conditions().single().attempts().firstEntry().value))
        val captured = SourceConsumer.capturedMapView(source, expected)
        assertEquals(2, captured.size)
        captured.forEach { assertSame(values, it) }
    }

    @Test
    fun `inline predicates infer user types through selection quantifiers and capture`() {
        val user = SourceConsumer.User(true)
        val values = SourceConsumer.inlineSelections(user)
        assertEquals(9, values.size)
        values.forEach { assertSame(user, it) }
    }

    @Test
    fun `captured retains the concrete collection behind a callable reference`() {
        val actual = arrayListOf("ready")
        val values = SourceConsumer.callableCollectionCapture { actual }
        assertEquals(2, values.size)
        values.forEach { assertSame(actual, it) }
    }

    @Test
    fun `captured retains optional observations from a covariant callable`() {
        val actual = Optional.of("ready")
        val values = SourceConsumer.covariantOptionalCapture { actual }
        assertEquals(2, values.size)
        values.forEach { assertSame(actual, it) }
    }

}

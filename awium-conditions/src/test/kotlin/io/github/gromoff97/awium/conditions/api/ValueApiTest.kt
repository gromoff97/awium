package io.github.gromoff97.awium.conditions.api

import consumer.value.ValueConsumer
import io.github.gromoff97.awium.conditions.ConditionAssertions.attempts
import io.github.gromoff97.awium.conditions.ConditionAssertions.observation
import java.math.BigDecimal
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ValueApiTest {
    @Test
    fun `candidate conditions retain scalar and container types`() {
        assertEquals(List(5) { 5 }, ValueConsumer.candidateLengths())
    }

    @ParameterizedTest
    @MethodSource("arrays")
    fun `array candidates remain whole values in both terminals and captured`(actual: Array<String>) {
        ValueConsumer.arrayCandidates(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `primitive array candidates keep the array type`() {
        assertEquals(42, ValueConsumer.primitiveArrayCandidate(intArrayOf(42)))
    }

    @Test
    fun `negative comparisons keep the observed type and identity`() {
        val actual = String(charArrayOf('a'))
        ValueConsumer.negativeComparisons(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `a condition accepting Object preserves the concrete source type`() {
        val actual = String(charArrayOf('a'))
        ValueConsumer.broadPreservingCondition(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `captured retains null and present observations in order`() {
        assertEquals(listOf(null, "ready"), ValueConsumer.captureNullThenPresent())
    }

    @Test
    fun `tryAwait retains nullable captured observations`() {
        assertEquals(listOf(null, "ready"), ValueConsumer.captureNullThenPresentResult().orThrow())
    }

    @Test
    fun `a nested null check keeps the selected element type`() {
        assertNull(ValueConsumer.missingElement())
    }

    @ParameterizedTest
    @MethodSource("absenceSelections")
    fun `absence preserves the selected optional`(select: (Optional<String>) -> Optional<String>) {
        val empty = Optional.empty<String>()
        assertSame(empty, select(empty))
    }

    @Test
    fun `type checks narrow direct and optional observations`() {
        assertEquals(List(3) { "ready" }, ValueConsumer.narrowedText())
    }

    @Test
    fun `a type check can select a broader type`() {
        assertEquals(1, ValueConsumer.broadenedNumber())
    }

    @Test
    fun `present selects the projected optional value`() {
        assertEquals(5, ValueConsumer.projectedOptionalLength())
    }

    @Test
    fun `narrowed results retain the original observation`() {
        val actual = BigDecimal("42.00")
        val result = ValueConsumer.narrowedResult(actual)
        assertSame(actual, result.orThrow())
        assertSame(actual, observation(attempts(result).single()))
    }

    @Test
    fun `null checks and predicates return successful null results`() {
        ValueConsumer.nullResults().forEach { assertNull(it.orThrow()) }
    }

    @Test
    fun `recorded optional selections expose their contained value`() {
        assertEquals("payment", ValueConsumer.optionalResult().orThrow())
    }

    companion object {
        @JvmStatic
        fun arrays() = listOf(named("empty", emptyArray<String>()), named("one element", arrayOf("ready")))

        @JvmStatic
        fun absenceSelections() = listOf(
            named("nested optional", ValueConsumer::nestedAbsence),
            named("map value", ValueConsumer::mappedAbsence)
        )
    }
}

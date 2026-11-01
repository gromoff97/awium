package io.github.gromoff97.awium.conditions.api

import consumer.collection.CapturedConsumer
import consumer.collection.CapturedConsumer.Payment
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.attempts
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.observation
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CapturedApiTest {
    private val payments = (1..3).map { Payment(it, "stage $it") }

    @Test
    fun `optional stages mix comparisons with method references`() {
        val observations = payments.map { Optional.of(it) }
        val values = CapturedConsumer.optionalStages(observations)
        assertSame(observations.first(), values.first())
        assertSame(observations.last(), values.last())
    }

    @Test
    fun `collection stages mix comparisons with method references`() {
        val observations = payments.map { listOf(it) }
        val result = CapturedConsumer.collectionStages(observations)
        assertSame(observations.first(), result.orThrow().first())
        assertSame(observations.last(), result.orThrow().last())
        assertSame(observations[1], observation(attempts(result)[1]))
    }

    @Test
    fun `map value stages keep method reference types after because`() {
        val observations = payments.map { mapOf("user" to it) }
        val result = CapturedConsumer.mapStages(observations)
        assertSame(observations.first(), result.orThrow().first())
        assertSame(observations.last(), result.orThrow().last())
        assertSame(observations[1], observation(attempts(result)[1]))
        assertEquals("ready", result.conditions().last().condition().explanation())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `alternating comparisons and predicates keep the source type`(recorded: Boolean) {
        val (created, pending, ready) = payments
        assertEquals(listOf(created, pending, ready, ready),
            CapturedConsumer.alternatingStages(listOf(created, created, pending, ready, ready), recorded))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `mixed checks and selections retain whole observations`(recorded: Boolean) {
        val observations = (1..3).map { listOf(Payment(it, "stage $it")) }
        val values = if (recorded) CapturedConsumer.recordedMixedChecks(observations).orThrow()
            else CapturedConsumer.mixedChecks(observations)
        assertThat(values).zipSatisfy(observations) { actual, expected -> assertSame(expected, actual) }
    }

    @Test
    fun `mixed stages record the complete observed container`() {
        val observations = (1..3).map { listOf(Payment(it, "stage $it")) }
        val result = CapturedConsumer.recordedMixedChecks(observations)
        assertEquals(observations, attempts(result).map(::observation))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `different stage result types do not change the capture type`(recorded: Boolean) {
        val observations = listOf(false, "ready", false, 1234)
        val values = if (recorded) CapturedConsumer.recordedHeterogeneousResults(observations).orThrow()
            else CapturedConsumer.heterogeneousResults(observations)
        assertEquals(listOf("ready", 1234), values)
    }

    @Test
    fun `rejected observations remain in each stage history`() {
        val result = CapturedConsumer.recordedHeterogeneousResults(listOf(false, "ready", false, 1234))
        assertEquals(listOf(false, "ready", false, 1234), attempts(result).map(::observation))
    }

    @Test
    fun `reusable captures infer source types across container families`() {
        assertEquals(listOf(4, 4, 5, 4, 4, 4), CapturedConsumer.reusableCaptures(Payment(3, "paid")))
    }

    @Test
    fun `a reason preserves narrowing stages`() {
        val payment = Payment(3, "paid")
        assertThat(CapturedConsumer.explainedNarrowing(payment)).hasSize(2).allSatisfy { assertSame(payment, it) }
    }

    @Test
    fun `a single stage still returns a list of observations`() {
        val payment = Payment(3, "paid")
        val result = CapturedConsumer.oneOptional(payment)
        assertEquals(1, result.size)
        assertSame(payment, result.single().orElseThrow())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `narrowing stages retry until their respective types match`(recorded: Boolean) {
        assertEquals(listOf("first", "second"),
            CapturedConsumer.narrowedStrings(listOf(42, "first", 43L, "second"), recorded))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `nested narrowing retains each original container`(recorded: Boolean) {
        val observations = listOf(listOf<Number>(1000), listOf<Number>(2000))
        assertThat(CapturedConsumer.nestedNarrowing(observations, recorded)).zipSatisfy(observations) { actual, expected ->
            assertSame(expected, actual)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `comparisons preserve the broader source type`(recorded: Boolean) {
        val observations = listOf(BigDecimal("1.00"), BigInteger("2"), 1024L)
        assertThat(CapturedConsumer.comparedNumbers(observations, recorded)).zipSatisfy(observations) { actual, expected ->
            assertSame(expected, actual)
        }
    }

    @Test
    fun `capture uses the value returned by the source projection`() {
        assertEquals(listOf(1, 2), CapturedConsumer.projectedObservations())
    }

    @Test
    fun `predicate stages infer the source object type`() {
        val observations = (1..3).map { Payment(it, "stage $it") }
        assertEquals(observations, CapturedConsumer.orderedStates(observations))
    }

    @Test
    fun `successful null observations are captured`() {
        assertEquals(listOf(null, null), CapturedConsumer.nullableObservations())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `changes to the caller array do not change the captured stages`(recorded: Boolean) {
        assertEquals(listOf(1, 2), CapturedConsumer.copiedArray(recorded))
    }
}

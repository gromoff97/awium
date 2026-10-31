package io.github.gromoff97.awium.conditions.api

import consumer.value.CheckedPredicateConsumer
import consumer.value.CheckedPredicateConsumer.User
import io.github.gromoff97.awium.conditions.ConditionAssertions.attempts
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CheckedPredicateApiTest {
    private val pending = User(0, false)
    private val first = User(1, true)
    private val last = User(2, true)

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `negated checked references retry and keep the source type`(recorded: Boolean) {
        assertSame(pending, CheckedPredicateConsumer.negatedMatch(listOf(first, pending), recorded))
    }

    @Test
    fun `a negated checked reference keeps the selected element type`() {
        assertEquals(0, CheckedPredicateConsumer.negatedElement(pending))
    }

    @Test
    fun `a negated paired reference keeps the selected entry types`() {
        val entry = CheckedPredicateConsumer.negatedEntry(listOf(mapOf("ready" to first), mapOf("pending" to pending)))
        assertEquals("pending", entry.key)
        assertSame(pending, entry.value)
    }

    @Test
    fun `a checked method reference retries before returning the typed value`() {
        assertEquals(1, CheckedPredicateConsumer.matchingId(listOf(pending, first)))
    }

    @Test
    fun `a recorded checked predicate retains its retries`() {
        val result = CheckedPredicateConsumer.recordedMatch(listOf(pending, last))
        assertSame(last, result.orThrow())
        assertEquals(2, attempts(result).size)
    }

    @Test
    fun `a lambda calling a checked method infers its input type`() {
        assertEquals(1, CheckedPredicateConsumer.lambdaId(first))
    }

    @Test
    fun `checked selectors retain the element type`() {
        assertEquals(listOf(1, 2, 1, 1), CheckedPredicateConsumer.selectedIds(listOf(first, pending, last)))
    }

    @Test
    fun `checked quantifiers retain their original containers`() {
        val mixed = listOf(first, pending, last)
        val ready = arrayListOf(first, last)
        val unavailable = listOf(pending)
        assertSame(mixed, CheckedPredicateConsumer.someReady(mixed))
        assertSame(ready, CheckedPredicateConsumer.allReady(ready))
        assertSame(unavailable, CheckedPredicateConsumer.noneReady(unavailable))
    }

    @Test
    fun `checked entry key and value predicates retain the concrete map type`() {
        val actual = linkedMapOf("first" to first, "last" to last)
        CheckedPredicateConsumer.mapChecks(actual).forEach { assertSame(actual, it) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `a checked paired predicate selects the typed entry`(recorded: Boolean) {
        val entry = CheckedPredicateConsumer.checkedEntry(mapOf("first" to first), recorded)
        assertEquals("first", entry.key)
        assertSame(first, entry.value)
    }

    @Test
    fun `a checked optional predicate tolerates a missing value`() {
        assertEquals(1, CheckedPredicateConsumer.optionalId(listOf(Optional.empty(), Optional.of(first))))
    }

    @Test
    fun `a checked map value predicate retries missing and mismatching values`() {
        val result = CheckedPredicateConsumer.valueResult(listOf(emptyMap(), mapOf("user" to pending), mapOf("user" to first)))
        assertSame(first, result.orThrow())
        assertEquals(3, attempts(result).size)
    }
}

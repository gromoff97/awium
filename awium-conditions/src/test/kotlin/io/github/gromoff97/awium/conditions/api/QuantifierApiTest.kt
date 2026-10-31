package io.github.gromoff97.awium.conditions.api

import consumer.collection.QuantifierConsumer
import consumer.collection.QuantifierConsumer.User
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class QuantifierApiTest {
    private val active = User(1, true)
    private val pending = User(2, false)

    @Test
    fun `all present preserves the optional collection after because`() {
        val actual = arrayListOf(Optional.of(active))
        assertSame(actual, QuantifierConsumer.allPresent(actual))
    }

    @Test
    fun `any present preserves the optional collection in tryAwait`() {
        val actual = arrayListOf(Optional.empty(), Optional.of(active))
        assertSame(actual, QuantifierConsumer.somePresent(actual))
    }

    @Test
    fun `none present preserves the optional collection`() {
        val actual = arrayListOf(Optional.empty<User>())
        assertSame(actual, QuantifierConsumer.nonePresent(actual))
    }

    @Test
    fun `map quantifiers discard nested selector results`() {
        val actual = linkedMapOf(Optional.of(active) to listOf(active))
        QuantifierConsumer.nestedMapSelections(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `map quantifiers accept nested map value selection`() {
        val actual = mapOf("group" to mapOf("user" to active))
        assertSame(actual, QuantifierConsumer.nestedMapValue(actual))
    }

    @Test
    fun `all accepts every condition family without changing the collection type`() {
        val actual = arrayListOf(active)
        QuantifierConsumer.allFamilies(actual, active).forEach { assertSame(actual, it) }
    }

    @Test
    fun `any accepts comparisons and checks in both terminals`() {
        val actual = arrayListOf(pending, active)
        QuantifierConsumer.anyFamilies(actual, active).forEach { assertSame(actual, it) }
    }

    @Test
    fun `none accepts every condition family without changing the collection type`() {
        val actual = arrayListOf(pending)
        QuantifierConsumer.noFamilies(actual, active).forEach { assertSame(actual, it) }
    }

    @Test
    fun `a narrowing quantifier preserves heterogeneous source elements`() {
        val actual = listOf("skip", active)
        assertSame(actual, QuantifierConsumer.heterogeneousValues(actual))
    }

    @Test
    fun `map key value and entry quantifiers preserve the concrete map`() {
        val actual = linkedMapOf("user" to active)
        QuantifierConsumer.mapConditionFamilies(actual, active, pending).forEach { assertSame(actual, it) }
    }

    @Test
    fun `entry and value comparisons accept narrower expected values`() {
        val actual = mapOf<String, Number>("count" to 1)
        QuantifierConsumer.narrowerExpectedValues(actual).forEach { assertSame(actual, it) }
    }
    @Test
    fun `entry predicates can accept broader key and value types`() {
        val actual = linkedMapOf("key" to 1)
        QuantifierConsumer.supertypeEntryPredicates(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `element predicates can accept a broader element type`() {
        val actual = arrayListOf("")
        QuantifierConsumer.supertypeElementPredicates(actual).forEach { assertSame(actual, it) }
    }

}

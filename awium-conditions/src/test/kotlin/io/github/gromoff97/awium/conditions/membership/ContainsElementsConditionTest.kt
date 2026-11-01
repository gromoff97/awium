package io.github.gromoff97.awium.conditions.membership

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsAll
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsAny
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainAll
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainAny
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.map.MapConditions.containsAllEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.containsAllKeys
import io.github.gromoff97.awium.conditions.map.MapConditions.containsAllValues
import io.github.gromoff97.awium.conditions.map.MapConditions.containsAnyEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAllEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAnyEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAnyKeys
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAnyValues
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.conditions.support.ProbeContainers.ProbeEntry
import io.github.gromoff97.awium.conditions.support.smallLists
import io.github.gromoff97.awium.result.AttemptResult.Satisfied
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock

class ContainsElementsConditionTest {
    data class Membership(
        val factory: (Iterable<String?>?) -> AwaitCondition,
        val accepts: (List<String?>, List<String?>) -> Boolean
    )

    data class MapMembership(
        val factory: (Map<String?, String?>?) -> AwaitCondition,
        val accepts: (Map<String?, String?>, Map<String?, String?>) -> Boolean
    )

    @ParameterizedTest
    @MethodSource("mapMemberships")
    fun `map membership checks the selected keys values or whole entries`(membership: MapMembership) {
        val maps = listOf<Map<String?, String?>>(emptyMap(), mapOf("a" to "1"), mapOf("a" to "2"), mapOf("b" to "1"),
            linkedMapOf("a" to "1", "b" to "2"), linkedMapOf("b" to "2", "a" to "1"),
            mapOf(null to "1"), mapOf("a" to null))
        for (expected in maps) for (actual in maps) {
            assertEquals(membership.accepts(actual, expected), evaluate(membership.factory(expected), actual) is Satisfied<*>,
                "actual=$actual, expected=$expected")
        }
    }

    @ParameterizedTest
    @MethodSource("mapMemberships")
    fun `a null map never matches an empty expectation`(membership: MapMembership) {
        assertEquals("map was null", rejection(membership.factory(emptyMap()), null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("mapMemberships")
    fun `empty map expectations do not read the observation`(membership: MapMembership) {
        val actual = mock(Map::class.java) { throw AssertionError("empty expectations need no map reads") }
        assertEquals(membership.accepts(mapOf("a" to "1"), emptyMap()),
            evaluate(membership.factory(emptyMap()), actual) is Satisfied<*>)
    }

    @ParameterizedTest
    @MethodSource("mapMemberships")
    fun `rejects missing expected map contents`(membership: MapMembership) {
        assertThrows(NullPointerException::class.java) { membership.factory(null) }
    }

    @Test
    fun `entry snapshot failures are reported when the condition is created`() {
        val cause = IllegalStateException("expected entries failed")
        val expected = EntryMap(listOf(ProbeEntry("key", "value")))
        expected.beforeEntrySet = Runnable { throw cause }
        assertSame(cause, assertThrows(IllegalStateException::class.java) { doesNotContainAnyEntries(expected) })
    }

    @ParameterizedTest
    @MethodSource("memberships")
    fun `membership handles missing values nulls and duplicates`(membership: Membership) {
        val inputs = smallLists()
        for (expected in inputs) {
            val condition = membership.factory(expected)
            for (actual in inputs) {
                assertEquals(membership.accepts(actual, expected), evaluate(condition, actual) is Satisfied<*>,
                    "actual=$actual, expected=$expected")
            }
        }
    }

    @ParameterizedTest
    @MethodSource("memberships")
    fun `a null collection never matches even an empty expectation`(membership: Membership) {
        assertEquals("collection was null", rejection(membership.factory(emptyList()), null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("memberships")
    fun `empty expectations do not read the observation`(membership: Membership) {
        val actual = mock(Collection::class.java) { throw AssertionError("empty expectations need no collection reads") }

        assertEquals(membership.accepts(emptyList(), emptyList()), evaluate(membership.factory(emptyList()), actual) is Satisfied<*>)
    }

    @ParameterizedTest
    @MethodSource("memberships")
    fun `rejects missing expected elements immediately`(membership: Membership) {
        val cause = assertThrows(NullPointerException::class.java) { membership.factory(null) }
        assertThat(cause.message).contains("elements")
    }

    @Test
    fun `nested collections and arrays remain complete elements`() {
        val actual = listOf(listOf("a", "b"), intArrayOf(1, 2))
        assertSame(actual, result(evaluate(containsAll(listOf(listOf("a", "b"), intArrayOf(1, 2))), actual)))
        rejection(containsAll(listOf(listOf("a"))), actual)
    }

    @Test
    fun `reports the first missing expected index`() {
        val condition = containsAll(listOf("a", "b", "c"))
        assertEquals("collection did not contain all expected elements; first missing at expected index 1",
            rejection(condition, listOf("a", "c")).mismatch())
    }

    @Test
    fun `reports the first forbidden expected index`() {
        val condition = doesNotContainAny(listOf("x", "b", "a"))
        assertEquals("collection contained unexpected elements; first at unexpected index 1",
            rejection(condition, listOf("a", "b")).mismatch())
    }

    @Test
    fun `searches for each expected element using observation-first equality`() {
        class Value(val name: String, val calls: MutableList<String>) {
            override fun equals(other: Any?): Boolean {
                if (other !is Value) return false
                calls += "$name->${other.name}"
                return name == other.name
            }
            override fun hashCode(): Int = throw AssertionError("membership must not hash elements")
        }
        val comparisons = mutableListOf<String>()
        val actual = listOf(Value("a", comparisons), Value("b", comparisons))
        val expected = listOf(Value("b", mutableListOf()), Value("a", mutableListOf()))

        assertSame(actual, result(evaluate(containsAll(expected), actual)))
        assertEquals(listOf("a->b", "b->b", "a->a"), comparisons)
        assertEquals(emptyList<String>(), expected.flatMap { it.calls })
    }

    companion object {
        @JvmStatic
        fun mapMemberships() = listOf(
            named("all keys", MapMembership({ containsAllKeys(it?.keys) }, { actual, expected -> actual.keys.containsAll(expected.keys) })),
            named("no keys", MapMembership({ doesNotContainAnyKeys(it?.keys) }, { actual, expected -> expected.keys.none { it in actual } })),
            named("all values", MapMembership({ containsAllValues(it?.values) }, { actual, expected -> actual.values.containsAll(expected.values) })),
            named("no values", MapMembership({ doesNotContainAnyValues(it?.values) }, { actual, expected -> expected.values.none { it in actual.values } })),
            named("all entries", MapMembership({ containsAllEntries(it) }, { actual, expected -> actual.entries.containsAll(expected.entries) })),
            named("any entry", MapMembership({ containsAnyEntries(it) }, { actual, expected -> expected.entries.any { it in actual.entries } })),
            named("no entries", MapMembership({ doesNotContainAnyEntries(it) }, { actual, expected -> expected.entries.none { it in actual.entries } })),
            named("not all entries", MapMembership({ doesNotContainAllEntries(it) }, { actual, expected -> !actual.entries.containsAll(expected.entries) }))
        )

        @JvmStatic
        fun memberships() = listOf(
            named("all", Membership({ containsAll(it) }, { actual, expected -> actual.containsAll(expected) })),
            named("any", Membership({ containsAny(it) }, { actual, expected -> expected.any { it in actual } })),
            named("none", Membership({ doesNotContainAny(it) }, { actual, expected -> expected.none { it in actual } })),
            named("not all", Membership({ doesNotContainAll(it) }, { actual, expected -> !actual.containsAll(expected) }))
        )
    }
}

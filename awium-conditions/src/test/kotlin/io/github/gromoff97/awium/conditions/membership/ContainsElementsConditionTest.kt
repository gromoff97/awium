package io.github.gromoff97.awium.conditions.membership

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.CollectionConditions.containsAll
import io.github.gromoff97.awium.conditions.CollectionConditions.containsAny
import io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContainAll
import io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContainAny
import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.ProbeContainers.membershipCollection
import io.github.gromoff97.awium.conditions.support.smallLists
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ContainsElementsConditionTest {
    data class Membership(
        val factory: (Iterable<String?>?) -> AwaitCondition,
        val accepts: (List<String?>, List<String?>) -> Boolean
    )

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
    fun `empty expectations do not traverse the observation`(membership: Membership) {
        val actual = membershipCollection(emptyList<String>())
        `when`(actual.iterator()).thenThrow(AssertionError("unnecessary traversal"))

        assertEquals(membership.accepts(emptyList(), emptyList()), evaluate(membership.factory(emptyList()), actual) is Satisfied<*>)
        verify(actual, never()).iterator()
    }

    @ParameterizedTest
    @MethodSource("memberships")
    fun `rejects missing expected elements immediately`(membership: Membership) {
        val cause = assertThrows(NullPointerException::class.java) { membership.factory(null) }
        assertThat(cause.message).contains("elements")
    }

    @ParameterizedTest
    @MethodSource("memberships")
    fun `copies expected elements before later mutations`(membership: Membership) {
        val expected = mutableListOf("a", null, "a")
        val condition = membership.factory(expected)
        val before = evaluate(condition, listOf("a", null))
        expected.clear()

        assertEquals(before, evaluate(condition, listOf("a", null)))
        assertEquals(listOf("a", null, "a"), condition.references().single().value())
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
        fun memberships() = listOf(
            named("all", Membership({ containsAll(it) }, { actual, expected -> actual.containsAll(expected) })),
            named("any", Membership({ containsAny(it) }, { actual, expected -> expected.any { it in actual } })),
            named("none", Membership({ doesNotContainAny(it) }, { actual, expected -> expected.none { it in actual } })),
            named("not all", Membership({ doesNotContainAll(it) }, { actual, expected -> !actual.containsAll(expected) }))
        )
    }
}

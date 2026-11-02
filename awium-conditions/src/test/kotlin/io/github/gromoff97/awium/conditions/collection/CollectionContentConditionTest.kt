package io.github.gromoff97.awium.conditions.collection

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsExactly
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsExactlyInAnyOrder
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsOnly
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainExactly
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainExactlyInAnyOrder
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.subsetOf
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.preservesInstance
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.support.ProbeContainers.once
import io.github.gromoff97.awium.conditions.support.Directional
import io.github.gromoff97.awium.conditions.support.ThrowingEquals
import io.github.gromoff97.awium.conditions.support.smallLists
import io.github.gromoff97.awium.result.AttemptResult.Satisfied
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class CollectionContentConditionTest {
    data class Rule(
        val factory: (Iterable<Any?>?) -> AwaitCondition,
        val accepts: (List<String?>, List<String?>) -> Boolean
    )

    @ParameterizedTest
    @MethodSource("rules")
    fun `content checks distinguish order multiplicity and membership`(rule: Rule) {
        val inputs = smallLists()
        for (expected in inputs) {
            val condition = rule.factory(expected)
            for (actual in inputs) {
                assertEquals(rule.accepts(actual, expected), evaluate(condition, actual) is Satisfied<*>,
                    "actual=$actual, expected=$expected")
            }
        }
    }

    @ParameterizedTest
    @MethodSource("rules")
    fun `a null collection is a mismatch`(rule: Rule) {
        assertEquals("collection was null", rejection(rule.factory(emptyList()), null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("rules")
    fun `rejects a missing expected collection`(rule: Rule) {
        assertThrows<NullPointerException> { rule.factory(null) }
    }

    @ParameterizedTest
    @MethodSource("exactMismatches")
    fun `different sizes do not start traversal`(factory: (Iterable<Any?>?) -> AwaitCondition, negated: Boolean, size: Int) {
        val actual = object : ArrayList<String>(List(size) { "a" }) {
            override fun iterator(): MutableIterator<String> = throw AssertionError("different size needs no traversal")
        }
        assertEquals(negated, evaluate(factory(listOf("a", "b")), actual) is Satisfied<*>)
    }

    @ParameterizedTest
    @MethodSource("changingContents")
    fun `detects content changes between size and traversal`(
        factory: (Iterable<Any?>?) -> AwaitCondition, negated: Boolean, grow: Boolean
    ) {
        val actual = object : ArrayList<String>(listOf("a", "b")) {
            val mutate = once { if (grow) add("c") else removeLast() }
            override fun iterator(): MutableIterator<String> {
                mutate.run()
                return super.iterator()
            }
        }
        assertEquals(negated, evaluate(factory(listOf("a", "b")), actual) is Satisfied<*>)
    }

    @ParameterizedTest
    @MethodSource("positiveRules")
    fun `uses observation-first equality without hashing`(rule: Rule) {
        val observed = Directional(true)
        val expected = Directional(false)
        val actual = listOf(observed)

        preservesInstance(rule.factory(listOf(expected)), actual)
        assertThat(observed.equalsCalls).isGreaterThan(0)
        assertEquals(0, expected.equalsCalls)
    }

    @ParameterizedTest
    @MethodSource("positiveRules")
    fun `compares arrays by their contents`(rule: Rule) {
        val actual = listOf(intArrayOf(1, 2), null)
        preservesInstance(rule.factory(listOf(intArrayOf(1, 2), null)), actual)
    }

    @Test
    fun `negative exact checks preserve traversal failures`() {
        val cause = IllegalStateException("iterator failed")
        val actual: List<String> = mock()
        `when`(actual.size).thenReturn(1)
        `when`(actual.iterator()).thenThrow(cause)

        assertSame(cause, thrown(doesNotContainExactly(listOf("a")), actual))
    }

    @Test
    fun `negative exact checks preserve equality failures`() {
        val cause = IllegalStateException("equals failed")
        val actual = listOf(ThrowingEquals { throw cause })
        assertSame(cause, thrown(doesNotContainExactly(listOf(ThrowingEquals {})), actual))
    }

    @Test
    fun `mismatch reporting does not traverse the actual again`() {
        val actual: List<String> = mock()
        `when`(actual.size).thenReturn(1)
        `when`(actual.iterator()).thenAnswer { listOf("a").iterator() }
        val condition = containsExactly(listOf("b"))

        assertThat(rejection(condition, actual).mismatch()).isNotBlank()
        assertEquals(listOf("b"), condition.references().single().value())
        verify(actual).iterator()
    }

    companion object {
        private val exact = Rule({ containsExactly(it) }, { actual, expected -> actual == expected })
        private val anyOrder = Rule({ containsExactlyInAnyOrder(it) }, { actual, expected ->
            actual.groupingBy { it }.eachCount() == expected.groupingBy { it }.eachCount()
        })
        private val only = Rule({ containsOnly(it) }, { actual, expected -> actual.toSet() == expected.toSet() })
        private val subset = Rule({ subsetOf(it) }, { actual, expected -> expected.containsAll(actual) })
        private val notExact = Rule({ doesNotContainExactly(it) }, { actual, expected -> !exact.accepts(actual, expected) })
        private val notAnyOrder = Rule({ doesNotContainExactlyInAnyOrder(it) }, { actual, expected -> !anyOrder.accepts(actual, expected) })

        @JvmStatic
        fun positiveRules() = listOf(named("exact", exact), named("any order", anyOrder), named("only", only), named("subset", subset))

        @JvmStatic
        fun rules() = positiveRules() + listOf(named("not exact", notExact), named("not any order", notAnyOrder))

        private fun exactRules() = listOf("exact" to exact, "any order" to anyOrder, "not exact" to notExact, "not any order" to notAnyOrder)

        @JvmStatic
        fun exactMismatches() = exactRules().flatMap { (name, rule) ->
            listOf(0, 1, 3).map { size -> argumentSet("$name / size $size", rule.factory, name.startsWith("not"), size) }
        }

        @JvmStatic
        fun changingContents() = exactRules().flatMap { (name, rule) ->
            listOf(false, true).map { grow -> argumentSet("$name / ${if (grow) "grow" else "shrink"}", rule.factory, name.startsWith("not"), grow) }
        }
    }
}

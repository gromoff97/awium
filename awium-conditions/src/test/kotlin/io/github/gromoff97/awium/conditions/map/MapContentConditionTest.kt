package io.github.gromoff97.awium.conditions.map

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.MapConditions.containsExactlyEntries
import io.github.gromoff97.awium.conditions.MapConditions.doesNotContainExactlyEntries
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.conditions.support.ProbeContainers.ProbeEntry
import io.github.gromoff97.awium.conditions.support.ProbeContainers.once
import io.github.gromoff97.awium.conditions.support.ProbeContainers.sizeOnlyMap
import io.github.gromoff97.awium.conditions.support.Directional
import io.github.gromoff97.awium.conditions.support.ThrowingEquals
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.verify

class MapContentConditionTest {
    @ParameterizedTest
    @MethodSource("rules")
    fun `compares all entries regardless of iteration order`(factory: (Map<*, *>?) -> AwaitCondition, negated: Boolean) {
        val maps = listOf(emptyMap(), mapOf("a" to "1"), mapOf("a" to "2"),
            linkedMapOf("a" to "1", "b" to "2"), linkedMapOf("b" to "2", "a" to "1"),
            mapOf(null to "1"), mapOf("a" to null))
        for (expected in maps) for (actual in maps) {
            assertEquals((actual == expected) != negated, evaluate(factory(expected), actual) is Satisfied<*>,
                "actual=$actual, expected=$expected")
        }
    }

    @ParameterizedTest
    @MethodSource("rules")
    fun `a null map is a mismatch`(factory: (Map<*, *>?) -> AwaitCondition) {
        assertEquals("map was null", rejection(factory(emptyMap<String, String>()), null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("rules")
    fun `rejects a missing expected map`(factory: (Map<*, *>?) -> AwaitCondition) {
        assertThrows(NullPointerException::class.java) { factory(null) }
    }

    @ParameterizedTest
    @MethodSource("differentSizes")
    fun `different sizes need no entry traversal`(factory: (Map<*, *>?) -> AwaitCondition, negated: Boolean, expected: Map<String, String>) {
        val actual = sizeOnlyMap<String, String>()
        assertEquals(negated, evaluate(factory(expected), actual) is Satisfied<*>)
        verify(actual).size
    }

    @ParameterizedTest
    @MethodSource("changes")
    fun `detects changes between size and entry traversal`(factory: (Map<*, *>?) -> AwaitCondition, negated: Boolean, grow: Boolean) {
        val expected = mapOf("a" to "1", "b" to "2")
        val actual = object : LinkedHashMap<String, String>(expected) {
            val change = once { if (grow) put("c", "3") else remove("b") }
            override val entries: MutableSet<MutableMap.MutableEntry<String, String>>
                get() {
                    change.run()
                    return super.entries
                }
        }
        assertEquals(negated, evaluate(factory(expected), actual) is Satisfied<*>)
    }

    @Test
    fun `compares array keys and values by content`() {
        val actual = EntryMap(listOf(ProbeEntry(intArrayOf(1), intArrayOf(2))))
        assertSame(actual, result(evaluate(containsExactlyEntries(mapOf(intArrayOf(1) to intArrayOf(2))), actual)))
    }

    @Test
    fun `uses observation-first equality without entry hashing`() {
        val key = Directional(true)
        val expected = Directional(false)
        val actual = EntryMap(listOf(ProbeEntry(key, "value")))
        val expectedMap = EntryMap(listOf(ProbeEntry(expected, "value")))

        assertSame(actual, result(evaluate(containsExactlyEntries(expectedMap), actual)))
        assertEquals(1, key.equalsCalls)
        assertEquals(0, expected.equalsCalls)
    }

    @Test
    fun `negative checks preserve equality failures`() {
        val cause = IllegalStateException("equals failed")
        val actual = EntryMap(listOf(ProbeEntry("key", ThrowingEquals { throw cause })))
        assertSame(cause, thrown(doesNotContainExactlyEntries(mapOf("key" to ThrowingEquals {})), actual))
    }

    companion object {
        private val exact: (Map<*, *>?) -> AwaitCondition = { containsExactlyEntries(it) }
        private val notExact: (Map<*, *>?) -> AwaitCondition = { doesNotContainExactlyEntries(it) }

        @JvmStatic
        fun rules() = listOf(argumentSet("exact", exact, false), argumentSet("not exact", notExact, true))

        @JvmStatic
        fun differentSizes() = listOf("exact" to exact, "not exact" to notExact).flatMap { (name, factory) ->
            listOf(emptyMap(), mapOf("a" to "1", "b" to "2")).map { expected ->
                argumentSet("$name / size ${expected.size}", factory, name == "not exact", expected)
            }
        }

        @JvmStatic
        fun changes() = listOf("exact" to exact, "not exact" to notExact).flatMap { (name, factory) ->
            listOf(false, true).map { grow -> argumentSet("$name / ${if (grow) "grow" else "shrink"}", factory, name == "not exact", grow) }
        }
    }
}

package io.github.gromoff97.awium.conditions.membership

import io.github.gromoff97.awium.dsl.AwaitCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.contains
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContain
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.preservesInstance
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.map.MapConditions.containsKey
import io.github.gromoff97.awium.conditions.map.MapConditions.containsValue
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainKey
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainValue
import io.github.gromoff97.awium.conditions.support.ProbeContainers.membershipCollection
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.conditions.support.ProbeContainers.ProbeEntry
import io.github.gromoff97.awium.conditions.support.Directional
import io.github.gromoff97.awium.conditions.support.ThrowingEquals
import java.util.IdentityHashMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ContainsConditionTest {
    data class Subject(
        val include: (Any?) -> AwaitCondition,
        val exclude: (Any?) -> AwaitCondition,
        val container: (List<Any?>) -> Any
    )

    @ParameterizedTest
    @MethodSource("subjects")
    fun `inclusion and exclusion are complementary`(subject: Subject) {
        val actual = subject.container(listOf("a", "b"))
        preservesInstance(subject.include("b"), actual)
        assertThat(rejection(subject.exclude("b"), actual).mismatch()).isNotBlank()
        preservesInstance(subject.exclude("c"), actual)
        assertThat(rejection(subject.include("c"), actual).mismatch()).isNotBlank()
    }

    @ParameterizedTest
    @MethodSource("subjects")
    fun `null is an ordinary element`(subject: Subject) {
        val actual = subject.container(listOf(null))
        preservesInstance(subject.include(null), actual)
        rejection(subject.exclude(null), actual)
    }

    @ParameterizedTest
    @MethodSource("subjects")
    fun `a null container never matches`(subject: Subject) {
        assertThat(rejection(subject.include("a"), null).mismatch()).contains("was null")
        assertThat(rejection(subject.exclude("a"), null).mismatch()).contains("was null")
    }

    @ParameterizedTest
    @MethodSource("subjects")
    fun `compares primitive and nested arrays by content`(subject: Subject) {
        val actual = subject.container(listOf(intArrayOf(1, 2), arrayOf<Any>(intArrayOf(3))))
        preservesInstance(subject.include(intArrayOf(1, 2)), actual)
        preservesInstance(subject.include(arrayOf<Any>(intArrayOf(3))), actual)
        rejection(subject.exclude(intArrayOf(1, 2)), actual)
    }

    @ParameterizedTest
    @MethodSource("subjects")
    fun `compares from the observation without hashing`(subject: Subject) {
        val element = Directional(true)
        val expected = Directional(false)
        val actual = subject.container(listOf(element))
        preservesInstance(subject.include(expected), actual)
        assertEquals(1, element.equalsCalls)
        assertEquals(0, expected.equalsCalls)
    }

    @ParameterizedTest
    @MethodSource("subjects")
    fun `single operands remain caller-owned objects`(subject: Subject) {
        val expected = mutableListOf<String>()
        val element = mutableListOf<String>()
        val actual = subject.container(listOf(element))
        val condition = subject.include(expected)

        assertSame(expected, condition.references().single().value())
        preservesInstance(condition, actual)
        expected.add("changed")
        element.add("changed")
        preservesInstance(condition, actual)
    }

    @ParameterizedTest
    @MethodSource("subjects")
    fun `exclusion preserves equality failures`(subject: Subject) {
        val cause = IllegalStateException("equals failed")
        val actual = subject.container(listOf(ThrowingEquals { throw cause }))
        assertSame(cause, thrown(subject.exclude(ThrowingEquals {}), actual))
    }

    @Test
    fun `exclusion preserves iterator failures`() {
        val cause = IllegalStateException("iterator failed")
        val actual = membershipCollection(emptyList<String>())
        `when`(actual.iterator()).thenThrow(cause)
        assertSame(cause, thrown(doesNotContain("a"), actual))
    }

    @Test
    fun `reporting a mismatch does not traverse the collection again`() {
        val actual = membershipCollection(listOf("a", "b"))
        rejection(doesNotContain("a"), actual)
        verify(actual).iterator()
    }

    @Test
    fun `reporting a mismatch does not traverse the map again`() {
        val actual = EntryMap(listOf(ProbeEntry("a", "1")))
        rejection(doesNotContainKey("a"), actual)
        assertEquals(1, actual.entrySetCalls)
    }

    @Test
    fun `map exclusion preserves entry traversal failures`() {
        val cause = IllegalStateException("actual entries failed")
        val actual = EntryMap(listOf(ProbeEntry("a", "1")))
        actual.beforeEntrySet = Runnable { throw cause }
        assertSame(cause, thrown(doesNotContainKey("a"), actual))
    }

    companion object {
        @JvmStatic
        fun subjects() = listOf(
            named("collection element", Subject({ contains(it) }, { doesNotContain(it) }, { it })),
            named("map key", Subject({ containsKey(it) }, { doesNotContainKey(it) }, { values ->
                IdentityHashMap<Any?, Int>().apply { values.forEachIndexed { index, value -> put(value, index) } }
            })),
            named("map value", Subject({ containsValue(it) }, { doesNotContainValue(it) }, { values ->
                values.withIndex().associate { it.index to it.value }
            }))
        )
    }
}

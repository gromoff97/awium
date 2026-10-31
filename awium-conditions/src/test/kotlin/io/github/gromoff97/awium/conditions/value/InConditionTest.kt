package io.github.gromoff97.awium.conditions.value

import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition
import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.Conditions.`in`
import io.github.gromoff97.awium.conditions.Conditions.notIn
import io.github.gromoff97.awium.conditions.support.Directional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class InConditionTest {
    @ParameterizedTest
    @MethodSource("values")
    fun `membership compares complete values including null and arrays`(actual: Any?, expected: Any?) {
        assertSame(actual, result(evaluate(`in`(listOf(expected)), actual)))
        assertEquals("value was in the unexpected values", rejection(notIn(listOf(expected)), actual).mismatch())
    }

    @Test
    fun `missing candidates fail inclusion and satisfy exclusion`() {
        val actual = "ready"
        val candidates = listOf("pending", "failed")
        assertEquals("value was not in the expected values", rejection(`in`(candidates), actual).mismatch())
        assertSame(actual, result(evaluate(notIn(candidates), actual)))
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["ready"])
    fun `an empty candidate set excludes every value`(actual: String?) {
        rejection(`in`(emptyList<String>()), actual)
        assertSame(actual, result(evaluate(notIn(emptyList<String>()), actual)))
    }

    @ParameterizedTest
    @MethodSource("factories")
    fun `rejects a missing candidate set immediately`(create: (Iterable<Any?>?) -> ComparisonCondition<Any?>) {
        assertThrows(NullPointerException::class.java) { create(null) }
    }

    @Test
    fun `reads candidates once and freezes membership`() {
        val mutable = mutableListOf(null, "ready")
        val candidates: Iterable<String?> = mock(CALLS_REAL_METHODS)
        `when`(candidates.iterator()).thenAnswer { mutable.iterator() }
            .thenThrow(AssertionError("candidates read twice"))
        val condition = `in`(candidates)
        mutable.clear()

        assertEquals("ready", result(evaluate(condition, "ready")))
        assertNull(result(evaluate(condition, null)))
        assertEquals(listOf(null, "ready"), condition.references().single().value())
        verify(candidates).iterator()
    }

    @ParameterizedTest
    @MethodSource("factories")
    fun `array-backed candidates are copied`(create: (Iterable<Any?>?) -> ComparisonCondition<Any?>) {
        val expected = arrayOf<Any?>("before", null)
        val condition = create(expected.asList())
        val before = evaluate(condition, "before")
        val after = evaluate(condition, "after")
        expected[0] = "after"

        assertEquals(before, evaluate(condition, "before"))
        assertEquals(after, evaluate(condition, "after"))
        assertEquals(listOf("before", null), condition.references().single().value())
    }

    @Test
    fun `equality is invoked on the observation`() {
        val actual = Directional(true)
        val candidate = Directional(false)

        assertSame(actual, result(evaluate(`in`(listOf(candidate)), actual)))
        assertEquals(1, actual.equalsCalls)
        assertEquals(0, candidate.equalsCalls)
    }

    companion object {
        @JvmStatic
        fun values() = listOf(
            argumentSet("null", null, null),
            argumentSet("text", String(charArrayOf('a')), "a"),
            argumentSet("list", listOf("a"), listOf("a")),
            argumentSet("map", mapOf("a" to 1), mapOf("a" to 1)),
            argumentSet("int array", intArrayOf(1, 2), intArrayOf(1, 2)),
            argumentSet("object array", arrayOf("a", null), arrayOf("a", null)),
            argumentSet("nested array", arrayOf<Any>(intArrayOf(1)), arrayOf<Any>(intArrayOf(1)))
        )

        @JvmStatic
        fun factories(): List<Named<(Iterable<Any?>?) -> ComparisonCondition<Any?>>> = listOf(
            named("in", { `in`(it) }), named("not in", { notIn(it) })
        )
    }
}

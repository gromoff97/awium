package io.github.gromoff97.awium.conditions.diagnostics

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.contains
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsExactlyInAnyOrder
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsOnly
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContain
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainExactly
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainExactlyInAnyOrder
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.subsetOf
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.common.CommonConditions.between
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.greaterThan
import io.github.gromoff97.awium.conditions.common.CommonConditions.notEqualTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.notSameAs
import io.github.gromoff97.awium.conditions.common.CommonConditions.sameAs
import io.github.gromoff97.awium.conditions.common.CommonConditions.strictlyBetween
import io.github.gromoff97.awium.conditions.map.MapConditions.containsKey
import io.github.gromoff97.awium.conditions.map.MapConditions.containsOnlyKeys
import io.github.gromoff97.awium.conditions.map.MapConditions.containsValue
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainKey
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainValue
import io.github.gromoff97.awium.conditions.map.MapConditions.entryFor
import io.github.gromoff97.awium.conditions.map.MapConditions.hasEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.hasNoEntry
import io.github.gromoff97.awium.conditions.string.StringConditions
import io.github.gromoff97.awium.conditions.string.StringConditions.containsIgnoringCase
import io.github.gromoff97.awium.conditions.string.StringConditions.contentEquals
import io.github.gromoff97.awium.conditions.string.StringConditions.doesNotEndWith
import io.github.gromoff97.awium.conditions.string.StringConditions.doesNotMatchRegex
import io.github.gromoff97.awium.conditions.string.StringConditions.doesNotStartWith
import io.github.gromoff97.awium.conditions.string.StringConditions.equalToIgnoringCase
import io.github.gromoff97.awium.conditions.string.StringConditions.matchesRegex
import io.github.gromoff97.awium.conditions.string.StringConditions.notEqualToIgnoringCase
import io.github.gromoff97.awium.result.ConditionHistory
import java.util.TreeMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class ExpectedOperandsTest {
    @ParameterizedTest
    @MethodSource("operands")
    fun `a mismatch report retains its expected or forbidden operand`(factory: () -> AwaitCondition, actual: Any, operand: String) {
        val check = factory()
        rejection(check, actual)
        assertThat(ConditionHistory<Any>(check, TreeMap()).toString()).contains(operand)
    }

    @ParameterizedTest
    @MethodSource("singleValues")
    fun `an array operand remains one complete reference`(factory: (IntArray) -> AwaitCondition) {
        val operand = intArrayOf(1, 2)
        assertSame(operand, factory(operand).references().single().value())
    }

    companion object {
        @JvmStatic
        fun operands(): List<Arguments> {
            val values = listOf("a", "b")
            val needle = String(charArrayOf('n', 'e', 'e', 'd', 'l', 'e'))
            val expected = "Expected: \"needle\""
            val unexpected = "Unexpected: \"needle\""
            val array = intArrayOf(1, 2)
            return listOf(
                argumentSet("any order", { containsExactlyInAnyOrder(values) }, listOf("other"), "Expected: [a, b]"),
                argumentSet("only", { containsOnly(values) }, listOf("other"), "Expected: [a, b]"),
                argumentSet("subset", { subsetOf(values) }, listOf("other"), "Expected: [a, b]"),
                argumentSet("not exactly", { doesNotContainExactly(values) }, values, "Unexpected: [a, b]"),
                argumentSet("not exactly in any order", { doesNotContainExactlyInAnyOrder(values) }, values, "Unexpected: [a, b]"),
                argumentSet("missing entry", { hasEntry("key", "value") }, mapOf("other" to "value"), "Expected: key=value"),
                argumentSet("unexpected entry", { hasNoEntry("key", "value") }, mapOf("key" to "value"), "Unexpected: key=value"),
                argumentSet("missing key", { containsOnlyKeys(listOf("key")) }, mapOf("other" to "value"), "Expected: [key]"),
                argumentSet("entry selection", { entryFor("key") }, mapOf("other" to "value"), "Key: \"key\""),
                argumentSet("contains text", { StringConditions.contains(needle) }, "other", expected),
                argumentSet("contains ignoring case", { containsIgnoringCase(needle) }, "other", expected),
                argumentSet("starts with", { StringConditions.startsWith(needle) }, "other", expected),
                argumentSet("ends with", { StringConditions.endsWith(needle) }, "other", expected),
                argumentSet("content equals", { contentEquals(needle) }, "other", expected),
                argumentSet("equals ignoring case", { equalToIgnoringCase(needle) }, "other", expected),
                argumentSet("does not contain text", { StringConditions.doesNotContain(needle) }, needle, unexpected),
                argumentSet("does not start with", { doesNotStartWith(needle) }, needle, unexpected),
                argumentSet("does not end with", { doesNotEndWith(needle) }, needle, unexpected),
                argumentSet("not equal ignoring case", { notEqualToIgnoringCase(needle) }, needle, unexpected),
                argumentSet("regex", { matchesRegex("a.*z") }, "other", "Expected: a.*z"),
                argumentSet("negative regex", { doesNotMatchRegex("a.*z") }, "abz", "Unexpected: a.*z"),
                argumentSet("equality", { equalTo(needle) }, "other", expected),
                argumentSet("identity", { sameAs(needle) }, "other", expected),
                argumentSet("inequality", { notEqualTo(needle) }, needle, unexpected),
                argumentSet("different identity", { notSameAs(needle) }, needle, unexpected),
                argumentSet("inclusive range", { between(1, 5) }, 10, "Expected: [1, 5]"),
                argumentSet("exclusive range", { strictlyBetween(1, 5) }, 10, "Expected: [1, 5]"),
                argumentSet("greater than", { greaterThan(5) }, 0, "Expected: 5"),
                argumentSet("contains array", { contains(array) }, listOf(intArrayOf(9)), "Expected: [1, 2]"),
                argumentSet("does not contain array", { doesNotContain(array) }, listOf(array), "Unexpected: [1, 2]"),
            )
        }

        @JvmStatic
        fun singleValues() = listOf(
            argumentSet("contains", { value: IntArray -> contains(value) }),
            argumentSet("does not contain", { value: IntArray -> doesNotContain(value) }),
            argumentSet("contains key", { value: IntArray -> containsKey(value) }),
            argumentSet("does not contain key", { value: IntArray -> doesNotContainKey(value) }),
            argumentSet("contains value", { value: IntArray -> containsValue(value) }),
            argumentSet("does not contain value", { value: IntArray -> doesNotContainValue(value) })
        )
    }
}

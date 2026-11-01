package io.github.gromoff97.awium.conditions.collection

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsSequence
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsSubsequence
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainSequence
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainSubsequence
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.endsWith
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.startsWith
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.smallLists
import io.github.gromoff97.awium.result.AttemptResult.Satisfied
import java.util.Collections.indexOfSubList
import java.util.LinkedList
import java.util.SequencedCollection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class CollectionSequenceConditionTest {
    data class Rule(
        val factory: (Iterable<Any?>?) -> AwaitCondition,
        val accepts: (List<String?>, List<String?>) -> Boolean
    )

    @ParameterizedTest
    @MethodSource("rules")
    fun `ordered patterns respect gaps order duplicates and nulls`(rule: Rule) {
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
    fun `a null collection is a mismatch even for an empty pattern`(rule: Rule) {
        assertEquals("collection was null", rejection(rule.factory(emptyList()), null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("rules")
    fun `an empty pattern does not read the observed collection`(rule: Rule) {
        val actual: SequencedCollection<Any?> = mock()
        assertEquals(rule.accepts(emptyList(), emptyList()), evaluate(rule.factory(emptyList()), actual) is Satisfied<*>)
        verifyNoInteractions(actual)
    }

    @ParameterizedTest
    @MethodSource("shortCollections")
    fun `an impossible pattern reads the size once and no elements`(rule: Rule, size: Int) {
        var sizeReads = 0
        val observedSize = size
        val actual = object : AbstractList<String>() {
            override val size get() = observedSize.also { sizeReads++ }
            override fun get(index: Int): String = throw AssertionError("impossible pattern must not read elements")
        }
        val condition = rule.factory(listOf("a", "b", "c"))
        assertEquals(rule.accepts(List(size) { "a" }, listOf("a", "b", "c")), evaluate(condition, actual) is Satisfied<*>)
        assertEquals(1, sizeReads)
    }

    @ParameterizedTest
    @MethodSource("rules")
    fun `rejects a missing pattern immediately`(rule: Rule) {
        assertThrows(NullPointerException::class.java) { rule.factory(null) }
    }

    @ParameterizedTest
    @MethodSource("arrayPatterns")
    fun `ordered patterns compare array elements by content`(condition: AwaitCondition) {
        val elements = listOf(null, intArrayOf(1, 2), "gap", intArrayOf(3, 4))
        for (actual in listOf(LinkedList(elements), LinkedHashSet(elements))) {
            assertSame(actual, result(evaluate(condition, actual)))
        }
    }

    companion object {
        private val prefix = Rule({ startsWith(it) }, { actual, expected -> actual.take(expected.size) == expected })
        private val suffix = Rule({ endsWith(it) }, { actual, expected -> actual.takeLast(expected.size) == expected })
        private val sequence = Rule({ containsSequence(it) }, { actual, expected -> indexOfSubList(actual, expected) >= 0 })
        private val subsequence = Rule({ containsSubsequence(it) }, { actual, expected ->
            (0 until (1 shl actual.size)).any { mask -> actual.filterIndexed { index, _ -> mask and (1 shl index) != 0 } == expected }
        })

        @JvmStatic
        fun rules() = listOf(
            named("prefix", prefix), named("suffix", suffix), named("sequence", sequence), named("subsequence", subsequence),
            named("no sequence", Rule({ doesNotContainSequence(it) }, { actual, expected -> !sequence.accepts(actual, expected) })),
            named("no subsequence", Rule({ doesNotContainSubsequence(it) }, { actual, expected -> !subsequence.accepts(actual, expected) }))
        )

        @JvmStatic
        fun shortCollections() = rules().flatMap { rule ->
            (0..2).map { size -> argumentSet("${rule.name} / size $size", rule.payload, size) }
        }

        @JvmStatic
        fun arrayPatterns() = listOf(
            named("prefix", startsWith(listOf(null, intArrayOf(1, 2)))),
            named("suffix", endsWith(listOf("gap", intArrayOf(3, 4)))),
            named("sequence", containsSequence(listOf(intArrayOf(1, 2), "gap"))),
            named("subsequence", containsSubsequence(listOf(null, intArrayOf(3, 4))))
        )
    }
}

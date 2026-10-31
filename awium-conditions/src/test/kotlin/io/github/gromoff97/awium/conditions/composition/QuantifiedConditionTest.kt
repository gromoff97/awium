package io.github.gromoff97.awium.conditions.composition

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.Conditions.matches
import io.github.gromoff97.awium.conditions.MapConditions.hasEntry
import io.github.gromoff97.awium.conditions.MapConditions.hasNoEntry
import io.github.gromoff97.awium.conditions.MapConditions.allEntries
import io.github.gromoff97.awium.conditions.MapConditions.allKeys
import io.github.gromoff97.awium.conditions.MapConditions.allValues
import io.github.gromoff97.awium.conditions.MapConditions.anyKey
import io.github.gromoff97.awium.conditions.MapConditions.anyValue
import io.github.gromoff97.awium.conditions.MapConditions.noKey
import io.github.gromoff97.awium.conditions.MapConditions.noValue
import io.github.gromoff97.awium.conditions.MapConditions.singleEntry
import io.github.gromoff97.awium.conditions.ConditionAssertions.thrown
import io.github.gromoff97.awium.function.CheckedPredicate
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class QuantifiedConditionTest {
    @ParameterizedTest
    @MethodSource("missingEntries")
    fun `entry checks reject missing and mismatching entries`(condition: AwaitCondition, actual: Map<String, String>) {
        rejection(condition, actual)
    }

    @ParameterizedTest
    @MethodSource("entryChecks")
    fun `entry checks retain the supplied reason`(condition: AwaitCondition) {
        assertEquals("ready entry required", condition.explanation())
    }

    @ParameterizedTest
    @MethodSource("forbiddenEntries")
    fun `entry absence rejects a matching entry`(condition: AwaitCondition) {
        rejection(condition, mapOf("ready" to "0", "failed" to "1"))
    }

    @ParameterizedTest
    @MethodSource("mapMismatches")
    fun `map quantifiers reject a decisive mismatch`(condition: AwaitCondition) {
        rejection(condition, linkedMapOf("a" to 1, "b" to 2))
    }

    @ParameterizedTest
    @MethodSource("entryFailures")
    fun `entry callbacks preserve the original checked failure`(factory: (CheckedPredicate<Map.Entry<String, Int>>) -> AwaitCondition) {
        val cause = IOException("entry failed")
        val condition = factory(CheckedPredicate { throw cause })
        assertSame(cause, thrown(condition, mapOf("key" to 1)))
    }

    companion object {
        private val entries = listOf<Pair<String, AwaitCondition>>(
            "exact" to hasEntry("ready", "1").because("ready entry required"),
            "predicate" to hasEntry { entry: Map.Entry<String, String> -> entry.key == "ready" && entry.value == "1" }
                .because("ready entry required"),
            "nested" to hasEntry(matches { entry: Map.Entry<String, String> -> entry.key == "ready" && entry.value == "1" })
                .because("ready entry required"))

        @JvmStatic
        fun entryChecks() = entries.map { (name, condition) -> argumentSet(name, condition) }

        @JvmStatic
        fun missingEntries() = entries.flatMap { (name, condition) ->
            listOf(emptyMap(), mapOf("other" to "1"), mapOf("ready" to "0")).map { actual ->
                argumentSet("$name / $actual", condition, actual)
            }
        }

        @JvmStatic
        fun forbiddenEntries() = listOf(
            argumentSet("exact", hasNoEntry("failed", "1")),
            argumentSet("predicate", hasNoEntry { entry: Map.Entry<String, String> -> entry.key == "failed" && entry.value == "1" }),
            argumentSet("explained", hasNoEntry { entry: Map.Entry<String, String> -> entry.key == "failed" && entry.value == "1" }
                .because("failed entry must be absent")))

        @JvmStatic
        fun mapMismatches() = listOf(
            argumentSet("all entries", allEntries { _: String, value: Int -> value < 2 }),
            argumentSet("some entry", hasEntry { _: String, value: Int -> value > 2 }),
            argumentSet("no entry", hasNoEntry { _: String, value: Int -> value == 2 }),
            argumentSet("all keys", allKeys { key: String -> key == "a" }),
            argumentSet("any key", anyKey { key: String -> key == "missing" }),
            argumentSet("no key", noKey { key: String -> key == "a" }),
            argumentSet("all values", allValues { value: Int -> value < 2 }),
            argumentSet("any value", anyValue { value: Int -> value > 2 }),
            argumentSet("no value", noValue { value: Int -> value == 2 }))

        @JvmStatic
        fun entryFailures() = listOf(
            argumentSet("single entry", { predicate: CheckedPredicate<Map.Entry<String, Int>> -> singleEntry(predicate) }),
            argumentSet("all entries", { predicate: CheckedPredicate<Map.Entry<String, Int>> -> allEntries(predicate) }))
    }
}

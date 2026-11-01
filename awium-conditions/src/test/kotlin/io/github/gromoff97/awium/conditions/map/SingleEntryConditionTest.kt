package io.github.gromoff97.awium.conditions.map

import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.conditions.support.ProbeContainers.ProbeEntry
import io.github.gromoff97.awium.conditions.support.ProbeContainers.once
import io.github.gromoff97.awium.function.AwaitAttempt.bind
import io.github.gromoff97.awium.result.AttemptResult.Unsatisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class SingleEntryConditionTest {
    @Test
    fun `returns the only entry`() {
        val actual = mapOf("key" to 42)
        assertEquals(actual.entries.single(), result(evaluate(singleEntry, actual)))
    }

    @ParameterizedTest
    @MethodSource("invalidMaps")
    fun `reports why no single entry exists`(actual: Map<*, *>?, expected: String) {
        assertEquals(expected, rejection(singleEntry, actual).mismatch())
    }

    @Test
    fun `a map emptied before reading entries can match on the next probe`() {
        val entries = mutableListOf<Map.Entry<String, String>>(ProbeEntry("key", "old"))
        val actual = EntryMap(entries)
        actual.beforeEntrySet = once { entries.clear() }
        val execution = bind<Map<String, String>, Map.Entry<String, String>>(singleEntry)

        assertInstanceOf(Unsatisfied::class.java, execution.probe(actual))
        val ready = ProbeEntry("key", "ready")
        entries.add(ready)
        assertSame(ready, result(execution.probe(actual)))
    }

    companion object {
        @JvmStatic
        fun invalidMaps() = listOf(
            arguments(null, "map was null"),
            arguments(emptyMap<Any, Any>(), "map entries: collection size was 0"),
            arguments(mapOf("first" to 1, "second" to 2), "map entries: collection size was greater than 1")
        )
    }
}

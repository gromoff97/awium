package io.github.gromoff97.awium.conditions.contracts

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.AwaitCondition.InputCondition
import io.github.gromoff97.awium.conditions.CollectionConditions.all as allElements
import io.github.gromoff97.awium.conditions.CollectionConditions.any as anyElement
import io.github.gromoff97.awium.conditions.CollectionConditions.contains
import io.github.gromoff97.awium.conditions.CollectionConditions.containsExactly
import io.github.gromoff97.awium.conditions.CollectionConditions.containsExactlyInAnyOrder
import io.github.gromoff97.awium.conditions.CollectionConditions.containsOnly
import io.github.gromoff97.awium.conditions.CollectionConditions.containsSequence
import io.github.gromoff97.awium.conditions.CollectionConditions.containsSubsequence
import io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContain
import io.github.gromoff97.awium.conditions.CollectionConditions.elementAt
import io.github.gromoff97.awium.conditions.CollectionConditions.endsWith
import io.github.gromoff97.awium.conditions.CollectionConditions.firstElement
import io.github.gromoff97.awium.conditions.CollectionConditions.hasNoDuplicates
import io.github.gromoff97.awium.conditions.CollectionConditions.hasSize
import io.github.gromoff97.awium.conditions.CollectionConditions.lastElement
import io.github.gromoff97.awium.conditions.CollectionConditions.none as noElement
import io.github.gromoff97.awium.conditions.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.CollectionConditions.sorted
import io.github.gromoff97.awium.conditions.CollectionConditions.startsWith
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.Conditions.captured
import io.github.gromoff97.awium.conditions.Conditions.conditionPerWait
import io.github.gromoff97.awium.conditions.Conditions.instanceOf
import io.github.gromoff97.awium.conditions.Conditions.matches
import io.github.gromoff97.awium.conditions.MapConditions.allEntries
import io.github.gromoff97.awium.conditions.MapConditions.allKeys
import io.github.gromoff97.awium.conditions.MapConditions.allValues
import io.github.gromoff97.awium.conditions.MapConditions.containsAllEntries
import io.github.gromoff97.awium.conditions.MapConditions.containsExactlyEntries
import io.github.gromoff97.awium.conditions.MapConditions.containsKey
import io.github.gromoff97.awium.conditions.MapConditions.containsOnlyKeys
import io.github.gromoff97.awium.conditions.MapConditions.containsValue
import io.github.gromoff97.awium.conditions.MapConditions.entryFor
import io.github.gromoff97.awium.conditions.MapConditions.hasEntry
import io.github.gromoff97.awium.conditions.MapConditions.hasNoEntry
import io.github.gromoff97.awium.conditions.MapConditions.hasSize as mapHasSize
import io.github.gromoff97.awium.conditions.MapConditions.singleEntry
import io.github.gromoff97.awium.conditions.MapConditions.valueFor
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.function.CheckedPredicate
import io.github.gromoff97.awium.result.ConditionResult.satisfied
import java.lang.Thread.currentThread
import java.lang.Thread.interrupted
import java.util.Objects.checkIndex
import java.util.AbstractMap.SimpleImmutableEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class ContainerInterruptionTest {
    @AfterEach
    fun clearInterruptFlag() { interrupted() }

    @ParameterizedTest
    @MethodSource("quantifiers")
    fun `an interrupted traversal skips the nested factory`(quantify: (InputCondition<String>) -> AwaitCondition) {
        val actual = object : AbstractList<String>() {
            override val size = 1
            override fun get(index: Int): String {
                checkIndex(index, size)
                currentThread().interrupt()
                return "ready"
            }
            override fun toString() = "interrupting collection"
        }
        var factories = 0
        val nested = conditionPerWait<String, String>("nested check") { factories++; AwaitAttempt { satisfied(it) } }
        assertInstanceOf(InterruptedException::class.java, thrown(quantify(nested), actual))
        assertTrue(currentThread().isInterrupted)
        assertEquals(0, factories)
    }

    @ParameterizedTest
    @MethodSource("getterFailures")
    fun `a getter failure keeps its cause when it also interrupts`(cause: Throwable) {
        val actual = object : ArrayList<String>(listOf("ready")) {
            override fun iterator(): MutableIterator<String> { currentThread().interrupt(); throw cause }
        }
        assertSame(cause, thrown(singleElement, actual))
        assertTrue(currentThread().isInterrupted)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `an interrupted extraction skips the nested factory`(sequence: Boolean) {
        var factories = 0
        val nested = conditionPerWait<String, String>("nested check") { factories++; AwaitAttempt { satisfied(it) } }
        val check = valueFor("key", nested).because("value must be readable")
        val actual = SignallingMap("getValue")
        assertInstanceOf(InterruptedException::class.java, thrown(if (sequence) captured(check, check) else check, actual))
        assertEquals(0, factories)
        assertEquals("key", check.references().first().value())
        assertEquals("value must be readable", check.explanation())
        assertTrue(currentThread().isInterrupted)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `an interrupted narrowing keeps the condition definition`(sequence: Boolean) {
        val check = valueFor("key", instanceOf(String::class.java)).because("value must be readable")
        assertInstanceOf(InterruptedException::class.java, thrown(if (sequence) captured(check, check) else check, SignallingMap("getValue")))
        assertEquals("key", check.references().first().value())
        assertEquals("value must be readable", check.explanation())
        assertTrue(currentThread().isInterrupted)
    }

    @Test
    fun `sorting checks interruption after comparing the extracted pair`() {
        val actual = SignallingList("secondNext")
        var comparisons = 0
        assertInterrupted(sorted<String> { _, _ -> comparisons++; 0 }, actual, "next", actual.calls)
        assertEquals(1, comparisons)
    }

    @ParameterizedTest
    @MethodSource("collectionBoundaries")
    fun `collection traversal stops at an interruption boundary`(factory: () -> AwaitCondition, boundary: String) {
        val actual = SignallingList(boundary)
        assertInterrupted(factory(), actual, boundary, actual.calls)
    }

    @ParameterizedTest
    @MethodSource("mapBoundaries")
    fun `map traversal stops at an interruption boundary`(factory: () -> AwaitCondition, boundary: String) {
        val actual = SignallingMap(boundary)
        assertInterrupted(factory(), actual, boundary, actual.calls)
    }

    private fun assertInterrupted(check: AwaitCondition, actual: Any, boundary: String, calls: List<String>) {
        assertInstanceOf(InterruptedException::class.java, thrown(check, actual))
        assertThat(calls).contains(boundary)
        assertTrue(currentThread().isInterrupted)
    }

    private class SignallingMap(private val boundary: String) : AbstractMap<String, String>() {
        val calls = mutableListOf<String>()
        private fun called(method: String) { calls += method; if (method == boundary) currentThread().interrupt() }
        override val size: Int get() { called("size"); return 1 }
        override val entries: Set<Map.Entry<String, String>>
            get() = view("entrySet", object : SimpleImmutableEntry<String, String>("key", "ready") {
                override val key: String get() { called("getKey"); return super.key }
                override val value: String get() { called("getValue"); return super.value }
            })
        override val keys: Set<String> get() = view("keySet", "key")
        override val values: Collection<String> get() = view("values", "ready")
        private fun <VALUE> view(method: String, value: VALUE): Set<VALUE> {
            called(method)
            return object : AbstractSet<VALUE>() {
                override val size = 1
                override fun iterator(): Iterator<VALUE> {
                    called("iterator")
                    return object : Iterator<VALUE> {
                        var consumed = false
                        override fun hasNext(): Boolean { called("hasNext"); return !consumed }
                        override fun next(): VALUE { called("next"); consumed = true; return value }
                    }
                }
            }
        }
        override fun toString() = "signalling map"
    }

    private class SignallingList(private val boundary: String) : ArrayList<String>(listOf("ready", "other")) {
        val calls = mutableListOf<String>()
        private fun called(method: String) { calls += method; if (method == boundary) currentThread().interrupt() }
        override val size: Int get() { called("size"); return super.size }
        override fun reversed(): MutableList<String> { called("reversed"); return this }
        override fun iterator(): MutableIterator<String> {
            called("iterator")
            val delegate = super.iterator()
            return object : MutableIterator<String> {
                var reads = 0
                override fun hasNext(): Boolean { called("hasNext"); return delegate.hasNext() }
                override fun next(): String {
                    called("next")
                    if (++reads == 2) called("secondNext")
                    return delegate.next()
                }
                override fun remove() = delegate.remove()
            }
        }
        override fun toString() = "signalling list"
    }

    companion object {
        @JvmStatic
        fun quantifiers() = listOf(
            argumentSet("all", { check: InputCondition<String> -> allElements(check) }),
            argumentSet("any", { check: InputCondition<String> -> anyElement(check) }),
            argumentSet("none", { check: InputCondition<String> -> noElement(check) })
        )

        @JvmStatic
        fun getterFailures() = listOf(IllegalStateException("getter failed"), InternalError("fatal getter"))

        @JvmStatic
        fun collectionBoundaries(): List<Arguments> {
            val factories: Map<String, () -> AwaitCondition> = linkedMapOf(
                "all" to { allElements<String> { true } }, "any" to { anyElement<String> { true } }, "none" to { noElement<String> { false } },
                "first matching element" to { firstElement(CheckedPredicate<String> { true }) }, "first element" to { firstElement },
                "last element" to { lastElement }, "last matching element" to { lastElement(CheckedPredicate<String> { true }) },
                "element at index" to { elementAt(0) }, "single matching element" to { singleElement<String>(matches<String> { true }) },
                "contains" to { contains("ready") }, "does not contain" to { doesNotContain("absent") },
                "contains exactly" to { containsExactly(listOf("ready", "other")) },
                "contains exactly in any order" to { containsExactlyInAnyOrder(listOf("other", "ready")) },
                "contains only" to { containsOnly(listOf("ready", "other")) },
                "contains subsequence" to { containsSubsequence(listOf("ready")) },
                "has no duplicates" to { hasNoDuplicates }, "sorted" to { sorted<String> { _, _ -> 0 } }
            )
            val selections: Map<String, Pair<() -> AwaitCondition, String>> = linkedMapOf(
                "last element" to ({ lastElement } to "reversed"),
                "last matching element" to ({ lastElement(CheckedPredicate<String> { true }) } to "reversed"),
                "contains sequence" to ({ containsSequence(listOf("ready")) } to "size"),
                "starts with" to ({ startsWith(listOf("ready")) } to "size"),
                "ends with" to ({ endsWith(listOf("other")) } to "size"),
                "has size" to ({ hasSize(2) } to "size")
            )
            return factories.flatMap { (name, factory) ->
                listOf("iterator", "hasNext", "next").map { boundary -> argumentSet("$name / $boundary", factory, boundary) }
            } + selections.map { (name, scenario) -> argumentSet("$name / ${scenario.second}", scenario.first, scenario.second) }
        }

        @JvmStatic
        fun mapBoundaries(): List<Arguments> {
            val traversal = listOf("entrySet", "iterator", "hasNext", "next")
            val keyRead = traversal + "getKey"
            val valueRead = keyRead + "getValue"
            val lookups: Map<String, Pair<() -> AwaitCondition, List<String>>> = linkedMapOf(
                "single entry" to ({ singleEntry } to traversal),
                "single matching entry" to ({ singleEntry<String, String>(matches<Any> { true }) } to traversal),
                "value for" to ({ valueFor("key") } to valueRead), "entry for" to ({ entryFor("key") } to keyRead),
                "all entries" to ({ allEntries<String, String> { _, _ -> true } } to valueRead),
                "matching entry" to ({ hasEntry<String, String> { _, _ -> true } } to valueRead),
                "no matching entry" to ({ hasNoEntry<String, String> { _, _ -> false } } to valueRead),
                "has entry" to ({ hasEntry("key", "ready") } to valueRead),
                "contains all entries" to ({ containsAllEntries(mapOf("key" to "ready")) } to valueRead),
                "contains exactly entries" to ({ containsExactlyEntries(mapOf("key" to "ready")) } to valueRead)
            )
            val views: Map<String, Pair<() -> AwaitCondition, String>> = linkedMapOf(
                "contains exactly entries" to ({ containsExactlyEntries(mapOf("key" to "ready")) } to "size"),
                "has size" to ({ mapHasSize(1) } to "size"), "all keys" to ({ allKeys<String> { true } } to "keySet"),
                "contains key" to ({ containsKey("key") } to "keySet"),
                "contains only keys" to ({ containsOnlyKeys(listOf("key")) } to "keySet"),
                "all values" to ({ allValues<String> { true } } to "values"),
                "contains value" to ({ containsValue("ready") } to "values")
            )
            return lookups.flatMap { (name, scenario) ->
                scenario.second.map { boundary -> argumentSet("$name / $boundary", scenario.first, boundary) }
            } + views.map { (name, scenario) -> argumentSet("$name / ${scenario.second}", scenario.first, scenario.second) }
        }
    }
}

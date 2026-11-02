package io.github.gromoff97.awium.conditions.contracts

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsAll
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.preservesInstance
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.common.CommonConditions
import io.github.gromoff97.awium.conditions.map.MapConditions
import io.github.gromoff97.awium.conditions.map.MapConditions.containsAllEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.containsExactlyEntries
import io.github.gromoff97.awium.conditions.string.StringConditions
import io.github.gromoff97.awium.conditions.support.Directional
import java.lang.Thread.currentThread
import java.util.IdentityHashMap
import java.util.Spliterator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class ExpectedSnapshotsTest {
    @ParameterizedTest
    @MethodSource("snapshots")
    fun `reads expected elements once and freezes their order duplicates and nulls`(
        factory: (Iterable<String?>) -> AwaitCondition, expected: List<String?>, actual: Any, other: Any, arrayBacked: Boolean
    ) {
        val array = expected.toTypedArray()
        val mutable = expected.toMutableList()
        val values = if (arrayBacked) array.asList() else mutable
        var reads = 0
        val input = Iterable { assertEquals(1, ++reads, "expected values were read twice"); values.iterator() }
        val check = factory(input)
        val before = listOf(evaluate(check, actual), evaluate(check, other))
        if (arrayBacked) array[0] = "new" else { mutable.clear(); mutable += "new" }

        assertEquals(before, listOf(evaluate(check, actual), evaluate(check, other)))
        assertEquals(expected, check.references().first().value())
        assertEquals(1, reads)
        val updated = factory(values)
        assertNotEquals(before, listOf(evaluate(updated, actual), evaluate(updated, other)))
    }

    @ParameterizedTest
    @MethodSource("splittableFactories")
    fun `reads a splittable expectation on the caller thread`(factory: (Iterable<String?>) -> AwaitCondition) {
        val caller = currentThread()
        val elements = (0 until 2048).map(Int::toString)
        var reads = 0
        val expected = object : Iterable<String> {
            override fun iterator() = elements.iterator()
            override fun spliterator(): Spliterator<String> = elements.parallelStream().map {
                assertSame(caller, currentThread())
                reads++
                it
            }.spliterator()
        }
        val check = factory(expected)
        assertEquals(elements.size, reads)
        assertEquals(elements, check.references().first().value())
    }

    @Test
    fun `validation does not ask the original container whether it is empty`() {
        val expected = object : ArrayList<String>(listOf("before")) {
            override fun isEmpty(): Boolean = throw AssertionError("original isEmpty called")
        }
        val check = containsAll(expected)
        expected.clear()
        assertEquals(listOf("before"), check.references().first().value())
        rejection(check, listOf("unrelated"))
        rejection(check, null)
    }

    @ParameterizedTest
    @MethodSource("iterableFactories")
    fun `an expectation emptied before traversal stays empty`(factory: (Iterable<String?>) -> AwaitCondition) {
        val expected = object : ConcurrentLinkedQueue<String>() {
            override fun spliterator(): Spliterator<String> { clear(); return super.spliterator() }
        }
        expected += "before"
        val check = factory(expected)
        expected += "after"
        assertEquals(emptyList<String>(), check.references().first().value())
    }

    @ParameterizedTest
    @MethodSource("mapFactories")
    fun `map expectations freeze empty entries even if the map changes later`(factory: (Map<String, String>) -> AwaitCondition) {
        val expected = object : ConcurrentHashMap<String, String>() {
            override val entries: MutableSet<MutableMap.MutableEntry<String, String>>
                get() { clear(); return super.entries }
        }
        expected["key"] = "before"
        val check = factory(expected)
        expected["key"] = "after"
        assertEquals(emptyList<String>(), check.references().first().value())
    }

    @ParameterizedTest
    @MethodSource("mapFactories")
    fun `map snapshots freeze entry values and nulls across mutation and clearing`(factory: (Map<String?, String?>) -> AwaitCondition) {
        val expected = linkedMapOf<String?, String?>("a" to "before", null to null)
        val original = expected.toMap()
        val changed = mapOf("a" to "changed")
        val check = factory(expected)
        val before = listOf(evaluate(check, original), evaluate(check, changed))
        expected.entries.first().setValue("changed")
        expected.remove(null)
        assertEquals(before, listOf(evaluate(check, original), evaluate(check, changed)))
        expected.clear()
        assertEquals(before, listOf(evaluate(check, original), evaluate(check, changed)))
    }

    @Test
    fun `map snapshots retain distinct keys that compare equal`() {
        val expected = IdentityHashMap<String, String>()
        expected[String(charArrayOf('k', 'e', 'y'))] = "first"
        expected[String(charArrayOf('k', 'e', 'y'))] = "second"
        val actual = IdentityHashMap(expected)
        val check = containsExactlyEntries(expected)
        expected.clear()
        assertEquals(2, actual.size)
        preservesInstance(check, actual)
    }

    @Test
    fun `copying expected entries calls neither key hashing nor equality`() {
        val key = Directional(false)
        val expected = IdentityHashMap<Directional, String>()
        expected[key] = "value"
        val check = containsExactlyEntries(expected)
        assertEquals(0, key.equalsCalls)
        preservesInstance(check, expected)
    }

    @Test
    fun `collection snapshots retain element identity in a read only container`() {
        val element = StringBuilder("before")
        val expected = mutableListOf(element)
        val check = containsAll(expected)
        val snapshot = check.references().single().value() as MutableList<*>
        expected.clear()
        element.append(" changed")
        assertSame(element, snapshot.single())
        assertThrows<UnsupportedOperationException> { snapshot.clear() }
        val actual = listOf(element)
        preservesInstance(check, actual)
    }

    @Test
    fun `text expectation references are read only`() {
        val snapshot = StringConditions.containsAll(listOf("before")).references().single().value() as MutableList<*>
        assertThrows<UnsupportedOperationException> { snapshot.clear() }
    }

    @Test
    fun `map diagnostics preserve order and expose immutable entries`() {
        val expected = linkedMapOf("b" to "before", "a" to null)
        val snapshot = containsAllEntries(expected).references().single().value() as MutableList<*>
        expected["b"] = "after"
        expected.remove("a")
        assertEquals("[b=before, a=null]", snapshot.toString())
        assertThrows<UnsupportedOperationException> { snapshot.clear() }
        @Suppress("UNCHECKED_CAST")
        val first = snapshot.first() as MutableMap.MutableEntry<String, String?>
        assertThrows<UnsupportedOperationException> { first.setValue(null) }
    }

    companion object {
        private val collections: Map<String, (Iterable<String?>) -> AwaitCondition> = linkedMapOf(
            "contains all" to CollectionConditions::containsAll, "contains any" to CollectionConditions::containsAny,
            "contains none" to CollectionConditions::doesNotContainAny, "does not contain all" to CollectionConditions::doesNotContainAll,
            "exactly" to CollectionConditions::containsExactly, "not exactly" to CollectionConditions::doesNotContainExactly,
            "any order" to CollectionConditions::containsExactlyInAnyOrder, "not any order" to CollectionConditions::doesNotContainExactlyInAnyOrder,
            "only" to CollectionConditions::containsOnly, "subset" to CollectionConditions::subsetOf,
            "starts with" to CollectionConditions::startsWith, "ends with" to CollectionConditions::endsWith,
            "sequence" to CollectionConditions::containsSequence, "no sequence" to CollectionConditions::doesNotContainSequence,
            "subsequence" to CollectionConditions::containsSubsequence, "no subsequence" to CollectionConditions::doesNotContainSubsequence
        )
        private val mapViews: Map<String, (Iterable<String?>) -> AwaitCondition> = linkedMapOf(
            "all keys" to MapConditions::containsAllKeys, "no keys" to MapConditions::doesNotContainAnyKeys,
            "only keys" to MapConditions::containsOnlyKeys, "all values" to MapConditions::containsAllValues,
            "no values" to MapConditions::doesNotContainAnyValues
        )
        private val text: Map<String, (Iterable<String?>) -> AwaitCondition> = linkedMapOf(
            "all fragments" to StringConditions::containsAll, "no fragments" to StringConditions::doesNotContainAny
        )
        private val candidates: Map<String, (Iterable<String?>) -> AwaitCondition> = linkedMapOf(
            "in" to CommonConditions::`in`, "not in" to CommonConditions::notIn
        )
        private val entries: Map<String, (Map<String?, String?>) -> AwaitCondition> = linkedMapOf(
            "all entries" to MapConditions::containsAllEntries, "no entries" to MapConditions::doesNotContainAnyEntries,
            "any entries" to MapConditions::containsAnyEntries, "not all entries" to MapConditions::doesNotContainAllEntries,
            "exact entries" to MapConditions::containsExactlyEntries, "not exact entries" to MapConditions::doesNotContainExactlyEntries
        )

        @JvmStatic
        fun snapshots() = listOf(false, true).flatMap { arrayBacked ->
            collections.map { (name, factory) -> argumentSet("$name / array=$arrayBacked", factory,
                listOf("a", null, "a"), listOf("a", null, "a"), listOf("new"), arrayBacked) } +
            mapViews.map { (name, factory) -> argumentSet("$name / array=$arrayBacked", factory,
                listOf("a", null), linkedMapOf("a" to "a", null to null), mapOf("new" to "new"), arrayBacked) } +
            text.map { (name, factory) -> argumentSet("$name / array=$arrayBacked", factory,
                listOf("a", "b"), StringBuilder("ab"), StringBuilder("new"), arrayBacked) } +
            candidates.map { (name, factory) -> argumentSet("$name / array=$arrayBacked", factory,
                listOf("ready", null), "ready", "new", arrayBacked) }
        }

        @JvmStatic
        fun iterableFactories() = (collections + mapViews + text + candidates).map { (name, factory) -> argumentSet(name, factory) }

        @JvmStatic
        fun mapFactories() = entries.map { (name, factory) -> argumentSet(name, factory) }

        @JvmStatic
        fun splittableFactories() = listOf(
            argumentSet("collection", collections.getValue("exactly")), argumentSet("map", mapViews.getValue("all keys")),
            argumentSet("membership", candidates.getValue("in")), argumentSet("text inclusion", text.getValue("all fragments")),
            argumentSet("text exclusion", text.getValue("no fragments"))
        )
    }
}

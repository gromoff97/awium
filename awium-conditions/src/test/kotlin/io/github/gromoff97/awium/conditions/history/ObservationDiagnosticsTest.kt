package io.github.gromoff97.awium.conditions.history

import io.github.gromoff97.awium.Await.tryAwait
import io.github.gromoff97.awium.Await.CollectionSource
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.all
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.any
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsAll
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainAny
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.hasNoDuplicates
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.attempts
import io.github.gromoff97.awium.conditions.Conditions.exactlyInstanceOf
import io.github.gromoff97.awium.conditions.Conditions.instanceOf
import io.github.gromoff97.awium.conditions.map.MapConditions.containsAllEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.containsAllKeys
import io.github.gromoff97.awium.conditions.map.MapConditions.containsAllValues
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAnyEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAnyKeys
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAnyValues
import io.github.gromoff97.awium.conditions.map.MapConditions.noValue
import io.github.gromoff97.awium.conditions.support.ProbeContainers.membershipCollection
import io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation
import io.github.gromoff97.awium.result.AwaitResult
import java.time.Duration.ofNanos
import java.util.concurrent.Callable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class ObservationDiagnosticsTest {
    private var equalityChecks = 0

    @ParameterizedTest
    @MethodSource("typeChecks")
    fun `type diagnostics distinguish observations with the same text`(check: Condition<Any?, String>) {
        val source = listOf(null, null, StringBuilder("pending"), StringBuilder("pending"), StringBuffer("pending"), "ready").iterator()
        val result = tryAwait(Callable { source.next() }).every(ofNanos(1)).until(check)

        assertEquals("ready", result.orThrow())
        assertEquals(listOf(2L, 3L, 4L, 5L, 6L), result.conditions().single().attempts().keys.toList())
        assertEquals(listOf("value was null", "value type was java.lang.StringBuilder",
            "value type was java.lang.StringBuilder", "value type was java.lang.StringBuffer"), mismatches(result))
    }

    @Test
    fun `duplicate positions survive later mutations without repeating comparisons`() {
        val values = arrayListOf(Element(1), Element(1), Element(2))
        val source = listOf<() -> ArrayList<Element>?>(
            { null }, { values }, change(values) { values[1] = Element(2) },
            change(values) { values[2] = Element(3) }).iterator()
        val result = tryAwait(Callable { source.next()() }).every(ofNanos(1)).until(hasNoDuplicates)

        assertSame(values, result.orThrow())
        assertEquals(7, equalityChecks)
        assertEquals(listOf("collection was null", "duplicate collection element at index 1",
            "duplicate collection element at index 2"), mismatches(result))
    }

    @Test
    fun `all retains progress through a mutable collection without rechecking elements`() {
        val values = arrayListOf(false, false, false)
        val source = (listOf({ values }) + (0..2).map { index -> change(values) { values[index] = true } }).iterator()
        var calls = 0
        val result = tryAwait(Callable { source.next()() }).every(ofNanos(1)).until(all { value: Boolean -> calls++; value })

        assertSame(values, result.orThrow())
        assertEquals(9, calls)
        assertEquals((1..3).map { "not all collection elements matched (checked $it): value did not match" }, mismatches(result))
    }

    @Test
    fun `any retains the number of checked elements including an empty observation`() {
        val values = arrayListOf<Boolean>()
        val source = (listOf({ values }) + listOf(false, false, true).map { value -> change(values) { values += value } }).iterator()
        var calls = 0
        val result = tryAwait(Callable { source.next()() }).every(ofNanos(1)).until(any { value: Boolean -> calls++; value })

        assertSame(values, result.orThrow())
        assertEquals(6, calls)
        assertEquals(listOf("no collection elements matched (checked 0)",
            "no collection elements matched (checked 1): value did not match",
            "no collection elements matched (checked 2): value did not match"), mismatches(result))
    }

    @Test
    fun `map absence retains the matching position in iteration order`() {
        val values = linkedMapOf("first" to true, "second" to true, "third" to true)
        val source = (listOf({ values }) + values.keys.map { key -> change(values) { values[key] = false } }).iterator()
        var calls = 0
        val result = tryAwait(Callable { source.next()() }).every(ofNanos(1)).until(noValue { value: Boolean -> calls++; value })

        assertSame(values, result.orThrow())
        assertEquals(9, calls)
        assertEquals((1..3).map { "some map values matched (checked $it)" }, mismatches(result))
    }

    @Test
    fun `membership retains missing positions without extra comparisons`() {
        val values = arrayListOf<Element>()
        val source = listOf<() -> ArrayList<Element>?>(
            { null }, { values }, change(values) { values += Element(1) }, { values },
            change(values) { values += Element(2) }, change(values) { values += Element(3) }).iterator()
        val result = tryAwait(Callable { source.next()() }).every(ofNanos(1))
            .until(containsAll(listOf(Element(1), Element(2), Element(3))))

        assertSame(values, result.orThrow())
        assertEquals(15, equalityChecks)
        assertEquals(listOf(1L, 2L, 4L, 5L, 6L), result.conditions().single().attempts().keys.toList())
        assertEquals(listOf("collection was null") + (0..2).map {
            "collection did not contain all expected elements; first missing at expected index $it"
        }, mismatches(result))
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("requiredMapParts")
    fun `map membership retains missing key value and entry positions`(elements: String, check: PreservingCondition<in Map<String, String>>) {
        val values = linkedMapOf<String, String>()
        val source = listOf({ values }, change(values) { values["first"] = "one" }, change(values) { values["second"] = "two" }).iterator()
        val result = tryAwait(Callable { source.next()() }).every(ofNanos(1)).until(check)

        assertSame(values, result.orThrow())
        assertEquals((0..1).map { "map did not contain all expected $elements; first missing at expected index $it" }, mismatches(result))
    }

    @Test
    fun `absence retains forbidden positions without extra traversal`() {
        val forbidden = listOf(1, 2, 3)
        val values = forbidden.toMutableList()
        val actual = membershipCollection(values)
        val source = listOf<() -> MutableCollection<Int>?>(
            { null }, { actual }, change(actual) { values.removeFirst() }, { actual },
            change(actual) { values.removeFirst() }, change(actual) { values.removeFirst() }).iterator()
        val result = tryAwait(CollectionSource<MutableCollection<Int>> { source.next()() }).every(ofNanos(1)).until(doesNotContainAny(forbidden))

        assertSame(actual, result.orThrow())
        verify(actual, times(11)).iterator()
        assertEquals(listOf(1L, 2L, 4L, 5L, 6L), result.conditions().single().attempts().keys.toList())
        assertEquals(listOf("collection was null") + (0..2).map {
            "collection contained unexpected elements; first at unexpected index $it"
        }, mismatches(result))
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("forbiddenMapParts")
    fun `map absence retains forbidden key value and entry positions`(elements: String, check: PreservingCondition<in Map<String, String>>) {
        val values = linkedMapOf("first" to "one", "second" to "two")
        val source = listOf({ values }, change(values) { values.remove("first") }, change(values) { values.remove("second") }).iterator()
        val result = tryAwait(Callable { source.next()() }).every(ofNanos(1)).until(check)

        assertSame(values, result.orThrow())
        assertEquals((0..1).map { "map contained unexpected $elements; first at unexpected index $it" }, mismatches(result))
    }

    private fun <ACTUAL> change(actual: ACTUAL, mutate: () -> Unit): () -> ACTUAL = { mutate(); actual }

    private fun mismatches(result: AwaitResult<*, *>) =
        attempts(result).filterIsInstance<UnsatisfiedObservation<*>>().map { it.mismatch() }

    private inner class Element(val number: Int) {
        override fun equals(other: Any?): Boolean { equalityChecks++; return other is Element && number == other.number }
        override fun hashCode() = number
    }

    companion object {
        @JvmStatic
        fun typeChecks() = listOf(named("instanceOf", instanceOf(String::class.java)), named("exactlyInstanceOf", exactlyInstanceOf(String::class.java)))

        @JvmStatic
        fun requiredMapParts() = linkedMapOf("first" to "one", "second" to "two").let { expected -> listOf(
            arguments("keys", containsAllKeys(expected.keys)), arguments("values", containsAllValues(expected.values)),
            arguments("entries", containsAllEntries(expected))) }

        @JvmStatic
        fun forbiddenMapParts() = linkedMapOf("first" to "one", "second" to "two").let { forbidden -> listOf(
            arguments("keys", doesNotContainAnyKeys(forbidden.keys)), arguments("values", doesNotContainAnyValues(forbidden.values)),
            arguments("entries", doesNotContainAnyEntries(forbidden))) }
    }
}

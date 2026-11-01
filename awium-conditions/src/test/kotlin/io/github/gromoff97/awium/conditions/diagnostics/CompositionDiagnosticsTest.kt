package io.github.gromoff97.awium.conditions.diagnostics

import io.github.gromoff97.awium.Await.tryAwait
import io.github.gromoff97.awium.Await.MapSource
import io.github.gromoff97.awium.Await.OptionalSource
import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.AwaitCondition.Reference
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.all
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.any
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.none
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.attempts
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.failure
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.uncontrolled
import io.github.gromoff97.awium.conditions.common.CommonConditions.captured
import io.github.gromoff97.awium.conditions.common.CommonConditions.condition
import io.github.gromoff97.awium.conditions.common.CommonConditions.conditionPerWait
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.instanceOf
import io.github.gromoff97.awium.conditions.common.CommonConditions.isNotNull
import io.github.gromoff97.awium.conditions.map.MapConditions.allEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.allKeys
import io.github.gromoff97.awium.conditions.map.MapConditions.allValues
import io.github.gromoff97.awium.conditions.map.MapConditions.anyKey
import io.github.gromoff97.awium.conditions.map.MapConditions.anyValue
import io.github.gromoff97.awium.conditions.map.MapConditions.hasEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.hasNoEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.noKey
import io.github.gromoff97.awium.conditions.map.MapConditions.noValue
import io.github.gromoff97.awium.conditions.map.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.execution.ConditionExecution.start
import io.github.gromoff97.awium.result.Attempt.Operation.CONDITION
import io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED
import io.github.gromoff97.awium.result.AttemptResult.Unsatisfied
import io.github.gromoff97.awium.result.AttemptResult.satisfied
import io.github.gromoff97.awium.result.AttemptResult.unsatisfied
import java.time.Duration.ofNanos
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.Optional
import java.util.concurrent.Callable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class CompositionDiagnosticsTest {
    @ParameterizedTest
    @ValueSource(strings = ["billing", "shipping"])
    fun `a map selection retains its key and nested expectation`(key: String) {
        val check = valueFor(key, equalTo("ready"))
        rejection(check, mapOf("billing" to "pending", "shipping" to "pending"))
        assertEquals(listOf(Reference("Key", key), Reference("Expected", "ready")), check.references())
    }

    @Test
    fun `map quantifiers keep nested operands without traversing them`() {
        val operand = object : AbstractList<String>() {
            override val size: Int get() = error("operand must not be inspected")
            override fun get(index: Int): String = error("operand must not be inspected")
        }
        val nested = object : Condition<String, String> {
            override fun description() = "custom expectation"
            override fun references() = listOf(Reference("Expected", operand))
            override fun probe(actual: String) = unsatisfied<String>("custom expectation was not met")
        }
        assertSame(operand, allValues(nested).references().single().value())
    }

    @Test
    fun `composed references stay immutable and do not format keys during polling`() {
        val key = UnprintableKey()
        val check = valueFor(key, equalTo("ready")).because("selected value must be ready")
        val source = listOf(mapOf(key to "pending"), mapOf(key to "ready")).iterator()
        val result = tryAwait(MapSource { source.next().toMutableMap() }).every(ofNanos(1)).until(check)

        assertEquals("ready", result.orThrow())
        assertEquals(0, key.renders)
        assertSame(key, check.references().first().value())
        assertEquals("ready", check.references().last().value())
        assertThrows(UnsupportedOperationException::class.java) { (check.references() as MutableList).clear() }
        assertThat(check.references().toString()).contains("Key=<unprintable>", "Expected=\"ready\"")
        assertThat(result.conditions().single().toString()).contains("Key: <unprintable>", "Expected: \"ready\"",
            "Because: selected value must be ready")
    }

    @Test
    fun `nested lookups retain their definition through missing keys and mismatches`() {
        val source = listOf<Map<String, Map<String, Any>>>(emptyMap(), mapOf("account" to emptyMap()),
            mapOf("account" to mapOf("state" to 1)), mapOf("account" to mapOf("state" to "ready"))).iterator()
        val check = valueFor("account", valueFor("state", instanceOf(String::class.java)))
        val result = tryAwait(MapSource { source.next().toMutableMap() }).every(ofNanos(1)).until(check)

        assertEquals("ready", result.orThrow())
        assertEquals(4, attempts(result).size)
        assertSame(check, result.conditions().single().condition())
        assertThat(result.conditions().single().toString()).contains("Key: \"account\"", "Key: \"state\"",
            "value is an instance of java.lang.String")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `an extraction failure identifies completed outer selections`(nested: Boolean) {
        val cause = IllegalStateException("iterator failed")
        val actual = if (nested) listOf(brokenCollection<String>(cause)) else brokenCollection<Collection<String>>(cause)
        val check = singleElement(singleElement(instanceOf(String::class.java)))
        val result = tryAwait(Callable { actual }).until(check)

        assertSame(cause, failure(result).cause())
        if (nested) {
            assertThat(failure(result).message()).contains("Within: single element")
            assertThat(attempts(result).single().toString()).contains("Within: single element")
        } else assertThat(failure(result).message()).doesNotContain("Within:")
    }

    @Test
    fun `nested quantifier failures retain their positions inside captured`() {
        val cause = IllegalArgumentException("probe failed")
        var calls = 0
        val nested = condition<String, String>("value is ready") {
            calls++
            if (it == "bad") throw cause else satisfied(it)
        }
        val actual = Optional.of(listOf(listOf("ready"), listOf("bad", "unused")))
        val check = hasValue(all(all(nested)))
        val result = tryAwait(Callable { actual }).every(ofNanos(1)).until(captured(isNotNull, check))
        val last = attempts(result).last()
        val location = "optional value → collection elements[1] → collection elements[0]"

        assertSame(cause, failure(result).cause())
        assertEquals(2, calls)
        assertSame(check, result.conditions().last().condition())
        assertEquals(2L, result.conditions().last().attempts().firstKey())
        assertEquals(location, uncontrolled(CONDITION, last).location())
        assertThat(failure(result).message()).contains("Within: $location")
        assertThat(last.toString()).contains("Within: $location")
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun `a traversal failure reports the number of checked candidates`(completed: Int) {
        val cause = AssertionError("iterator failed")
        val actual = if (completed == 0) brokenCollection<String>(cause) else object : AbstractCollection<String>() {
            override val size = 2
            override fun iterator() = object : Iterator<String> {
                var read = false
                override fun hasNext(): Boolean = if (read) throw cause else true
                override fun next(): String { read = true; return "ready" }
            }
            override fun toString() = "unavailable collection"
        }
        var calls = 0
        val check = all(condition<String, String>("ready") { calls++; satisfied(it) })
        val result = tryAwait(Callable { actual }).until(check)

        assertEquals(completed, calls)
        assertSame(cause, failure(CONDITION_FAILED, result).cause())
        assertThat(failure(result).message()).contains("Within: collection elements (checked $completed)")
    }

    @Test
    fun `an unavailable map view reports its scope before initializing the nested check`() {
        val cause = IllegalStateException("entries unavailable")
        val actual = object : AbstractMap<String, String>() {
            override val entries: Set<Map.Entry<String, String>> get() = throw cause
            override fun toString() = "unavailable map"
        }
        val check = allEntries<String, String>(conditionPerWait<Any, Any>("ready") { throw AssertionError("nested factory called") })
        val result = tryAwait(Callable { actual }).until(check)
        val attempt = uncontrolled(CONDITION, attempts(result).single())

        assertSame(cause, failure(result).cause())
        assertEquals("map entries → collection elements (checked 0)", attempt.location())
        assertThat(failure(result).message()).contains("Within: ${attempt.location()}")
    }

    @ParameterizedTest
    @MethodSource("quantifierMismatches")
    fun `quantifiers report their scope named check and mismatch`(check: AwaitCondition, actual: Any, scope: String, mismatch: String) {
        assertEquals("$scope: account is active", check.description())
        assertEquals(mismatch, rejection(check, actual).mismatch())
    }

    @Test
    fun `captured keeps the failed quantifier stage mismatch`() {
        val check = all(condition<Any, Any>("account is active") { unsatisfied("account is not active") })
        val run = start<List<String>, List<List<String>>>(captured(isNotNull, check))
        run.probe(listOf("pending"))
        val rejected = assertInstanceOf(Unsatisfied::class.java, run.probe(listOf("pending")))
        assertEquals("not all collection elements matched (checked 1): account is not active", rejected.mismatch())
        assertEquals("all collection elements match: account is active", check.description())
        assertEquals(rejected, run.historyResult())
    }

    @ParameterizedTest
    @MethodSource("missingOptionals")
    fun `missing optional values retain the nested definition`(actual: Optional<String>?, mismatch: String) {
        val selected = hasValue(equalTo("Bob"))
        val typed = hasValue(instanceOf(Int::class.javaObjectType))
        assertEquals(mismatch, rejection(selected, actual).mismatch())
        assertEquals(mismatch, rejection(typed, actual).mismatch())
        assertEquals("Bob", selected.references().single().value())
        assertThat(typed.description()).contains("java.lang.Integer")
    }

    @Test
    fun `optional history keeps its definition as presence and value change`() {
        val source = listOf(Optional.empty<String>(), Optional.of("Alice"), Optional.of("Bob")).iterator()
        val nested = equalTo("Bob")
        val check = hasValue(nested)
        val result = tryAwait(OptionalSource { source.next() }).every(ofNanos(1)).until(check)
        assertEquals("Bob", result.orThrow())
        assertEquals(3, attempts(result).size)
        assertSame(check, result.conditions().single().condition())
        assertEquals(nested.references(), result.conditions().single().condition().references())
    }

    @ParameterizedTest
    @MethodSource("missingValues")
    fun `a missing or mismatching map value retains both expected operands`(actual: Map<String, String>?) {
        val check = valueFor("customerId", equalTo("Bob"))
        rejection(check, actual)
        assertEquals(listOf(Reference("Key", "customerId"), Reference("Expected", "Bob")), check.references())
    }

    @Test
    fun `lookup history keeps both expectations after the key appears`() {
        val source = listOf(emptyMap(), mapOf("customerId" to "Alice"), mapOf("customerId" to "Bob")).iterator()
        val check = valueFor("customerId", equalTo("Bob"))
        val result = tryAwait(MapSource { source.next().toMutableMap() }).every(ofNanos(1)).until(check)
        assertEquals("Bob", result.orThrow())
        assertEquals(3, attempts(result).size)
        assertSame(check, result.conditions().single().condition())
        assertEquals(listOf(Reference("Key", "customerId"), Reference("Expected", "Bob")),
            result.conditions().single().condition().references())
    }

    @Test
    fun `a missing captured key retains lookup context without initializing its nested check`() {
        val check = valueFor("customerId", conditionPerWait<String, String>("nested") { throw AssertionError("nested factory called") })
        val run = start<Map<String, String>, List<Map<String, String>>>(captured(check, check))
        val rejected = assertInstanceOf(Unsatisfied::class.java, run.probe(emptyMap()))
        assertEquals("map did not contain the expected key", rejected.mismatch())
        assertEquals("customerId", check.references().single().value())
        assertEquals(rejected, run.historyResult())
    }

    @Test
    fun `a missing key is not formatted during construction or probing`() {
        val key = UnprintableKey()
        val check = valueFor(key, equalTo("Bob"))
        assertNull(rejection(check, emptyMap<Any, String>()).cause())
        assertSame(key, check.references().first().value())
        assertEquals(0, key.renders)
    }

    @ParameterizedTest
    @MethodSource("extractionFailures")
    fun `a failed extraction retains its key and skips the nested callback`(cause: Throwable) {
        val entry = object : SimpleImmutableEntry<String, String>("customerId", "Alice") {
            override val value: String get() = throw cause
        }
        val actual = EntryMap(listOf(entry))
        val check = valueFor("customerId", conditionPerWait<String, String>("nested") { throw AssertionError("nested factory called") })
            .because("customer lookup must succeed")
        val result = tryAwait(Callable { actual }).until(check)

        assertSame(cause, failure(CONDITION_FAILED, result).cause())
        assertThat(failure(result).message()).contains("Key: \"customerId\"", "Because: customer lookup must succeed")
        assertSame(cause, thrown(check, actual))
        val recorded = tryAwait(Callable { actual }).until(captured(check, check))
        assertSame(cause, uncontrolled(CONDITION, attempts(recorded).single()).cause())
        assertTrue(recorded.conditions().last().attempts().isEmpty())
        assertEquals("customerId", recorded.conditions().first().condition().references().single().value())
        assertEquals("customer lookup must succeed", recorded.conditions().first().condition().explanation())
    }

    private fun <ELEMENT> brokenCollection(cause: Throwable): Collection<ELEMENT> = object : AbstractCollection<ELEMENT>() {
        override val size = 1
        override fun iterator(): Iterator<ELEMENT> = throw cause
        override fun toString() = "unavailable collection"
    }

    private class UnprintableKey {
        var renders = 0
            private set
        override fun toString(): String { renders++; throw IllegalStateException("key formatting failed") }
    }

    companion object {
        @JvmStatic
        fun quantifierMismatches(): List<Arguments> {
            val rejected = condition<Any, Any>("account is active") { unsatisfied("account is not active") }
            val matched = condition<Any, Any>("account is active") { satisfied(it) }
            val collection = listOf("pending")
            val map = mapOf("account" to "pending")
            return listOf(
                argumentSet("all elements", all(rejected), collection, "all collection elements match", "not all collection elements matched (checked 1): account is not active"),
                argumentSet("any element", any(rejected), collection, "some collection elements match", "no collection elements matched (checked 1): account is not active"),
                argumentSet("no element", none(matched), collection, "no collection elements match", "some collection elements matched (checked 1)"),
                argumentSet("all entries", allEntries<Any, Any>(rejected), map, "map entries: all collection elements match", "map entries: not all collection elements matched (checked 1): account is not active"),
                argumentSet("any entry", hasEntry<Any, Any>(rejected), map, "map entries: some collection elements match", "map entries: no collection elements matched (checked 1): account is not active"),
                argumentSet("no entry", hasNoEntry<Any, Any>(matched), map, "map entries: no collection elements match", "map entries: some collection elements matched (checked 1)"),
                argumentSet("all keys", allKeys(rejected), map, "map keys: all collection elements match", "map keys: not all collection elements matched (checked 1): account is not active"),
                argumentSet("any key", anyKey(rejected), map, "map keys: some collection elements match", "map keys: no collection elements matched (checked 1): account is not active"),
                argumentSet("no key", noKey(matched), map, "map keys: no collection elements match", "map keys: some collection elements matched (checked 1)"),
                argumentSet("all values", allValues(rejected), map, "map values: all collection elements match", "map values: not all collection elements matched (checked 1): account is not active"),
                argumentSet("any value", anyValue(rejected), map, "map values: some collection elements match", "map values: no collection elements matched (checked 1): account is not active"),
                argumentSet("no value", noValue(matched), map, "map values: no collection elements match", "map values: some collection elements matched (checked 1)"),
                argumentSet("nested quantifiers", all(any(rejected)), listOf(collection),
                    "all collection elements match: some collection elements match",
                    "not all collection elements matched (checked 1): no collection elements matched (checked 1): account is not active")
            )
        }

        @JvmStatic
        fun missingOptionals() = listOf(argumentSet("null", null, "optional was null"), argumentSet("empty", Optional.empty<String>(), "optional was empty"))

        @JvmStatic
        fun missingValues() = listOf(argumentSet("null", null), argumentSet("empty", emptyMap<String, String>()), argumentSet("mismatch", mapOf("customerId" to "Alice")))

        @JvmStatic
        fun extractionFailures() = listOf(IllegalStateException("value failed"), AssertionError("getter assertion"), Throwable("getter throwable"))
    }
}

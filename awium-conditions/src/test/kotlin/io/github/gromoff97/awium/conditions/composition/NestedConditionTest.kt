package io.github.gromoff97.awium.conditions.composition

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.conditions.CollectionConditions.elementAt
import io.github.gromoff97.awium.conditions.CollectionConditions.firstElement
import io.github.gromoff97.awium.conditions.CollectionConditions.lastElement
import io.github.gromoff97.awium.conditions.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.Conditions.condition
import io.github.gromoff97.awium.conditions.Conditions.instanceOf
import io.github.gromoff97.awium.conditions.Conditions.matches
import io.github.gromoff97.awium.conditions.Conditions.conditionPerWait
import io.github.gromoff97.awium.conditions.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.MapConditions.singleEntry
import io.github.gromoff97.awium.conditions.OptionalConditions.hasValue
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.execution.ConditionExecution.bind
import io.github.gromoff97.awium.function.CheckedFunction
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import io.github.gromoff97.awium.result.ConditionResult.satisfied
import io.github.gromoff97.awium.result.ConditionResult.unsatisfied
import java.math.BigDecimal
import java.io.IOException
import java.util.Optional
import java.util.AbstractMap.SimpleImmutableEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class NestedConditionTest {
    @AfterEach
    fun `clear the caller interrupt flag`() {
        Thread.interrupted()
    }
    data class Selection(
        val wrap: (Number) -> Any,
        val compose: (Condition<Number, Number>) -> AwaitCondition
    )

    @ParameterizedTest
    @MethodSource("selections")
    fun `nested factories start lazily for each execution`(selection: Selection) {
        var factories = 0
        val nested: Condition<Number, Number> = conditionPerWait("second observation") {
            factories++
            var calls = 0
            CheckedFunction { actual -> if (++calls == 2) satisfied(actual) else unsatisfied("first observation") }
        }
        val condition = selection.compose(nested)
        val selected = BigDecimal("1.00")
        val actual = selection.wrap(selected)

        repeat(2) { completedRuns ->
            val execution = bind<Any, Number>(condition)
            assertEquals(completedRuns, factories)
            assertInstanceOf(Unsatisfied::class.java, execution.probe(actual))
            assertSame(selected, result(execution.probe(actual)))
            assertEquals(completedRuns + 1, factories)
        }
    }

    @ParameterizedTest
    @MethodSource("invalidSingleSelections")
    fun `a missing sole element does not initialize the nested condition`(actual: List<String>?) {
        var factories = 0
        val nested = conditionPerWait<String, String>("ready") {
            factories++
            CheckedFunction { satisfied(it) }
        }
        rejection(singleElement(nested), actual)
        assertEquals(0, factories)
    }

    @Test
    fun `single selection checks cardinality before checking its value`() {
        var checked = 0
        val nested = conditionPerWait<Int, Int>("answer") {
            CheckedFunction { actual ->
                checked++
                if (actual == 42) satisfied(actual) else unsatisfied("not the answer")
            }
        }
        val execution = bind(singleElement(nested))

        for (actual in listOf(emptyList(), listOf(42, 42), listOf(1, 42))) {
            assertInstanceOf(Unsatisfied::class.java, execution.probe(actual))
        }
        assertEquals(0, checked)
        assertInstanceOf(Unsatisfied::class.java, execution.probe(listOf(1)))
        assertEquals(42, result(execution.probe(listOf(42))))
        assertEquals(2, checked)
    }

    @Test
    fun `map value checks start only after the key is found`() {
        val events = mutableListOf<String>()
        val nested = conditionPerWait<Int, Int>("value is ready") {
            events += "factory"
            CheckedFunction { value -> events += "value $value"; satisfied(value) }
        }
        val execution = bind(valueFor("key", nested))

        for (actual in listOf(null, emptyMap(), mapOf("other" to 2))) {
            assertInstanceOf(Unsatisfied::class.java, execution.probe(actual))
        }
        assertEquals(emptyList<String>(), events)
        assertEquals(1, result(execution.probe(mapOf("key" to 1, "other" to 2))))
        assertEquals(2, result(execution.probe(mapOf("key" to 2, "other" to 1))))
        assertEquals(listOf("factory", "value 1", "value 2"), events)
    }

    @Test
    fun `single entry checks start only after one entry is found`() {
        val events = mutableListOf<String>()
        val nested = conditionPerWait<Map.Entry<String, Int>, Int>("entry is ready") {
            events += "factory"
            CheckedFunction { entry -> events += "value ${entry.value}"; satisfied(entry.value) }
        }
        val execution = bind(singleEntry(nested))

        for (actual in listOf(null, emptyMap(), mapOf("key" to 2, "other" to 1))) {
            assertInstanceOf(Unsatisfied::class.java, execution.probe(actual))
        }
        assertEquals(emptyList<String>(), events)
        assertEquals(1, result(execution.probe(mapOf("key" to 1))))
        assertEquals(2, result(execution.probe(mapOf("key" to 2))))
        assertEquals(listOf("factory", "value 1", "value 2"), events)
    }

    @Test
    fun `map value metadata retains the key and nested description`() {
        val nested = conditionPerWait<Int, Int>("value is ready") { CheckedFunction { satisfied(it) } }
        val condition = valueFor("key", nested).because("the selected value must be ready")

        assertEquals("map value: value is ready", condition.description())
        assertEquals("key", condition.references().first().value())
        assertEquals("the selected value must be ready", condition.explanation())
    }

    @Test
    fun `an interrupted value extraction skips the nested factory`() {
        val entry = object : SimpleImmutableEntry<String, String>("key", "ready") {
            override val value: String
                get() { Thread.currentThread().interrupt(); return super.value }
        }
        val nested = conditionPerWait<String, String>("must not run") { throw AssertionError("nested factory called") }
        assertInstanceOf(InterruptedException::class.java, thrown(valueFor("key", nested), EntryMap(listOf(entry))))
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @Test
    fun `single entry rejects a missing nested condition`() {
        assertThrows(NullPointerException::class.java) { singleEntry(null as PreservingCondition<Map.Entry<String, Int>>?) }
    }

    @ParameterizedTest
    @MethodSource("positions")
    fun `positional selectors preserve nested mismatch details`(select: (Condition<Any, String>?) -> AwaitCondition) {
        val rejected = rejection(select(instanceOf(String::class.java)), listOf(1, "ready", 2))
        assertThat(rejected.mismatch()).contains("value type was java.lang.Integer")
    }

    @ParameterizedTest
    @MethodSource("positions")
    fun `positional selectors reject a missing nested condition`(select: (Condition<Any, String>?) -> AwaitCondition) {
        assertEquals("condition must not be null", assertThrows(NullPointerException::class.java) { select(null) }.message)
    }

    @Test
    fun `an absent index reports cardinality before checking the selected value`() {
        val condition = elementAt(3, instanceOf(String::class.java))
        assertEquals("collection size was 3", rejection(condition, listOf(1, "ready", 2)).mismatch())
        assertThat(condition.description()).contains("index 3")
    }

    @Test
    fun `a negative index is rejected before creating a nested selector`() {
        assertThrows(IllegalArgumentException::class.java) { elementAt(-1, instanceOf(String::class.java)) }
    }

    @Test
    fun `single element rejects a missing preserving condition`() {
        assertEquals("condition must not be null", assertThrows(NullPointerException::class.java) {
            singleElement(null as PreservingCondition<String>?)
        }.message)
    }

    @ParameterizedTest
    @MethodSource("nullableCollections")
    fun `a nested selector can recover after a null collection`(condition: AwaitCondition) {
        val execution = bind<List<String>?, String>(condition)
        assertInstanceOf(Unsatisfied::class.java, execution.probe(null))
        assertEquals(" ", result(execution.probe(listOf(" "))))
    }

    @Test
    fun `a nested null result fails the probe`() {
        val invalid = condition<String, String>("invalid") { null }
        assertInstanceOf(NullPointerException::class.java, thrown(singleElement(invalid), listOf("ready")))
    }

    @ParameterizedTest
    @MethodSource("failures")
    fun `nested failures retain their cause without changing the interrupt flag`(cause: Throwable) {
        var calls = 0
        val nested = condition<String, String>("broken") { calls++; throw cause }
        assertSame(cause, thrown(singleElement(nested), listOf("ready")))
        assertEquals(1, calls)
        assertFalse(Thread.currentThread().isInterrupted)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `an interrupted positional predicate stops after one call`(last: Boolean) {
        var calls = 0
        val predicate = io.github.gromoff97.awium.function.CheckedPredicate<Int> {
            calls++
            Thread.currentThread().interrupt()
            false
        }
        val condition = if (last) lastElement(predicate) else firstElement(predicate)
        assertInstanceOf(InterruptedException::class.java, thrown(condition, listOf(1, 2)))
        assertEquals(1, calls)
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @ParameterizedTest
    @MethodSource("failedLookups")
    fun `positional selectors retain nested lookup failures and their key`(position: Int, cause: Throwable) {
        val entry: Map.Entry<String, Any> = mock()
        `when`(entry.key).thenReturn("target-key")
        `when`(entry.value).thenThrow(cause)
        val broken = EntryMap(listOf(entry))
        val lookup = valueFor("target-key", instanceOf(String::class.java))
        val condition = when (position) {
            0 -> firstElement(lookup)
            1 -> lastElement(lookup)
            else -> elementAt(0, lookup)
        }

        assertSame(cause, thrown(condition, listOf(broken, mapOf("target-key" to "ready"), broken)))
        assertEquals("target-key", condition.references().first().value())
    }

    companion object {
        @JvmStatic
        fun nullableCollections() = listOf(
            argumentSet("last", lastElement { value: String -> value.isBlank() }),
            argumentSet("single", singleElement(matches { value: String -> value.isBlank() })))

        @JvmStatic
        fun positions() = listOf(
            argumentSet("first", { nested: Condition<Any, String>? -> firstElement(nested) }),
            argumentSet("last", { nested: Condition<Any, String>? -> lastElement(nested) }),
            argumentSet("index", { nested: Condition<Any, String>? -> elementAt(0, nested) }))

        @JvmStatic
        fun failures() = listOf(argumentSet("checked", IOException("checked")), argumentSet("interrupted", InterruptedException("interrupted")),
            argumentSet("runtime", IllegalStateException("runtime")), argumentSet("assertion", AssertionError("assertion")),
            argumentSet("fatal", OutOfMemoryError("fatal")))

        @JvmStatic
        fun failedLookups() = listOf("first", "last", "index").flatMapIndexed { index, name ->
            listOf(IllegalStateException("getter failed"), AssertionError("getter failed")).map { cause ->
                argumentSet("$name / ${cause.javaClass.simpleName}", index, cause)
            }
        }

        @JvmStatic
        fun selections() = listOf(
            named("optional value", Selection({ Optional.of(it) }, { hasValue(it) })),
            named("single element", Selection({ listOf(it) }, { singleElement(it) })),
            named("first element", Selection({ listOf(it) }, { firstElement(it) })),
            named("last element", Selection({ listOf(it) }, { lastElement(it) })),
            named("element at index", Selection({ listOf(it) }, { elementAt(0, it) })),
            named("map value", Selection({ mapOf("key" to it) }, { valueFor("key", it) }))
        )

        @JvmStatic
        fun invalidSingleSelections() = listOf(
            argumentSet("null collection", null), argumentSet("empty collection", emptyList<String>()),
            argumentSet("several elements", listOf("ready", "other"))
        )
    }
}

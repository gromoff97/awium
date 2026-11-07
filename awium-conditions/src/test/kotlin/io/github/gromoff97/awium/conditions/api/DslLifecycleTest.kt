package io.github.gromoff97.awium.conditions.api

import consumer.DslLifecycleConsumer
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.dsl.Await.tryAwait
import io.github.gromoff97.awium.dsl.AwaitCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.CollectingCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.ComparisonCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.InputCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.MapValueCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.SelectedCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.bind
import io.github.gromoff97.awium.dsl.AwaitCondition.definitionOf
import io.github.gromoff97.awium.dsl.TypedCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.common.CommonConditions.captured
import io.github.gromoff97.awium.conditions.common.CommonConditions.condition
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.matches
import io.github.gromoff97.awium.conditions.map.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.result.AttemptResult
import io.github.gromoff97.awium.result.AttemptResult.Satisfied
import io.github.gromoff97.awium.result.AttemptResult.satisfied
import io.github.gromoff97.awium.result.AttemptResult.unsatisfied
import java.math.BigDecimal
import java.util.concurrent.Callable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class DslLifecycleTest {
    @Test
    fun `a standalone collector declares itself as its only stage`() {
        val original = RecordOnce()
        assertEquals(listOf(original), original.stages())
    }

    @Test
    fun `a collector keeps its declared stages when its reason changes`() {
        val original = RecordOnce()
        assertEquals(original.stages(), original.because("record once").stages())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `java terminals infer a custom collector result from the source`(diagnostic: Boolean) {
        assertEquals("ready", DslLifecycleConsumer.collected(RecordOnce(), diagnostic))
    }

    @Test
    fun `a custom collector preserves its explanation in recorded metadata`() {
        val original = RecordOnce()
        val explained = original.because("old reason").because("record once")
        val result = tryAwait(Callable { "ready" }).until(explained)
        assertNull(original.explanation())
        assertSame(explained, definitionOf(result.conditions().single().condition()).orElseThrow())
        assertEquals("record once", result.conditions().single().condition().explanation())
    }

    @Test
    fun `separate collector bindings keep their observations independent`() {
        val original = RecordOnce()
        val first = bind<String>(original)
        val second = bind<String>(original)
        val firstResult = result(first.probe("first"))
        assertEquals(listOf("second"), result(second.probe("second")))
        assertEquals(listOf("first"), firstResult)
    }

    @ParameterizedTest
    @MethodSource("families")
    fun `a replacement reason retains the condition family and metadata`(original: AwaitCondition, family: Class<*>) {
        val first = original.because("first reason")
        val second = first.because("second reason")
        assertNotSame(original, first)
        assertNotSame(first, second)
        assertTrue(family.isInstance(second))
        assertNull(original.explanation())
        assertEquals("first reason", first.explanation())
        assertEquals("second reason", second.explanation())
        assertEquals(original.description(), second.description())
        assertEquals(original.references(), second.references())
    }

    @ParameterizedTest
    @MethodSource("families")
    fun `a null reason is rejected in every family`(original: AwaitCondition) {
        assertThrows<NullPointerException> { original.because(null) }
    }

    @ParameterizedTest
    @MethodSource("families")
    fun `a blank reason is rejected in every family`(original: AwaitCondition) {
        assertThrows<IllegalArgumentException> { original.because(" \n\t") }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `preserving policies return the observation instead of the probe value`(comparison: Boolean) {
        val check: AwaitCondition = if (comparison) object : ComparisonCondition<CharSequence> {
            override fun description() = "accepted"
            override fun probe(actual: Any?) = satisfied("different")
        } else object : PreservingCondition<CharSequence> {
            override fun description() = "accepted"
            override fun probe(actual: CharSequence) = satisfied("different")
        }
        val actual = StringBuilder("observed")
        assertSame(actual, result(bind<StringBuilder>(check).probe(actual)))
    }

    @Test
    fun `preserving checks create their evaluator only once per binding`() {
        var factories = 0
        val original = object : PreservingCondition<String> {
            override fun description() = "two observations"
            override fun probe(actual: String): AttemptResult<*> = error("evaluator required")
            override fun newAttempt(): AwaitAttempt<String, String> {
                factories++
                var calls = 0
                return AwaitAttempt { if (++calls == 2) satisfied("different") else unsatisfied("pending") }
            }
        }
        val bound = bind<String>(original.because("two observations"))
        assertEquals(0, factories)
        assertEquals(unsatisfied<String>("pending"), bound.probe("ready"))
        assertEquals(satisfied("ready"), bound.probe("ready"))
        assertEquals(1, factories)
    }

    @Test
    fun `a preserving reason retains the original evaluator`() {
        val evaluator = AwaitAttempt<String, String> { satisfied(it) }
        val original = object : PreservingCondition<String> {
            override fun description() = "ready"
            override fun probe(actual: String) = satisfied(actual)
            override fun newAttempt() = evaluator
        }
        assertSame(evaluator, original.because("needed").newAttempt())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `selected custom runs start fresh with or without an explanation`(explained: Boolean) {
        val definition = afterTwo<List<String>> { it.first() }
        val selector = object : SelectedCondition<List<String>> {
            override fun description() = definition.description()
            override fun probe(actual: List<String>) = definition.probe(actual)
            override fun newRun(): Condition<List<String>, *> = definition.newRun()
        }
        independentRuns(if (explained) selector.because("first value") else selector, listOf("ready"))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `map value custom runs start fresh with or without an explanation`(explained: Boolean) {
        val definition = afterTwo<Map<String, String>> { it.getValue("key") }
        val selector = object : MapValueCondition<Map<String, String>> {
            override fun description() = definition.description()
            override fun probe(actual: Map<String, String>) = definition.probe(actual)
            override fun newRun(): Condition<Map<String, String>, *> = definition.newRun()
        }
        independentRuns(if (explained) selector.because("key value") else selector, mapOf("key" to "ready"))
    }

    @Test
    fun `java selection types survive every configuration method`() {
        val value = BigDecimal("42.00")
        val selections = DslLifecycleConsumer.configuredSelections(value)
        assertEquals(8, selections.size)
        selections.forEach { assertSame(value, it) }
    }

    @Test
    fun `java callable references and lambdas retain selected types`() {
        assertEquals(List(9) { "ready" }, DslLifecycleConsumer.callableSelections())
    }

    @Test
    fun `java wildcard lambdas retain element and entry bounds`() {
        assertEquals(listOf(42, 42, 42, 5, 42), DslLifecycleConsumer.wildcardSelections(listOf(42), mapOf(42 to "ready")))
    }

    @Test
    fun `java constructor lambdas retain their concrete types`() {
        assertEquals(listOf(0, 0, 0, 0), DslLifecycleConsumer.constructorLambdas())
    }

    private fun <INPUT> independentRuns(selector: AwaitCondition, actual: INPUT) {
        val first = bind<INPUT>(selector)
        val second = bind<INPUT>(selector)
        assertEquals(unsatisfied<String>("pending"), first.probe(actual))
        assertEquals(unsatisfied<String>("pending"), second.probe(actual))
        assertEquals(satisfied("ready"), first.probe(actual))
        assertEquals(satisfied("ready"), second.probe(actual))
    }

    private fun <INPUT> afterTwo(extract: (INPUT) -> String): Condition<INPUT, String> = object : Condition<INPUT, String> {
        override fun description() = "two observations"
        override fun probe(actual: INPUT): AttemptResult<String> = error("the custom run must perform the check")
        override fun newRun(): Condition<INPUT, String> = object : Condition<INPUT, String> {
            private var observations = 0
            override fun description() = "two observations"
            override fun probe(actual: INPUT): AttemptResult<String> =
                if (++observations == 2) satisfied(extract(actual)) else unsatisfied("pending")
        }
    }

    private class RecordOnce : CollectingCondition<InputCondition<Any>> {
        override fun description() = "record one observation"
        override fun newRun(): Condition<Any?, List<Any?>> = object : Condition<Any?, List<Any?>> {
            override fun description() = "record one observation"
            override fun probe(actual: Any?) = satisfied(listOf(actual))
        }
    }

    companion object {
        @JvmStatic
        fun families() = listOf(
            argumentSet("typed", condition("length") { actual: String -> satisfied(actual.length) }, TypedCondition::class.java),
            argumentSet("preserving", matches { actual: String -> actual.isNotEmpty() }, PreservingCondition::class.java),
            argumentSet("comparison", equalTo("ready"), ComparisonCondition::class.java),
            argumentSet("element", singleElement, SelectedCondition::class.java),
            argumentSet("value", valueFor("key"), MapValueCondition::class.java),
            argumentSet("collecting", captured(equalTo("ready")), CollectingCondition::class.java))
    }
}

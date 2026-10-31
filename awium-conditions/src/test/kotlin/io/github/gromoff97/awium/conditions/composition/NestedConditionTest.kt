package io.github.gromoff97.awium.conditions.composition

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.conditions.CollectionConditions.elementAt
import io.github.gromoff97.awium.conditions.CollectionConditions.firstElement
import io.github.gromoff97.awium.conditions.CollectionConditions.lastElement
import io.github.gromoff97.awium.conditions.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.Conditions.conditionPerWait
import io.github.gromoff97.awium.conditions.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.OptionalConditions.hasValue
import io.github.gromoff97.awium.execution.ConditionExecution.bind
import io.github.gromoff97.awium.function.CheckedFunction
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import io.github.gromoff97.awium.result.ConditionResult.satisfied
import io.github.gromoff97.awium.result.ConditionResult.unsatisfied
import java.math.BigDecimal
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class NestedConditionTest {
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

    companion object {
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

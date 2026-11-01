package io.github.gromoff97.awium.conditions.composition

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.all
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.Conditions.equalTo
import io.github.gromoff97.awium.conditions.Conditions.matches
import io.github.gromoff97.awium.conditions.map.MapConditions.allEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.hasEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.hasNoEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class ExplanationTest {
    @ParameterizedTest
    @MethodSource("compositions")
    fun `an outer reason overrides the inherited reason without changing the check`(
        compose: (ComparisonCondition<String>) -> AwaitCondition, actual: Any
    ) {
        val nested = equalTo("ready").because("inner reason")
        val inherited = compose(nested)
        val overridden = inherited.because("outer reason")

        assertEquals(rejection(inherited, actual), rejection(overridden, actual))
        assertEquals("inner reason", inherited.explanation())
        assertEquals("outer reason", overridden.explanation())
        assertEquals("inner reason", nested.explanation())
    }

    @ParameterizedTest
    @MethodSource("entryChecks")
    fun `entry quantifiers inherit the nested reason`(check: AwaitCondition) {
        rejection(check, mapOf("ready" to 1))
        assertEquals("entry requirement", check.explanation())
    }

    companion object {
        @JvmStatic
        fun compositions() = listOf(
            Triple("optional", { nested: ComparisonCondition<String> -> hasValue(nested) },
                listOf(Optional.empty<String>(), Optional.of("pending"))),
            Triple("map value", { nested: ComparisonCondition<String> -> valueFor("id", nested) },
                listOf(emptyMap<String, String>(), mapOf("id" to "pending"))),
            Triple("single element", { nested: ComparisonCondition<String> -> singleElement(nested) },
                listOf(emptyList<String>(), listOf("pending"))),
            Triple("all elements", { nested: ComparisonCondition<String> -> all(nested) }, listOf(listOf("pending")))
        ).flatMap { (name, compose, values) -> values.map { argumentSet("$name / $it", compose, it) } }

        @JvmStatic
        fun entryChecks() = listOf(
            argumentSet("all entries", allEntries(matches { _: Map.Entry<String, Int> -> false }.because("entry requirement"))),
            argumentSet("any entry", hasEntry(matches { _: Map.Entry<String, Int> -> false }.because("entry requirement"))),
            argumentSet("no entry", hasNoEntry(matches { _: Map.Entry<String, Int> -> true }.because("entry requirement")))
        )
    }
}

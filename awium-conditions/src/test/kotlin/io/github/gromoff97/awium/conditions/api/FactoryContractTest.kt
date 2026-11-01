package io.github.gromoff97.awium.conditions.api

import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions
import io.github.gromoff97.awium.conditions.common.Conditions
import io.github.gromoff97.awium.conditions.map.MapConditions
import io.github.gromoff97.awium.conditions.optional.OptionalConditions
import io.github.gromoff97.awium.conditions.string.StringConditions
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.all
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.any
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.none
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.map.MapConditions.allEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.hasEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.hasNoEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue
import io.github.gromoff97.awium.conditions.function.CheckedPredicate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class FactoryContractTest {
    @ParameterizedTest
    @ValueSource(classes = [Conditions::class, OptionalConditions::class, CollectionConditions::class,
        MapConditions::class, StringConditions::class])
    fun `constant conditions have no zero argument factory aliases`(catalog: Class<*>) {
        for (field in catalog.fields) {
            assertThat(catalog.declaredMethods).describedAs(field.toGenericString()).noneMatch {
                it.name == field.name && it.parameterCount == 0
            }
        }
    }

    @ParameterizedTest
    @MethodSource("missingChecks")
    fun `a missing check is rejected by its factory`(argument: String, create: Executable) {
        assertEquals("$argument must not be null", assertThrows(NullPointerException::class.java, create).message)
    }

    companion object {
        @JvmStatic
        fun missingChecks() = listOf(
            argumentSet("singleElement predicate", "predicate", Executable { singleElement(null as CheckedPredicate<String>?) }),
            argumentSet("hasValue predicate", "predicate", Executable { hasValue(null as CheckedPredicate<String>?) }),
            argumentSet("valueFor predicate", "predicate", Executable { valueFor("key", null as CheckedPredicate<String>?) }),
            argumentSet("singleEntry predicate", "predicate", Executable { singleEntry(null as CheckedPredicate<Map.Entry<String, String>>?) }),
            argumentSet("allEntries predicate", "predicate", Executable { allEntries(null as CheckedPredicate<Map.Entry<String, String>>?) }),
            argumentSet("hasEntry predicate", "predicate", Executable { hasEntry(null as CheckedPredicate<Map.Entry<String, String>>?) }),
            argumentSet("hasNoEntry predicate", "predicate", Executable { hasNoEntry(null as CheckedPredicate<Map.Entry<String, String>>?) }),
            argumentSet("all preserving check", "condition", Executable { all(null as PreservingCondition<String>?) }),
            argumentSet("any comparison", "condition", Executable { any(null as ComparisonCondition<String>?) }),
            argumentSet("none typed check", "condition", Executable { none(null as Condition<Any, String>?) }),
            argumentSet("valueFor preserving check", "condition", Executable { valueFor("key", null as PreservingCondition<String>?) }),
            argumentSet("valueFor typed check", "condition", Executable { valueFor("key", null as Condition<Any, String>?) }),
            argumentSet("hasValue comparison", "condition", Executable { hasValue(null as ComparisonCondition<String>?) }),
            argumentSet("singleElement comparison", "condition", Executable { singleElement(null as ComparisonCondition<String>?) }),
            argumentSet("valueFor comparison", "condition", Executable { valueFor("key", null as ComparisonCondition<String>?) }),
            argumentSet("singleEntry comparison", "condition", Executable { singleEntry(null as ComparisonCondition<Map.Entry<String, String>>?) })
        )
    }
}

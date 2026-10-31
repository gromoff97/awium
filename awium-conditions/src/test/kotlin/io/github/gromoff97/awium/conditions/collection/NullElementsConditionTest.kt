package io.github.gromoff97.awium.conditions.collection

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.CollectionConditions.containsNull
import io.github.gromoff97.awium.conditions.CollectionConditions.containsOnlyNulls
import io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContainNull
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.smallLists
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class NullElementsConditionTest {
    @ParameterizedTest
    @MethodSource("rules")
    fun `checks null elements including empty collections`(condition: AwaitCondition, accepts: (List<String?>) -> Boolean) {
        for (actual in smallLists()) {
            assertEquals(accepts(actual), evaluate(condition, actual) is Satisfied<*>, "actual=$actual")
        }
    }

    @ParameterizedTest
    @MethodSource("conditions")
    fun `a null collection is a mismatch`(condition: AwaitCondition) {
        assertEquals("collection was null", rejection(condition, null).mismatch())
    }

    companion object {
        @JvmStatic
        fun rules() = listOf(
            argumentSet("contains null", containsNull, { values: List<String?> -> values.any { it == null } }),
            argumentSet("no nulls", doesNotContainNull, { values: List<String?> -> values.none { it == null } }),
            argumentSet("only nulls", containsOnlyNulls, { values: List<String?> -> values.isNotEmpty() && values.all { it == null } })
        )

        @JvmStatic
        fun conditions() = listOf(containsNull, doesNotContainNull, containsOnlyNulls)
    }
}

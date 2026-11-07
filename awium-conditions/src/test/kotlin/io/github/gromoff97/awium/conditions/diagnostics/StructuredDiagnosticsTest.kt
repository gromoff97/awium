package io.github.gromoff97.awium.conditions.diagnostics

import io.github.gromoff97.awium.dsl.AwaitCondition
import io.github.gromoff97.awium.Condition.Reference
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.all
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.none
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.common.CommonConditions.captured
import io.github.gromoff97.awium.conditions.common.CommonConditions.condition
import io.github.gromoff97.awium.conditions.map.MapConditions.allValues
import io.github.gromoff97.awium.conditions.map.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.result.AttemptResult.satisfied
import io.github.gromoff97.awium.result.AttemptResult.unsatisfied
import io.github.gromoff97.awium.result.Diagnostic
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class StructuredDiagnosticsTest {

    @ParameterizedTest
    @MethodSource("nestedFailures")
    fun `nested checks preserve diagnostic fields and the original cause`(check: AwaitCondition, actual: Any) {
        val rejected = rejection(check, actual)

        assertEquals(details.fields(), rejected.diagnostic().fields())
        assertSame(cause, rejected.cause())
    }

    @Test
    fun `a forbidden match retains the fields from its successful check`() {
        val inner = condition<Int, Int>("positive") { satisfied(it, details) }

        assertEquals(details.fields(), rejection(none(inner), listOf(2)).diagnostic().fields())
    }

    @Test
    fun `successful nested extraction retains diagnostic fields`() {
        val inner = condition<Int, String>("text") { satisfied(it.toString(), details) }
        val evaluated = evaluate(singleElement(hasValue(inner)), listOf(Optional.of(2)))

        assertSame(details, evaluated.diagnostic())
    }

    companion object {
        private val details = Diagnostic("pending", Reference("Progress", 2))
        private val cause = IllegalStateException("not ready")

        @JvmStatic
        fun nestedFailures(): List<Arguments> {
            val inner = condition<Int, Int>("ready") { unsatisfied(details, cause) }
            return listOf(
                argumentSet("collection selection", singleElement(inner), listOf(2)),
                argumentSet("optional selection", hasValue(inner), Optional.of(2)),
                argumentSet("map selection", valueFor("id", inner), mapOf("id" to 2)),
                argumentSet("quantifier", all(inner), listOf(2)),
                argumentSet("map view", allValues(inner), mapOf("id" to 2)),
                argumentSet("captured stage", captured(inner), 2),
            )
        }
    }
}

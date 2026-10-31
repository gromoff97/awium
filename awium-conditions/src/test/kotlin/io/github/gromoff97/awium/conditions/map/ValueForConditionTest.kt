package io.github.gromoff97.awium.conditions.map

import io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.Conditions.equalTo
import io.github.gromoff97.awium.execution.ConditionExecution.bind
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ValueForConditionTest {
    @Test
    fun `a predicate is called only when the key is present`() {
        val checked = mutableListOf<String>()
        val execution = bind<Map<String, String>, String>(valueFor("key") { value: String -> checked += value; value == "ready" })
        execution.probe(emptyMap())
        execution.probe(mapOf("key" to "pending"))
        execution.probe(mapOf("key" to "ready"))
        assertEquals(listOf("pending", "ready"), checked)
    }

    @Test
    fun `returns the original value for the selected key`() {
        val value = BigDecimal("42.00")
        assertSame(value, result(evaluate(valueFor("key"), mapOf("other" to 0, "key" to value))))
    }

    @Test
    fun `a present null value is a successful selection`() {
        assertNull(result(evaluate(valueFor(arrayOf("nullable")), mapOf(arrayOf("nullable") to null))))
    }

    @Test
    fun `a missing key is a mismatch`() {
        assertEquals("map did not contain the expected key", rejection(valueFor("missing"), mapOf("key" to null)).mismatch())
    }

    @Test
    fun `a null map is a mismatch`() {
        assertEquals("map was null", rejection(valueFor("key"), null).mismatch())
    }

    @Test
    fun `reused execution selects the current value`() {
        val execution = bind<Map<String, String>, String>(valueFor("key"))
        assertEquals("old", result(execution.probe(mapOf("key" to "old"))))
        assertEquals("new", result(execution.probe(mapOf("key" to "new"))))
    }

    @Test
    fun `nested conditions are checked again after success`() {
        val execution = bind<Map<String, String>, String>(valueFor("key", equalTo("ready")))
        assertEquals("ready", result(execution.probe(mapOf("key" to "ready"))))
        assertInstanceOf(Unsatisfied::class.java, execution.probe(mapOf("key" to "changed")))
    }
}

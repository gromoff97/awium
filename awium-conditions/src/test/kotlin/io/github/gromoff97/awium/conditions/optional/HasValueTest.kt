package io.github.gromoff97.awium.conditions.optional

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.notEqualTo
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue
import io.github.gromoff97.awium.conditions.support.Directional
import io.github.gromoff97.awium.execution.ConditionExecution.bind
import java.util.Optional
import org.assertj.core.api.Assertions.assertThatNullPointerException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class HasValueTest {
    @Test
    fun `a predicate is called only for present values`() {
        val checked = mutableListOf<String>()
        val execution = bind<Optional<String>, String>(hasValue { value: String -> checked += value; value == "ready" })
        execution.probe(Optional.empty())
        execution.probe(Optional.of("ready"))
        assertEquals(listOf("ready"), checked)
    }

    @Test
    fun `rejects null`() {
        rejection(hasValue(equalTo("1")), null)
    }

    @Test
    fun `rejects an empty optional`() {
        rejection(hasValue(equalTo("1")), Optional.empty<String>())
    }

    @Test
    fun `returns the contained instance when it equals the expected value`() {
        val actual = String(charArrayOf('1'))
        assertSame(actual, result(evaluate(hasValue(equalTo(String(charArrayOf('1')))), Optional.of(actual))))
    }

    @Test
    fun `returns the contained value when it differs from the excluded value`() {
        val actual = "2"
        assertSame(actual, result(evaluate(hasValue(notEqualTo("1")), Optional.of(actual))))
    }

    @Test
    fun `rejects a contained value that fails the nested check`() {
        rejection(hasValue(equalTo("1")), Optional.of("2"))
    }

    @Test
    fun `rejects the excluded value`() {
        rejection(hasValue(notEqualTo("1")), Optional.of("1"))
    }

    @Test
    fun `calls equals on the contained value`() {
        val actual = Directional(true)
        assertSame(actual, result(evaluate(hasValue(equalTo(Directional(false))), Optional.of(actual))))
        assertEquals(1, actual.equalsCalls)
    }

    @Test
    fun `compares array contents and returns the contained array`() {
        val actual = intArrayOf(1, 2)
        assertSame(actual, result(evaluate(hasValue(equalTo(intArrayOf(1, 2))), Optional.of(actual))))
    }

    @Test
    fun `rejects a missing typed condition immediately`() {
        assertThatNullPointerException().isThrownBy { hasValue(null as Condition<Any, Any>?) }
            .withMessageContaining("condition")
    }

    @Test
    fun `rejects a missing preserving condition immediately`() {
        assertThatNullPointerException().isThrownBy { hasValue(null as PreservingCondition<Any>?) }
            .withMessageContaining("condition")
    }
}

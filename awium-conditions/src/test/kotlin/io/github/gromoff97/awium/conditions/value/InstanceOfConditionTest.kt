package io.github.gromoff97.awium.conditions.value

import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.common.Conditions.exactlyInstanceOf
import io.github.gromoff97.awium.conditions.common.Conditions.instanceOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class InstanceOfConditionTest {
    open class Parent
    class Child : Parent()

    @ParameterizedTest
    @MethodSource("successfulChecks")
    fun `returns the original instance`(condition: Condition<Any, *>) {
        val actual = Child()
        assertSame(actual, result(evaluate(condition, actual)))
    }

    @ParameterizedTest
    @MethodSource("stringChecks")
    fun `null cannot be an instance`(condition: Condition<Any, *>) {
        assertEquals("value was null", rejection(condition, null).mismatch())
    }

    @ParameterizedTest
    @MethodSource("stringChecks")
    fun `an unrelated value reports its actual type`(condition: Condition<Any, *>) {
        assertThat(rejection(condition, 42).mismatch()).contains("java.lang.Integer")
        assertThat(condition.description()).contains("java.lang.String")
    }

    @Test
    fun `an exact check rejects subclasses`() {
        assertThat(rejection(exactlyInstanceOf(Parent::class.java), Child()).mismatch()).contains(Child::class.java.name)
    }

    @Test
    fun `interfaces accept their implementations`() {
        val actual = StringBuilder("ready")
        assertSame(actual, result(evaluate(instanceOf(CharSequence::class.java), actual)))
    }

    @ParameterizedTest
    @MethodSource("factories")
    fun `rejects a missing type immediately`(create: (Class<String>?) -> Condition<Any, String>) {
        val failure = assertThrows(NullPointerException::class.java) { create(null) }
        assertEquals("type must not be null", failure.message)
    }

    companion object {
        @JvmStatic
        fun successfulChecks() = listOf(
            named("exact type", exactlyInstanceOf(Child::class.java)),
            named("same type", instanceOf(Child::class.java)),
            named("parent type", instanceOf(Parent::class.java))
        )

        @JvmStatic
        fun stringChecks() = listOf(named("instance", instanceOf(String::class.java)), named("exact instance", exactlyInstanceOf(String::class.java)))

        @JvmStatic
        fun factories(): List<Named<(Class<String>?) -> Condition<Any, String>>> = listOf(
            named("instance", { instanceOf(it) }),
            named("exact instance", { exactlyInstanceOf(it) })
        )
    }
}

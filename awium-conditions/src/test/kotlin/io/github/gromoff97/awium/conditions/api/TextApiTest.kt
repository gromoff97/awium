package io.github.gromoff97.awium.conditions.api

import consumer.value.TextConsumer
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.conditions.StringConditions
import java.nio.CharBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TextApiTest {
    @ParameterizedTest
    @MethodSource("checks")
    fun `until preserves String`(condition: PreservingCondition<CharSequence>) {
        assertEquals("Ready", TextConsumer.stringPrefix("Ready 42", condition))
    }

    @ParameterizedTest
    @MethodSource("checks")
    fun `until preserves StringBuilder`(condition: PreservingCondition<CharSequence>) {
        val actual = StringBuilder("Ready 42")
        assertEquals(actual.capacity(), TextConsumer.builderCapacity(actual, condition))
    }

    @ParameterizedTest
    @MethodSource("checks")
    fun `tryAwait patterns preserve StringBuilder`(condition: PreservingCondition<CharSequence>) {
        val actual = StringBuilder("Ready 42")
        assertEquals(actual.capacity(), TextConsumer.diagnosticBuilderCapacity(actual, condition))
    }

    @ParameterizedTest
    @MethodSource("checks")
    fun `captured preserves StringBuilder elements`(condition: PreservingCondition<CharSequence>) {
        val actual = StringBuilder("Ready 42")
        assertEquals(actual.capacity(), TextConsumer.capturedBuilderCapacity(actual, condition))
    }

    @Test
    fun `hasValue preserves StringBuilder`() {
        val actual = StringBuilder("ready")
        assertEquals(actual.capacity(), TextConsumer.optionalBuilderCapacity(actual))
    }

    @Test
    fun `valueFor preserves StringBuilder`() {
        val actual = StringBuilder("ready")
        assertEquals(actual.capacity(), TextConsumer.mappedBuilderCapacity(actual))
    }

    @Test
    fun `singleElement preserves CharBuffer`() {
        val actual = CharBuffer.wrap(" ready").position(1)
        assertEquals(1, TextConsumer.selectedBufferPosition(actual))
    }

    @Test
    fun `contentEquals accepts different CharSequence implementations`() {
        val actual = CharBuffer.wrap("ready")
        assertEquals(0, TextConsumer.contentEqualsBufferPosition(actual, StringBuilder("ready")))
    }

    @Test
    fun `nested contentEquals preserves StringBuilder`() {
        val actual = StringBuilder("ready")
        assertEquals(actual.capacity(), TextConsumer.nestedContentEqualsCapacity(actual, CharBuffer.wrap("ready")))
    }

    companion object {
        @JvmStatic
        fun checks() = listOf(
            StringConditions.nonEmpty, StringConditions.nonBlank,
            StringConditions.containsAll(listOf("Ready", "42")), StringConditions.doesNotContain("failed"),
            StringConditions.containsIgnoringCase("READY"), StringConditions.startsWith("Ready"),
            StringConditions.doesNotStartWith("failed"), StringConditions.endsWith("42"),
            StringConditions.doesNotEndWith("failed"), StringConditions.matchesRegex("Ready \\d+"),
            StringConditions.doesNotMatchRegex("failed"), StringConditions.equalToIgnoringCase("ready 42"),
            StringConditions.notEqualToIgnoringCase("failed"), StringConditions.hasLength(8),
            StringConditions.hasLengthNotEqualTo(0), StringConditions.hasLengthGreaterThan(0),
            StringConditions.hasLengthAtLeast(8), StringConditions.hasLengthLessThan(9),
            StringConditions.hasLengthAtMost(8), StringConditions.hasLengthBetween(1, 10)
        ).map { named(it.description(), it) }
    }
}

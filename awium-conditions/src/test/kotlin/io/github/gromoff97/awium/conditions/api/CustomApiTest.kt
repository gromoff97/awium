package io.github.gromoff97.awium.conditions.api

import consumer.value.CustomConsumer
import consumer.value.CheckedCallbackConsumer
import consumer.value.CustomConsumer.User
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.attempts
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.observation
import io.github.gromoff97.awium.result.AwaitResult.Succeeded
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class CustomApiTest {
    @Test
    fun `sources and checks accept method references declaring checked failures`() {
        assertEquals(listOf(5, 5, 5), CheckedCallbackConsumer.checkedReferences())
    }

    @Test
    fun `asserted retries an assertion failure from a checked method reference`() {
        assertEquals(2, CheckedCallbackConsumer.retriedAssertion())
    }

    @ParameterizedTest
    @MethodSource("selectedIds")
    fun `Java infers the result selected by a callback`(select: (User) -> Int) {
        assertEquals(42, select(User(42)))
    }

    @ParameterizedTest
    @MethodSource("selectedUsers")
    fun `Java retains the selected user type and identity`(select: (User) -> User) {
        val actual = User(42)
        assertSame(actual, select(actual))
    }

    @ParameterizedTest
    @MethodSource("entryQuantifiers")
    fun `entry quantifiers accept selected values and return the map`(select: (Map<String, User>) -> Map<String, User>) {
        val actual = mapOf("user" to User(42))
        assertSame(actual, select(actual))
    }

    @Test
    fun `a callback can select null from a non-null source`() {
        assertNull(CustomConsumer.nullSelection(User(42)))
    }

    @Test
    fun `a successful null selection retains the observation in history`() {
        val actual = User(42)
        val result = CustomConsumer.nullSelectionResult(actual)

        assertInstanceOf(Succeeded::class.java, result)
        assertNull(result.orThrow())
        assertSame(actual, observation(attempts(result).single()))
    }

    @Test
    fun `identity callbacks infer their result through factories selectors and capture`() {
        val actual = CustomConsumer.User(42)
        CustomConsumer.inferredIdentities(actual).forEach { assertSame(actual, it) }
    }

    @Test
    fun `callbacks accept composed JDK functions and predicates`() {
        assertEquals(listOf("ready", "ready", "ready", "ready", "first ready", "second ready", "1"), CustomConsumer.composedCallbacks())
    }

    @Test
    fun `evaluator factories retain declared results while capture keeps observations`() {
        assertEquals(List(5) { "ready" }, CustomConsumer.evaluatorResults())
    }

    companion object {
        @JvmStatic
        fun selectedIds() = listOf(
            named("value", CustomConsumer::selectedId),
            named("factory", CustomConsumer::lazySelectedId),
            named("optional", CustomConsumer::optionalId),
            named("single element", CustomConsumer::elementId),
            named("map value", CustomConsumer::valueId),
            named("map entry", CustomConsumer::entryId),
            named("first element", CustomConsumer::firstUserId)
        )

        @JvmStatic
        fun selectedUsers() = listOf(
            named("stored attempt", CustomConsumer::storedAttempt),
            named("callable factory", CustomConsumer::callableFactory),
            named("result pattern", CustomConsumer::recordPattern)
        )

        @JvmStatic
        fun entryQuantifiers() = listOf(
            named("all entries", CustomConsumer::allSelectedIds),
            named("any entry", CustomConsumer::anySelectedId),
            named("no entry", CustomConsumer::noMissingId)
        )
    }
}

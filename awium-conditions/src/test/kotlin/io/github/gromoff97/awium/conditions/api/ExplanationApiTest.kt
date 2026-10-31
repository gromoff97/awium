package io.github.gromoff97.awium.conditions.api

import consumer.value.ExplanationConsumer
import consumer.value.ExplanationConsumer.User
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ExplanationApiTest {
    @Test
    fun `typed lambdas and method references preserve the collection type after because`() {
        val users = arrayListOf(User(true))
        ExplanationConsumer.collections(users).forEach { assertSame(users, it) }
    }

    @Test
    fun `because preserves the selected type across source families`() {
        val user = User(true)
        ExplanationConsumer.selections(user).forEach { assertSame(user, it) }
    }

    @Test
    fun `explained entry predicates accept a broader value type`() {
        val actual = linkedMapOf("ready" to 1)
        ExplanationConsumer.wildcardEntries(actual).forEach { assertSame(actual, it) }
    }
}

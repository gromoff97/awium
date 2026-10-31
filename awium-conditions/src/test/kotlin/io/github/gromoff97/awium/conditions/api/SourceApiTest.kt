package io.github.gromoff97.awium.conditions.api

import consumer.SourceConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SourceApiTest {
    @Test
    fun `covariant views retain selection bounds and accept broader callbacks`() {
        assertEquals(listOf(1, 1, 1, 1, 1), SourceConsumer.typedViews())
    }
}

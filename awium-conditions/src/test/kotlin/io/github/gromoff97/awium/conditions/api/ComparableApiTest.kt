package io.github.gromoff97.awium.conditions.api

import consumer.value.ComparableConsumer.nonComparableBounds
import consumer.value.ComparableConsumer.subclassBounds
import consumer.value.ComparableConsumer.unrelatedComparableBounds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ComparableApiTest {
    @Test
    fun `a single bound need not implement Comparable`() {
        assertThat(nonComparableBounds()).hasSize(14).containsOnly(10)
    }

    @Test
    fun `subclass bounds preserve the declared source type`() {
        assertThat(subclassBounds()).hasSize(18).containsOnly(10)
    }

    @Test
    fun `the observation can compare with another type`() {
        assertThat(unrelatedComparableBounds()).containsExactly(10, 10, 10, 10, 10, 10, 10, 1)
    }
}

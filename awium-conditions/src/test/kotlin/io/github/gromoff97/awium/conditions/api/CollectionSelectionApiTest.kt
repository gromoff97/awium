package io.github.gromoff97.awium.conditions.api

import consumer.collection.SelectionConsumer
import consumer.collection.SelectionConsumer.User
import java.util.ArrayDeque
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class CollectionSelectionApiTest {
    @Test
    fun `selectors return the declared narrowed type`() {
        assertEquals(listOf(1, 2, 1, 1), SelectionConsumer.narrowedIds())
    }

    @Test
    fun `checks and comparisons preserve the source element type`() {
        assertEquals(listOf(1, 2, 1, 1, 2, 2, 1, 2, 1), SelectionConsumer.checkedIds())
    }

    @Test
    fun `nested selectors infer the selected type across containers`() {
        assertEquals(listOf(1, 2, 1, 2, 1), SelectionConsumer.nestedIds())
    }

    @Test
    fun `indexed selectors support deques and reversed views`() {
        assertEquals(listOf(2, 2, 2, 2, 2, 1), SelectionConsumer.dequeIds())
    }

    @ParameterizedTest
    @MethodSource("captures")
    fun `captured with a reason retains complete source observations`(capture: (List<User>) -> List<List<User>>) {
        val actual = listOf(User(1))
        val observations = capture(actual)
        assertEquals(2, observations.size)
        observations.forEach { assertSame(actual, it) }
    }

    @Test
    fun `captured retains the concrete deque type`() {
        val actual = ArrayDeque(listOf(User(1), User(2)))
        val observations = SelectionConsumer.capturedQueue(actual)
        assertEquals(3, observations.size)
        observations.forEach { assertSame(actual, it) }
        assertEquals(2, observations.last().peekLast().id())
    }

    @Test
    fun `captured retains the concrete sequenced set type`() {
        val actual = linkedSetOf(User(1), User(2))
        val observations = SelectionConsumer.capturedSet(actual)
        assertEquals(3, observations.size)
        observations.forEach { assertSame(actual, it) }
        assertEquals(2, observations.last().last().id())
    }

    @Test
    fun `a narrower comparison preserves the observed element type`() {
        assertEquals(5, SelectionConsumer.narrowerExpectedType())
    }

    @Test
    fun `single element accepts checks assertions and comparisons`() {
        val user = User(1)
        SelectionConsumer.singleChecks(arrayListOf(user)).forEach { assertSame(user, it) }
    }

    @Test
    fun `single element narrows Number to Integer`() {
        assertEquals(42, SelectionConsumer.singleNarrowed(listOf(42)))
    }

    @Test
    fun `single element accepts a null selection in each overload`() {
        SelectionConsumer.nullableSingle().forEach { assertNull(it) }
    }

    @Test
    fun `single element infers the type of a map key view`() {
        assertEquals("key", SelectionConsumer.singleMapKey())
    }

    companion object {
        @JvmStatic
        fun captures() = listOf(
            named("selectors", SelectionConsumer::capturedSelectors),
            named("recorded selectors", SelectionConsumer::recordedSelectors),
            named("comparison and selector", SelectionConsumer::capturedComparison),
            named("recorded comparison and selector", SelectionConsumer::recordedComparison)
        )
    }
}

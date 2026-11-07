package io.github.gromoff97.awium.conditions.history

import io.github.gromoff97.awium.dsl.AwaitCondition.definitionOf

import io.github.gromoff97.awium.dsl.Await.tryAwait
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.attempts
import io.github.gromoff97.awium.conditions.common.CommonConditions.instanceOf
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue
import io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation
import java.time.Duration.ofNanos
import java.util.Optional
import java.util.concurrent.Callable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class NestedHistoryTest {
    @Test
    fun `repeated selectors group retries separately at each nesting level`() {
        val emptyOuter = emptyList<List<String>>()
        val emptyInner = listOf(emptyList<String>())
        val source = listOf(emptyOuter, emptyOuter, emptyInner, emptyInner, listOf(listOf("ready"))).iterator()
        val check = singleElement(singleElement(instanceOf(String::class.java)))
        val result = tryAwait(Callable { source.next() }).every(ofNanos(1)).until(check)

        assertEquals("ready", result.orThrow())
        assertEquals(listOf(2L, 4L, 5L), result.conditions().single().attempts().keys.toList())
        assertEquals(listOf("collection size was 0", "single element: collection size was 0"),
            attempts(result).take(2).map { assertInstanceOf<UnsatisfiedObservation<*>>(it).mismatch() })
        assertSame(check, definitionOf(result.conditions().single().condition()).orElseThrow())
    }

    @Test
    fun `nested selections retain paths through different container kinds`() {
        val source = listOf(Optional.empty<List<List<String>>>(), Optional.of(emptyList()),
            Optional.of(listOf(emptyList())), Optional.of(listOf(listOf("ready")))).iterator()
        val result = tryAwait(Callable { source.next() }).every(ofNanos(1))
            .until(hasValue(singleElement(singleElement(instanceOf(String::class.java)))))

        assertEquals("ready", result.orThrow())
        assertEquals(listOf("optional was empty", "optional value: collection size was 0",
            "optional value: single element: collection size was 0"),
            attempts(result).take(3).map { assertInstanceOf<UnsatisfiedObservation<*>>(it).mismatch() })
    }
}

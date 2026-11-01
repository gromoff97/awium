package io.github.gromoff97.awium.conditions.composition

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.all
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.any
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.none
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.firstElement
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.lastElement
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.elementAt
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.Conditions.matches
import io.github.gromoff97.awium.conditions.map.MapConditions.allEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.allKeys
import io.github.gromoff97.awium.conditions.map.MapConditions.allValues
import io.github.gromoff97.awium.conditions.map.MapConditions.anyKey
import io.github.gromoff97.awium.conditions.map.MapConditions.anyValue
import io.github.gromoff97.awium.conditions.map.MapConditions.hasEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.hasNoEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.noKey
import io.github.gromoff97.awium.conditions.map.MapConditions.noValue
import io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue
import io.github.gromoff97.awium.conditions.function.CheckedBiPredicate
import io.github.gromoff97.awium.conditions.function.CheckedPredicate
import java.io.IOException
import java.util.Optional
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class PredicateFailureTest {
    data class User(val id: Int)

    class Predicates(cause: Throwable) {
        var calls = 0
        val value = CheckedPredicate<User> { calls++; throw cause }
        val key = CheckedPredicate<String> { calls++; throw cause }
        val entry = CheckedBiPredicate<String, User> { _, _ -> calls++; throw cause }
        val entryObject = CheckedPredicate<Map.Entry<String, User>> { value.test(it.value) }
    }

    @AfterEach
    fun `clear the caller interrupt flag`() { Thread.interrupted() }

    @ParameterizedTest
    @MethodSource("predicates")
    fun `a predicate failure stops the condition after one callback`(
        actual: Any, create: (Predicates) -> AwaitCondition, cause: Throwable
    ) {
        val predicates = Predicates(cause)
        assertSame(cause, thrown(create(predicates), actual))
        assertEquals(1, predicates.calls)
        assertFalse(Thread.currentThread().isInterrupted)
    }

    companion object {
        @JvmStatic
        fun predicates() = cases().flatMap { (name, actual, create) ->
            listOf(IOException("checked"), AssertionError("assertion"), Throwable("direct"),
                InterruptedException("ordinary condition failure")).map { cause ->
                argumentSet("$name / ${cause.javaClass.simpleName}", actual, create, cause)
            }
        }

        private fun cases(): List<Triple<String, Any, (Predicates) -> AwaitCondition>> {
            val first = User(1)
            val last = User(2)
            val users = listOf(first, last)
            val entries = mapOf("first" to first, "last" to last)
            return listOf(
                Triple("matches", first, { matches(it.value) }),
                Triple("hasValue", Optional.of(first), { hasValue(it.value) }),
                Triple("singleElement matches", listOf(first), { singleElement(matches(it.value)) }),
                Triple("singleElement predicate", listOf(first), { singleElement(it.value) }),
                Triple("firstElement", users, { firstElement(it.value) }),
                Triple("lastElement", users, { lastElement(it.value) }),
                Triple("elementAt", users, { elementAt(0, it.value) }),
                Triple("all", users, { all(it.value) }),
                Triple("any", users, { any(it.value) }),
                Triple("none", users, { none(it.value) }),
                Triple("valueFor", entries, { valueFor("first", it.value) }),
                Triple("singleEntry pair", mapOf("first" to first), { singleEntry(it.entry) }),
                Triple("singleEntry object", mapOf("first" to first), { singleEntry(it.entryObject) }),
                Triple("allEntries pair", entries, { allEntries(it.entry) }),
                Triple("allEntries object", entries, { allEntries(it.entryObject) }),
                Triple("hasEntry pair", entries, { hasEntry(it.entry) }),
                Triple("hasEntry object", entries, { hasEntry(it.entryObject) }),
                Triple("hasNoEntry pair", entries, { hasNoEntry(it.entry) }),
                Triple("hasNoEntry object", entries, { hasNoEntry(it.entryObject) }),
                Triple("allKeys", entries, { allKeys(it.key) }),
                Triple("anyKey", entries, { anyKey(it.key) }),
                Triple("noKey", entries, { noKey(it.key) }),
                Triple("allValues", entries, { allValues(it.value) }),
                Triple("anyValue", entries, { anyValue(it.value) }),
                Triple("noValue", entries, { noValue(it.value) })
            )
        }
    }
}

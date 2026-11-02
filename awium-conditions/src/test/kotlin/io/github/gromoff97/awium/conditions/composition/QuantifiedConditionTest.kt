package io.github.gromoff97.awium.conditions.composition

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.AwaitCondition.InputCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.all
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.any
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.none
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.preservesInstance
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.common.CommonConditions.captured
import io.github.gromoff97.awium.conditions.common.CommonConditions.condition
import io.github.gromoff97.awium.conditions.common.CommonConditions.conditionPerWait
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.instanceOf
import io.github.gromoff97.awium.conditions.common.CommonConditions.matches
import io.github.gromoff97.awium.conditions.map.MapConditions.hasEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.hasNoEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.allEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.allKeys
import io.github.gromoff97.awium.conditions.map.MapConditions.allValues
import io.github.gromoff97.awium.conditions.map.MapConditions.anyKey
import io.github.gromoff97.awium.conditions.map.MapConditions.anyValue
import io.github.gromoff97.awium.conditions.map.MapConditions.noKey
import io.github.gromoff97.awium.conditions.map.MapConditions.noValue
import io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.present
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.function.AwaitAttempt.bind
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.function.checked.CheckedPredicate
import io.github.gromoff97.awium.result.AttemptResult.Satisfied
import io.github.gromoff97.awium.result.AttemptResult.Unsatisfied
import io.github.gromoff97.awium.result.AttemptResult.satisfied
import io.github.gromoff97.awium.result.AttemptResult.unsatisfied
import java.io.IOException
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class QuantifiedConditionTest {
    data class User(val active: Boolean)
    data class Example(val condition: AwaitCondition, val matching: Any, val mismatching: Any)

    @AfterEach
    fun `clear the caller interrupt flag`() {
        Thread.interrupted()
    }

    @ParameterizedTest
    @MethodSource("conditionFamilies")
    fun `matching inputs preserve the observation`(example: Example) {
        preservesInstance(example.condition, example.matching)
    }

    @ParameterizedTest
    @MethodSource("conditionFamilies")
    fun `rejects a mismatching nested condition`(example: Example) {
        rejection(example.condition, example.mismatching)
    }

    @ParameterizedTest
    @MethodSource("conditionFamilies")
    fun `null containers are mismatches`(example: Example) {
        assertThat(rejection(example.condition, null).mismatch()).endsWith("was null")
    }

    @ParameterizedTest
    @MethodSource("shortCircuits")
    fun `stops at the first decisive candidate`(factory: (InputCondition<User>) -> AwaitCondition, actual: List<User>) {
        var calls = 0
        evaluate(factory(matches { user: User -> calls++; user.active }), actual)
        assertEquals(1, calls)
    }

    @ParameterizedTest
    @MethodSource("emptyInputs")
    fun `empty inputs do not initialize nested state`(factory: (InputCondition<User>) -> AwaitCondition, successful: Boolean) {
        val forbidden = conditionPerWait<User, User>("must stay lazy") { error("empty input created nested state") }
        assertEquals(successful, evaluate(factory(forbidden), emptyList<User>()) is Satisfied<*>)
    }

    @Test
    fun `each execution shares state across candidates and starts its own evaluator`() {
        var factories = 0
        val nested = conditionPerWait<User, User>("second check") {
            factories++
            var calls = 0
            AwaitAttempt { actual -> if (++calls == 2) satisfied(actual) else unsatisfied("waiting for a second observation") }
        }
        val actual = listOf(ready, ready)
        repeat(2) {
            assertSame(actual, result(bind<List<User>>(any(nested)).probe(actual)))
        }
        assertEquals(2, factories)
    }

    @Test
    fun `captured positions get independent nested state`() {
        var factories = 0
        val nested = conditionPerWait<User, User>("second check") {
            factories++
            var calls = 0
            AwaitAttempt { actual -> if (++calls == 2) satisfied(actual) else unsatisfied("waiting for a second observation") }
        }
        val actual = listOf(ready, ready)
        val execution = bind<List<User>>(captured(any(nested), any(nested)))

        assertInstanceOf<Unsatisfied<*>>(execution.probe(actual))
        assertEquals(listOf(actual, actual), result(execution.probe(actual)))
        assertEquals(2, factories)
    }

    @Test
    fun `nested mismatches preserve their cause and message`() {
        val cause = IOException("not ready")
        val nested = condition<User, User>("ready") { unsatisfied("pending state", cause) }
        val rejected = rejection(all(nested), listOf(pending))
        assertSame(cause, rejected.cause())
        assertThat(rejected.mismatch()).contains("pending state")
    }

    @Test
    fun `nested lookup mismatches retain the key reference`() {
        val condition = any(valueFor("user", instanceOf(User::class.java)))
        rejection(condition, listOf(emptyMap<String, User>()))
        assertEquals("user", condition.references().first().value())
    }

    @Test
    fun `failed nested selectors reject the container without losing its reason`() {
        val condition = all(present).because("each user is available")
        rejection(condition, listOf(Optional.empty(), Optional.of(ready)))
        assertEquals("each user is available", condition.explanation())
    }

    @ParameterizedTest
    @MethodSource("failures")
    fun `a thrown failure stops before the next candidate`(cause: Throwable) {
        var calls = 0
        val nested = matches { _: User -> calls++; throw cause }
        assertSame(cause, thrown(any(nested), listOf(ready, pending)))
        assertEquals(1, calls)
    }

    @Test
    fun `interruption stops before the next candidate`() {
        var calls = 0
        val nested = matches { _: User -> calls++; Thread.currentThread().interrupt(); false }
        assertInstanceOf<InterruptedException>(thrown(any(nested), listOf(ready, pending)))
        assertEquals(1, calls)
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @Test
    fun `a flag cleared within the callback does not interrupt iteration`() {
        val actual = listOf(ready, pending)
        val check = all(matches { _: User ->
            Thread.currentThread().interrupt()
            Thread.interrupted()
            true
        })
        preservesInstance(check, actual)
        assertFalse(Thread.currentThread().isInterrupted)
    }

    @Test
    fun `a callback failure after interruption keeps its original cause`() {
        val cause = IllegalStateException("callback failed")
        var calls = 0
        val check = all(matches { _: User -> calls++; Thread.currentThread().interrupt(); throw cause })
        assertSame(cause, thrown(check, listOf(ready, pending)))
        assertEquals(1, calls)
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @ParameterizedTest
    @MethodSource("missingEntries")
    fun `entry checks reject missing and mismatching entries`(condition: AwaitCondition, actual: Map<String, String>) {
        rejection(condition, actual)
    }

    @ParameterizedTest
    @MethodSource("entryChecks")
    fun `entry checks retain the supplied reason`(condition: AwaitCondition) {
        assertEquals("ready entry required", condition.explanation())
    }

    @ParameterizedTest
    @MethodSource("forbiddenEntries")
    fun `entry absence rejects a matching entry`(condition: AwaitCondition) {
        rejection(condition, mapOf("ready" to "0", "failed" to "1"))
    }

    @ParameterizedTest
    @MethodSource("entryFailures")
    fun `entry callbacks preserve the original checked failure`(factory: (CheckedPredicate<Map.Entry<String, Int>>) -> AwaitCondition) {
        val cause = IOException("entry failed")
        val condition = factory(CheckedPredicate { throw cause })
        assertSame(cause, thrown(condition, mapOf("key" to 1)))
    }

    companion object {
        private val ready = User(true)
        private val pending = User(false)
        private class UserEntry : SimpleImmutableEntry<String, User>("user", ready)

        @JvmStatic
        fun conditionFamilies(): List<org.junit.jupiter.params.provider.Arguments> {
            val key = matches { value: String? -> value == "user" }
            val user = matches { value: User? -> value == ready }
            val entry = matches { value: Map.Entry<String?, User?> -> value.key == "user" && value.value == ready }
            val expectedEntry = equalTo(java.util.Map.entry("user", ready))
            val readyMap = EntryMap(listOf(UserEntry()))
            val pendingMap = EntryMap(listOf(SimpleImmutableEntry<String?, User?>(null, null)))
            val mixedMap = EntryMap(listOf(SimpleImmutableEntry<String?, User?>(null, null), UserEntry()))
            val mapAll = listOf<AwaitCondition>(
                allKeys(key), allKeys(equalTo("user")), allKeys(instanceOf(String::class.java)),
                allValues(user), allValues(equalTo(ready)), allValues(instanceOf(User::class.java)),
                allEntries(entry), allEntries(expectedEntry), allEntries<String, User>(instanceOf(UserEntry::class.java)))
            val mapAny = listOf<AwaitCondition>(
                anyKey(key), anyKey(equalTo("user")), anyKey(instanceOf(String::class.java)),
                anyValue(user), anyValue(equalTo(ready)), anyValue(instanceOf(User::class.java)),
                hasEntry(entry), hasEntry(expectedEntry), hasEntry<String, User>(instanceOf(UserEntry::class.java)))
            val mapAbsences = listOf<AwaitCondition>(noKey(key), noKey(equalTo("user")), noKey(instanceOf(String::class.java)),
                noValue(user), noValue(equalTo(ready)), noValue(instanceOf(User::class.java)),
                hasNoEntry(entry), hasNoEntry(expectedEntry), hasNoEntry<String, User>(instanceOf(UserEntry::class.java)))
            val collections = listOf(
                Example(all(equalTo(ready)), listOf(ready), listOf(pending, ready)),
                Example(all(instanceOf(String::class.java)), listOf("ready"), listOf(ready, "ready")),
                Example(all(matches { value: User -> value.active }), listOf(ready), listOf(pending, ready)),
                Example(any(equalTo(ready)), listOf(pending, ready), listOf(pending)),
                Example(any(instanceOf(String::class.java)), listOf(ready, "ready"), listOf(ready)),
                Example(any(matches { value: User -> value.active }), listOf(pending, ready), listOf(pending)),
                Example(none(equalTo(ready)), listOf(pending), listOf(ready)),
                Example(none(instanceOf(User::class.java)), listOf("skip"), listOf(ready)),
                Example(none(matches { value: User -> value.active }), listOf(pending), listOf(ready)))
            return mapAll.map { argumentSet(it.description(), Example(it, readyMap, mixedMap)) } +
                mapAny.map { argumentSet(it.description(), Example(it, mixedMap, pendingMap)) } +
                mapAbsences.map { argumentSet(it.description(), Example(it, pendingMap, readyMap)) } +
                mapPredicates().map { argumentSet(it.condition.description(), it) } +
                collections.map { argumentSet(it.condition.description(), it) }
        }

        private val allUsers: (InputCondition<User>) -> AwaitCondition = { all(it) }
        private val anyUser: (InputCondition<User>) -> AwaitCondition = { any(it) }
        private val noUser: (InputCondition<User>) -> AwaitCondition = { none(it) }

        @JvmStatic
        fun shortCircuits() = listOf(
            argumentSet("all", allUsers, listOf(pending, ready)), argumentSet("any", anyUser, listOf(ready, pending)),
            argumentSet("none", noUser, listOf(ready, pending)))

        @JvmStatic
        fun emptyInputs() = listOf(argumentSet("all", allUsers, true), argumentSet("any", anyUser, false), argumentSet("none", noUser, true))

        @JvmStatic
        fun failures() = listOf(argumentSet("checked", IOException("checked")), argumentSet("assertion", AssertionError("assertion")),
            argumentSet("throwable", Throwable("failure")))

        private val entries = listOf<Pair<String, AwaitCondition>>(
            "exact" to hasEntry("ready", "1").because("ready entry required"),
            "predicate" to hasEntry { entry: Map.Entry<String, String> -> entry.key == "ready" && entry.value == "1" }
                .because("ready entry required"),
            "nested" to hasEntry(matches { entry: Map.Entry<String, String> -> entry.key == "ready" && entry.value == "1" })
                .because("ready entry required"))

        @JvmStatic
        fun entryChecks() = entries.map { (name, condition) -> argumentSet(name, condition) }

        @JvmStatic
        fun missingEntries() = entries.flatMap { (name, condition) ->
            listOf(emptyMap(), mapOf("other" to "1"), mapOf("ready" to "0")).map { actual ->
                argumentSet("$name / $actual", condition, actual)
            }
        }

        @JvmStatic
        fun forbiddenEntries() = listOf(
            argumentSet("exact", hasNoEntry("failed", "1")),
            argumentSet("predicate", hasNoEntry { entry: Map.Entry<String, String> -> entry.key == "failed" && entry.value == "1" }),
            argumentSet("explained", hasNoEntry { entry: Map.Entry<String, String> -> entry.key == "failed" && entry.value == "1" }
                .because("failed entry must be absent")))

        private fun mapPredicates(): List<Example> {
            val one = mapOf("a" to 1)
            val mixed = linkedMapOf("a" to 1, "b" to 2)
            return listOf(
                Example(allEntries { _: String, value: Int -> value < 2 }, one, mixed),
                Example(hasEntry { _: String, value: Int -> value == 2 }, mixed, one),
                Example(hasNoEntry { _: String, value: Int -> value == 2 }, one, mixed),
                Example(allKeys { key: String -> key == "a" }, one, mixed),
                Example(anyKey { key: String -> key == "b" }, mixed, one),
                Example(noKey { key: String -> key == "b" }, one, mixed),
                Example(allValues { value: Int -> value < 2 }, one, mixed),
                Example(anyValue { value: Int -> value == 2 }, mixed, one),
                Example(noValue { value: Int -> value == 2 }, one, mixed))
        }

        @JvmStatic
        fun entryFailures() = listOf(
            argumentSet("single entry", { predicate: CheckedPredicate<Map.Entry<String, Int>> -> singleEntry(predicate) }),
            argumentSet("all entries", { predicate: CheckedPredicate<Map.Entry<String, Int>> -> allEntries(predicate) }))
    }
}

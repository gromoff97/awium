package io.github.gromoff97.awium.conditions.composition

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.AwaitCondition.InputCondition
import io.github.gromoff97.awium.conditions.CollectionConditions.all
import io.github.gromoff97.awium.conditions.CollectionConditions.any
import io.github.gromoff97.awium.conditions.CollectionConditions.none
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.evaluate
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.result
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.rejection
import io.github.gromoff97.awium.conditions.Conditions.captured
import io.github.gromoff97.awium.conditions.Conditions.condition
import io.github.gromoff97.awium.conditions.Conditions.conditionPerWait
import io.github.gromoff97.awium.conditions.Conditions.equalTo
import io.github.gromoff97.awium.conditions.Conditions.instanceOf
import io.github.gromoff97.awium.conditions.Conditions.matches
import io.github.gromoff97.awium.conditions.MapConditions.hasEntry
import io.github.gromoff97.awium.conditions.MapConditions.hasNoEntry
import io.github.gromoff97.awium.conditions.MapConditions.allEntries
import io.github.gromoff97.awium.conditions.MapConditions.allKeys
import io.github.gromoff97.awium.conditions.MapConditions.allValues
import io.github.gromoff97.awium.conditions.MapConditions.anyKey
import io.github.gromoff97.awium.conditions.MapConditions.anyValue
import io.github.gromoff97.awium.conditions.MapConditions.noKey
import io.github.gromoff97.awium.conditions.MapConditions.noValue
import io.github.gromoff97.awium.conditions.MapConditions.singleEntry
import io.github.gromoff97.awium.conditions.MapConditions.valueFor
import io.github.gromoff97.awium.conditions.support.ConditionAssertions.thrown
import io.github.gromoff97.awium.conditions.OptionalConditions.present
import io.github.gromoff97.awium.conditions.support.ProbeContainers.EntryMap
import io.github.gromoff97.awium.execution.ConditionExecution.bind
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.conditions.function.CheckedPredicate
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import io.github.gromoff97.awium.result.ConditionResult.satisfied
import io.github.gromoff97.awium.result.ConditionResult.unsatisfied
import java.io.IOException
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
        assertSame(example.matching, result(evaluate(example.condition, example.matching)))
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
            AwaitAttempt { actual -> if (++calls == 2) satisfied(actual) else unsatisfied() }
        }
        val actual = listOf(ready, ready)
        repeat(2) {
            assertSame(actual, result(bind<List<User>, List<User>>(any(nested)).probe(actual)))
        }
        assertEquals(2, factories)
    }

    @Test
    fun `captured positions get independent nested state`() {
        var factories = 0
        val nested = conditionPerWait<User, User>("second check") {
            factories++
            var calls = 0
            AwaitAttempt { actual -> if (++calls == 2) satisfied(actual) else unsatisfied() }
        }
        val actual = listOf(ready, ready)
        val execution = bind<List<User>, List<List<User>>>(captured(any(nested), any(nested)))

        assertInstanceOf(Unsatisfied::class.java, execution.probe(actual))
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
        assertInstanceOf(InterruptedException::class.java, thrown(any(nested), listOf(ready, pending)))
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
        assertSame(actual, result(evaluate(check, actual)))
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
    @MethodSource("mapMismatches")
    fun `map quantifiers reject a decisive mismatch`(condition: AwaitCondition) {
        rejection(condition, linkedMapOf("a" to 1, "b" to 2))
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
            val mapMatches = listOf<AwaitCondition>(
                allKeys(key), allKeys(equalTo("user")), allKeys(instanceOf(String::class.java)),
                anyKey(key), anyKey(equalTo("user")), anyKey(instanceOf(String::class.java)),
                allValues(user), allValues(equalTo(ready)), allValues(instanceOf(User::class.java)),
                anyValue(user), anyValue(equalTo(ready)), anyValue(instanceOf(User::class.java)),
                allEntries(entry), allEntries(expectedEntry), allEntries<String, User>(instanceOf(UserEntry::class.java)),
                hasEntry(entry), hasEntry(expectedEntry), hasEntry<String, User>(instanceOf(UserEntry::class.java)))
            val mapAbsences = listOf<AwaitCondition>(noKey(key), noKey(equalTo("user")), noKey(instanceOf(String::class.java)),
                noValue(user), noValue(equalTo(ready)), noValue(instanceOf(User::class.java)),
                hasNoEntry(entry), hasNoEntry(expectedEntry), hasNoEntry<String, User>(instanceOf(UserEntry::class.java)))
            val collections = listOf(
                Example(all(equalTo(ready)), listOf(ready), listOf(pending)),
                Example(all(instanceOf(String::class.java)), listOf("ready"), listOf(ready)),
                Example(all(matches { value: User -> value.active }), listOf(ready), listOf(pending)),
                Example(any(equalTo(ready)), listOf(ready), listOf(pending)),
                Example(any(instanceOf(String::class.java)), listOf("ready"), listOf(ready)),
                Example(any(matches { value: User -> value.active }), listOf(ready), listOf(pending)),
                Example(none(equalTo(ready)), listOf(pending), listOf(ready)),
                Example(none(instanceOf(User::class.java)), listOf("skip"), listOf(ready)),
                Example(none(matches { value: User -> value.active }), listOf(pending), listOf(ready)))
            return mapMatches.map { argumentSet(it.description(), Example(it, readyMap, pendingMap)) } +
                mapAbsences.map { argumentSet(it.description(), Example(it, pendingMap, readyMap)) } +
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

        @JvmStatic
        fun mapMismatches() = listOf(
            argumentSet("all entries", allEntries { _: String, value: Int -> value < 2 }),
            argumentSet("some entry", hasEntry { _: String, value: Int -> value > 2 }),
            argumentSet("no entry", hasNoEntry { _: String, value: Int -> value == 2 }),
            argumentSet("all keys", allKeys { key: String -> key == "a" }),
            argumentSet("any key", anyKey { key: String -> key == "missing" }),
            argumentSet("no key", noKey { key: String -> key == "a" }),
            argumentSet("all values", allValues { value: Int -> value < 2 }),
            argumentSet("any value", anyValue { value: Int -> value > 2 }),
            argumentSet("no value", noValue { value: Int -> value == 2 }))

        @JvmStatic
        fun entryFailures() = listOf(
            argumentSet("single entry", { predicate: CheckedPredicate<Map.Entry<String, Int>> -> singleEntry(predicate) }),
            argumentSet("all entries", { predicate: CheckedPredicate<Map.Entry<String, Int>> -> allEntries(predicate) }))
    }
}

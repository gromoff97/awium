package io.github.gromoff97.awium.conditions.api

import org.junit.jupiter.api.Test

class ConditionTypeCompilationContractTest {

    @Test
    fun `custom checks cannot return an attempt failure`() {
        for (expression in listOf(
                """Conditions.condition("failed", value -> failure);""",
                """Conditions.conditionPerWait("failed", () -> value -> failure);""")) {
            CompilationSupport.assertRejected("""
                    package io.github.gromoff97.awium;
                    import io.github.gromoff97.awium.conditions.*;
                    import io.github.gromoff97.awium.result.Attempt;
                    final class Contract {
                        void check() {
                            var failure = new Attempt.FailedAfterObservation<>("actual", Attempt.Operation.CONDITION, new IllegalStateException(), null);
                            %s
                        }
                    }
                    """.trimIndent().format(expression))
        }
    }

    @Test
    fun `custom checks reject incompatible input and result types`() {
        for (expression in listOf(
                """String wrong = await(numbers).until(condition("identity", value -> ConditionResult.satisfied(value)));""",
                """AwaitResult<Number, String> wrong = tryAwait(numbers).until(condition("identity", value -> ConditionResult.satisfied(value)));""",
                """List<String> wrong = await(numbers).until(captured(condition("first", value -> ConditionResult.satisfied(value)), isNotNull));""",
                """Condition<Number, String> wrong = condition("transform", value -> ConditionResult.satisfied(value));""",
                "await(() -> 123).until(new consumer.ArtifactConsumer.Length());",
                """String wrong = await(() -> "ready").until(new consumer.ArtifactConsumer.Length());""",
                """String wrong = await(numbers).until(conditionPerWait("identity", () -> value -> ConditionResult.satisfied(value)));""",
                """await(numbers).until(condition("length", (String value) -> ConditionResult.satisfied(value.length())));""",
                """String wrong = await(numbers).until(condition("id", (Number value) -> ConditionResult.satisfied(value.intValue())).because("reason"));""",
                """await(() -> Map.of("key", 1)).until(MapConditions.hasEntry(condition("wrong value", """
                        + "(Map.Entry<String, String> entry) -> ConditionResult.satisfied(entry.getValue()))));")) {
            assertRejected(expression)
        }
    }

    @Test
    fun `explanations cannot weaken input or result types`() {
        for (expression in listOf(
                """await(() -> "ready").until(equalTo(123).because("reason"));""",
                """await(collection::call).until(singleElement(matches(String::isBlank)).because("reason"));""",
                """String wrong = await(optional::call).until(present.because("reason"));""",
                """String wrong = await(collection::call).until(singleElement.because("reason"));""",
                """String wrong = await(map::call).until(valueFor("key").because("reason"));""",
                """Integer wrong = await(() -> "ready").until(instanceOf(String.class).because("reason"));""",
                """await(() -> "ready").until(captured(isNotNull, equalTo(123)).because("reason"));""",
                """List<String> wrong = await(optional::call).until(captured(present, present).because("reason"));""",
                """List<String> wrong = await(collection::call).until(captured(singleElement, singleElement).because("reason"));""",
                """List<String> wrong = await(map::call).until(captured(valueFor("key"), valueFor("key")).because("reason"));""",
                """List<Integer> wrong = await(() -> "ready").until(captured(instanceOf(String.class)).because("reason"));""")) {
            assertRejected(expression)
        }
    }

    @Test
    fun `comparisons reject incompatible expected types`() {
        for (factory in listOf("await", "tryAwait")) {
            for (condition in listOf("equalTo(123)",
                "notEqualTo(123)",
                "sameAs(123)",
                "notSameAs(123)",
                "in(List.of(123, 456))",
                "notIn(List.of(123, 456))")) {
                assertRejected("""%s(() -> "ready").until(%s);""".format(factory, condition))
            }
        }
    }

    @Test
    fun `subset rejects incompatible element types`() {
        for (factory in listOf("await", "tryAwait")) {
            for (expression in listOf(
                    """%s(() -> List.of("ready")).until(CollectionConditions.subsetOf(List.of(123)));""",
                    """%s(() -> List.of("ready")).until(captured(CollectionConditions.subsetOf(List.of(123)), isNotNull));""",
                    """%s(() -> List.of(List.of("ready"))).until(singleElement(CollectionConditions.subsetOf(List.of(123))));""")) {
                assertRejected(expression.format(factory))
            }
        }
    }

    @Test
    fun `nested comparisons reject incompatible expected types`() {
        for (factory in listOf("await", "tryAwait")) {
            for (expression in listOf(
                    """%s(optional::call).until(hasValue(equalTo("wrong")));""",
                    """%s(collection::call).until(singleElement(equalTo("wrong")));""",
                    """%s(map::call).until(valueFor("key", equalTo("wrong")));""",
                    "%s(map::call).until(singleEntry(equalTo(Map.entry(123, 1))));",
                    """%s(map::call).until(singleEntry(equalTo(Map.entry("key", "wrong"))));""")) {
                assertRejected(expression.format(factory))
            }
        }
    }

    @Test
    fun `selector constraints also apply to views and mixed capture sequences`() {
        for (factory in listOf("await", "tryAwait")) {
            for (expression in listOf(
                    """%s(new CollectionViewSource<Number, List<? extends Number>>(() -> List.of(1))).until(singleElement(equalTo("wrong")).because("reason"));""",
                    """%s(new MapViewSource<String, Number, Map<String, ? extends Number>>(() -> Map.of("key", 1))).until(valueFor("key", equalTo("wrong")).because("reason"));""",
                    """%s(collection::call).until(captured(isNotNull, singleElement(equalTo("wrong"))));""",
                    """%s(optional::call).until(captured(isNotNull, hasValue(equalTo("wrong"))));""",
                    """%s(map::call).until(captured(isNotNull, valueFor("key", equalTo("wrong"))));""",
                    """%s(collection::call).until(captured(singleElement(equalTo("wrong")), singleElement((Number value) -> true), isNotNull));""",
                    """%s(optional::call).until(captured(hasValue(equalTo("wrong")), hasValue((Number value) -> true), isNotNull));""",
                    """%s(map::call).until(captured(valueFor("key", equalTo("wrong")), valueFor("key", (Number value) -> true), isNotNull));""",
                    "%s(map::call).until(captured(isNotNull, singleEntry(matches((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null))));",
                    "%s(collection::call).until(present);",
                    "%s(optional::call).until(singleElement);",
                    "%s(map::call).until(singleElement);")) {
                assertRejected(expression.format(factory))
            }
        }
    }

    @Test
    fun `mixed value stages retain both input constraints`() {
        for (factory in listOf("await", "tryAwait")) {
            for (arguments in listOf(
                    "equalTo(123), isNotNull",
                    "isNotNull, equalTo(123)",
                    "equalTo(new Object()), matches(String::isBlank)",
                    """equalTo("ready"), matches((Integer value) -> true)""",
                    "matches(String::isBlank), notIn(List.of(123))")) {
                assertRejected("""%s(() -> "ready").until(captured(%s));""".format(factory, arguments))
            }
        }
        assertRejected("""List<Integer> wrong = await(() -> "ready").until(captured(equalTo("ready"), isNotNull));""")
        assertRejected("""await(() -> "ready").<Object>until(captured(equalTo(123), isNotNull));""")
    }

    @Test
    fun `preserving checks cannot be assigned arbitrary result types`() {
        assertRejected("Condition<Object, Object> erased = isNotNull;")
        assertRejected("""Condition<String, String> erased = equalTo("ready");""")
    }

    @Test
    fun `map lookups reject incompatible key types`() {
        for (factory in listOf("await", "tryAwait")) {
            for (condition in listOf("valueFor(123)", "entryFor(123)",
                    "valueFor(123, equalTo(1))", "valueFor(123, instanceOf(String.class))")) {
                assertRejected("%s(map::call).until(%s);".format(factory, condition))
            }
        }
    }

    @Test
    fun `matches rejects existing conditions`() {
        for (expression in listOf(
                "matches(equalTo(1));",
                "matches(instanceOf(String.class));",
                "matches(present);",
                "matches(singleElement);",
                """matches(valueFor("key"));""")) {
            assertRejected(expression)
        }
    }

    @Test
    fun `conditions reject unrelated result types`() {
        for (expression in listOf(
                "String wrong = await(numbers).until(equalTo(1));",
                "String wrong = await(collection::call).until(singleElement(equalTo(1)));",
                "String wrong = await(collection::call).until(singleElement(matches((Number value) -> value.intValue() > 0)));",
                "AwaitResult<List<Number>, String> wrong = tryAwait(collection::call).until(singleElement(matches((Number value) -> value.intValue() > 0)));",
                "List<String> wrong = await(collection::call).until(captured(singleElement(matches((Number value) -> value.intValue() > 0)), singleElement(matches((Number value) -> value.intValue() > 0))));",
                """String wrong = await(map::call).until(valueFor("key"));""",
                """Map.Entry<String, Number> wrong = await(map::call).until(valueFor("key"));""",
                """Number wrong = await(map::call).until(entryFor("key"));""",
                """AwaitCondition.SelectedCondition<Map<?, ?>> wrong = valueFor("key");""",
                "await(map::call).until(singleEntry(matches((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null)));",
                "await(map::call).until(singleEntry(matches((Map.Entry<Object, Number> entry) -> true)));",
                "await(map::call).until(singleEntry(matches((Map.Entry<String, Integer> entry) -> true)));",
                "Map.Entry<String, String> wrong = await(map::call).until(singleEntry(matches((Map.Entry<String, Number> entry) -> true)));",
                "await(collection::call).until(singleElement(matches(String::isBlank)));",
                """AwaitResult<Map<String, Number>, String> wrong = tryAwait(map::call).until(valueFor("key"));""",
                """List<String> wrong = await(map::call).until(captured(valueFor("key"), valueFor("key")));""")) {
            assertRejected(expression)
        }
    }

    @Test
    fun `typed quantifiers reject incompatible callbacks`() {
        for (factory in listOf("await", "tryAwait")) {
            for (expression in listOf(
                    "%s(map::call).until(allKeys((Integer key) -> key > 0));",
                    "%s(map::call).until(anyKey((Integer key) -> key > 0));",
                    "%s(map::call).until(noKey((Integer key) -> key > 0));",
                    "%s(map::call).until(allValues(String::isBlank));",
                    "%s(map::call).until(anyValue(String::isBlank));",
                    "%s(map::call).until(noValue(String::isBlank));",
                    "%s(map::call).until(allEntries((String key, String value) -> value.isBlank()));",
                    "%s(map::call).until(hasEntry((String key, String value) -> value.isBlank()));",
                    "%s(map::call).until(hasEntry((Map.Entry<Integer, Number> entry) -> true));",
                    """%s(map::call).until(hasEntry((Map.Entry<String, String> entry) -> entry.getValue().isBlank()).because("reason"));""",
                    "%s(map::call).until(hasEntry((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null));",
                    "%s(map::call).until(hasNoEntry((String key, String value) -> value.isBlank()));",
                    "%s(map::call).until(hasNoEntry((Map.Entry<Integer, Number> entry) -> true));",
                    """%s(map::call).until(hasNoEntry((Map.Entry<String, String> entry) -> entry.getValue().isBlank()).because("reason"));""",
                    "%s(map::call).until(hasNoEntry((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null));",
                    "%s(collection::call).until(CollectionConditions.all(String::isBlank));",
                    "%s(collection::call).until(CollectionConditions.any(String::isBlank));",
                    "%s(collection::call).until(CollectionConditions.none(String::isBlank));")) {
                assertRejected(expression.format(factory))
            }
        }
    }

    @Test
    fun `selectors keep their declared input and result types`() {
        for (expression in listOf("Condition<Optional<?>, Object> erased = present;",
                """Condition<Map<?, ?>, Object> erased = valueFor("key");""",
                "Object value = await(() -> List.of(Optional.of(1))).until(singleElement(present));",
                """Object value = await(() -> Optional.of(Map.of("key", 1))).until(hasValue(valueFor("key")));""",
                "List<Object> values = await(collection::call).until(captured(singleElement, isNotNull));",
                """Callable<Map<String, Number>> plain = map; String value = await(plain::call).until(valueFor("key"));""",
                "await(collection::call).until(CollectionConditions.sorted(String::compareTo));")) {
            assertRejected(expression)
        }
    }

    private fun assertRejected(body: String) {
        CompilationSupport.assertRejected("""
                import java.util.*;
                import java.util.concurrent.Callable;
                import io.github.gromoff97.awium.*;
                import io.github.gromoff97.awium.conditions.*;
                import io.github.gromoff97.awium.result.*;
                import io.github.gromoff97.awium.function.*;
                import io.github.gromoff97.awium.Await.*;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Await.tryAwait;
                import static io.github.gromoff97.awium.conditions.Conditions.*;
                import static io.github.gromoff97.awium.conditions.OptionalConditions.*;
                import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
                import static io.github.gromoff97.awium.conditions.MapConditions.*;
                final class Contract {
                    void check(Callable<Number> numbers, Callable<Optional<Number>> optional,
                            Callable<List<Number>> collection, Callable<Map<String, Number>> map) {
                        %s
                    }
                }
                """.trimIndent().format(body))
    }
}

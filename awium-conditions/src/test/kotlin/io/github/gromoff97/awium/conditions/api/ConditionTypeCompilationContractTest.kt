package io.github.gromoff97.awium.conditions.api

import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ConditionTypeCompilationContractTest {

    @ParameterizedTest
    @ValueSource(strings = ["await", "tryAwait"])
    fun `natural order rejects mutually incomparable elements`(entryPoint: String) {
        assertRejected("""
            $entryPoint(() -> List.of(1, "value"))
                    .until(CollectionConditions.sorted(Comparator.naturalOrder()));
        """.trimIndent())
    }

    @TestFactory
    fun `custom checks cannot return an attempt failure`() = buildList {
        for (expression in listOf(
                """CommonConditions.condition("failed", value -> failure);""",
                """CommonConditions.conditionPerWait("failed", () -> value -> failure);""")) {
            add(dynamicTest(expression) { CompilationSupport.assertRejected("""
                    package io.github.gromoff97.awium;
                    import io.github.gromoff97.awium.conditions.common.*;
                    import io.github.gromoff97.awium.result.Attempt;
                    final class Contract {
                        void check() {
                            var failure = new Attempt.FailedAfterObservation<>("actual", Attempt.Operation.CONDITION, new IllegalStateException(), null);
                            %s
                        }
                    }
                    """.trimIndent().format(expression)) })
        }
    }

    @TestFactory
    fun `custom checks reject incompatible input and result types`() = listOf(
        """String wrong = await(numbers).until(condition("identity", value -> AttemptResult.satisfied(value)));""",
        """AwaitResult<Number, String> wrong = tryAwait(numbers).until(condition("identity", value -> AttemptResult.satisfied(value)));""",
        """List<String> wrong = await(numbers).until(captured(condition("first", value -> AttemptResult.satisfied(value)), isNotNull));""",
        """Condition<Number, String> wrong = condition("transform", value -> AttemptResult.satisfied(value));""",
        "await(() -> 123).until(new consumer.ArtifactConsumer.Length());",
        """String wrong = await(() -> "ready").until(new consumer.ArtifactConsumer.Length());""",
        """String wrong = await(numbers).until(conditionPerWait("identity", () -> value -> AttemptResult.satisfied(value)));""",
        """await(numbers).until(condition("length", (String value) -> AttemptResult.satisfied(value.length())));""",
        """String wrong = await(numbers).until(condition("id", (Number value) -> AttemptResult.satisfied(value.intValue())).because("reason"));""",
        """await(() -> Map.of("key", 1)).until(MapConditions.hasEntry(condition("wrong value", """
                + "(Map.Entry<String, String> entry) -> AttemptResult.satisfied(entry.getValue()))));"
    ).map(::rejected)

    @TestFactory
    fun `explanations cannot weaken input or result types`() = listOf(
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
        """List<Integer> wrong = await(() -> "ready").until(captured(instanceOf(String.class)).because("reason"));"""
    ).map(::rejected)

    @TestFactory
    fun `comparisons reject incompatible expected types`() = listOf("await", "tryAwait").flatMap { factory ->
        listOf(
            "equalTo(123)",
            "notEqualTo(123)",
            "sameAs(123)",
            "notSameAs(123)",
            "in(List.of(123, 456))",
            "notIn(List.of(123, 456))"
        ).map { condition -> rejected("""%s(() -> "ready").until(%s);""".format(factory, condition)) }
    }

    @TestFactory
    fun `subset rejects incompatible element types`() = listOf("await", "tryAwait").flatMap { factory ->
        listOf(
            """%s(() -> List.of("ready")).until(CollectionConditions.subsetOf(List.of(123)));""",
                """%s(() -> List.of("ready")).until(captured(CollectionConditions.subsetOf(List.of(123)), isNotNull));""",
                """%s(() -> List.of(List.of("ready"))).until(singleElement(CollectionConditions.subsetOf(List.of(123))));"""
        ).map { expression -> rejected(expression.format(factory)) }
    }

    @TestFactory
    fun `nested comparisons reject incompatible expected types`() = listOf("await", "tryAwait").flatMap { factory ->
        listOf(
            """%s(optional::call).until(hasValue(equalTo("wrong")));""",
                """%s(collection::call).until(singleElement(equalTo("wrong")));""",
                """%s(map::call).until(valueFor("key", equalTo("wrong")));""",
                "%s(map::call).until(singleEntry(equalTo(Map.entry(123, 1))));",
                """%s(map::call).until(singleEntry(equalTo(Map.entry("key", "wrong"))));"""
        ).map { expression -> rejected(expression.format(factory)) }
    }

    @TestFactory
    fun `selector constraints also apply to views and mixed capture sequences`() = listOf("await", "tryAwait").flatMap { factory ->
        listOf(
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
                "%s(map::call).until(singleElement);"
        ).map { expression -> rejected(expression.format(factory)) }
    }

    @TestFactory
    fun `mixed value stages retain both input constraints`() = buildList {
        for (factory in listOf("await", "tryAwait")) {
            for (arguments in listOf(
                    "equalTo(123), isNotNull",
                    "isNotNull, equalTo(123)",
                    "equalTo(new Object()), matches(String::isBlank)",
                    """equalTo("ready"), matches((Integer value) -> true)""",
                    "matches(String::isBlank), notIn(List.of(123))")) {
                add(rejected("""%s(() -> "ready").until(captured(%s));""".format(factory, arguments)))
            }
        }
        add(rejected("""List<Integer> wrong = await(() -> "ready").until(captured(equalTo("ready"), isNotNull));"""))
        add(rejected("""await(() -> "ready").<Object>until(captured(equalTo(123), isNotNull));"""))
    }

    @TestFactory
    fun `preserving checks cannot be assigned arbitrary result types`() = listOf(
        "Condition<Object, Object> erased = isNotNull;",
        """Condition<String, String> erased = equalTo("ready");"""
    ).map(::rejected)

    @TestFactory
    fun `map lookups reject incompatible key types`() = listOf("await", "tryAwait").flatMap { factory ->
        listOf(
            "valueFor(123)", "entryFor(123)",
                "valueFor(123, equalTo(1))", "valueFor(123, instanceOf(String.class))"
        ).map { condition -> rejected("%s(map::call).until(%s);".format(factory, condition)) }
    }

    @TestFactory
    fun `matches rejects existing conditions`() = listOf(
        "matches(equalTo(1));",
        "matches(instanceOf(String.class));",
        "matches(present);",
        "matches(singleElement);",
        """matches(valueFor("key"));"""
    ).map(::rejected)

    @TestFactory
    fun `conditions reject unrelated result types`() = listOf(
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
        """List<String> wrong = await(map::call).until(captured(valueFor("key"), valueFor("key")));"""
    ).map(::rejected)

    @TestFactory
    fun `typed quantifiers reject incompatible callbacks`() = listOf("await", "tryAwait").flatMap { factory ->
        listOf(
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
                "%s(collection::call).until(CollectionConditions.none(String::isBlank));"
        ).map { expression -> rejected(expression.format(factory)) }
    }

    @TestFactory
    fun `selectors keep their declared input and result types`() = listOf(
        "Condition<Optional<?>, Object> erased = present;",
        """Condition<Map<?, ?>, Object> erased = valueFor("key");""",
        "Object value = await(() -> List.of(Optional.of(1))).until(singleElement(present));",
        """Object value = await(() -> Optional.of(Map.of("key", 1))).until(hasValue(valueFor("key")));""",
        "List<Object> values = await(collection::call).until(captured(singleElement, isNotNull));",
        """Callable<Map<String, Number>> plain = map; String value = await(plain::call).until(valueFor("key"));""",
        "await(collection::call).until(CollectionConditions.sorted(String::compareTo));"
    ).map(::rejected)

    private fun rejected(body: String) = dynamicTest(body) { assertRejected(body) }

    private fun assertRejected(body: String) {
        CompilationSupport.assertRejected("""
                import java.util.*;
                import java.util.concurrent.Callable;
                import io.github.gromoff97.awium.*;
                import io.github.gromoff97.awium.conditions.common.*;
                import io.github.gromoff97.awium.conditions.collection.CollectionConditions;
                import io.github.gromoff97.awium.conditions.map.MapConditions;
                import io.github.gromoff97.awium.result.*;
                import io.github.gromoff97.awium.function.*;
                import io.github.gromoff97.awium.Await.*;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Await.tryAwait;
                import static io.github.gromoff97.awium.conditions.common.CommonConditions.*;
                import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.*;
                import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
                import static io.github.gromoff97.awium.conditions.map.MapConditions.*;
                final class Contract {
                    void check(Callable<Number> numbers, Callable<Optional<Number>> optional,
                            Callable<List<Number>> collection, Callable<Map<String, Number>> map) {
                        %s
                    }
                }
                """.trimIndent().format(body))
    }
}

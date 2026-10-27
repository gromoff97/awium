package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConditionTypeCompilationContractTest {

    @TempDir
    Path directory;

    @Test
    void customChecksCannotReturnAnAttemptFailure() throws IOException {
        for (String expression : List.of(
                "Conditions.condition(\"failed\", value -> failure);",
                "Conditions.conditionPerWait(\"failed\", () -> value -> failure);")) {
            CompilationSupport.assertRejected(directory, """
                    package io.github.gromoff97.awium;
                    final class Contract {
                        void check() {
                            var failure = new AttemptResult.Uncontrolled<String>(new IllegalStateException(), null, null);
                            %s
                        }
                    }
                    """.formatted(expression));
        }
    }

    @Test
    void customChecksCannotTransformTheObservedType() throws IOException {
        for (String expression : List.of(
                "String wrong = await(numbers).until(condition(\"identity\", ConditionResult::satisfied));",
                "AwaitResult<Number, String> wrong = await(numbers).tryUntil(condition(\"identity\", ConditionResult::satisfied));",
                "List<String> wrong = await(numbers).until(condition(\"first\", ConditionResult::satisfied), isNotNull);",
                "Condition.Typed<Number, String> wrong = condition(\"transform\", value -> ConditionResult.satisfied(\"text\"));")) {
            assertRejected(expression);
        }
    }

    @Test
    void explanationsCannotWeakenInputOrResultTypes() throws IOException {
        for (String expression : List.of(
                "await(() -> \"ready\").until(equalTo(123).because(\"reason\"));",
                "await(collection::call).until(singleElement(matches(String::isBlank)).because(\"reason\"));",
                "String wrong = await(optional::call).until(present.because(\"reason\"));",
                "String wrong = await(collection::call).until(singleElement.because(\"reason\"));",
                "String wrong = await(map::call).until(valueFor(\"key\").because(\"reason\"));",
                "Integer wrong = await(() -> \"ready\").until(instanceOf(String.class).because(\"reason\"));")) {
            assertRejected(expression);
        }
    }

    @Test
    void comparisonsRejectIncompatibleExpectedTypes() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String condition : List.of("equalTo(123)",
                "notEqualTo(123)",
                "sameAs(123)",
                "notSameAs(123)",
                "in(List.of(123, 456))",
                "notIn(List.of(123, 456))")) {
                String expression = "await(() -> \"ready\").%s(%s);".formatted(terminal, condition);
                assertRejected(expression);
            }
        }
    }

    @Test
    void subsetRejectsIncompatibleElementTypes() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String expression : List.of(
                    "await(() -> List.of(\"ready\")).%s(CollectionConditions.subsetOf(List.of(123)));",
                    "await(() -> List.of(\"ready\")).%s(CollectionConditions.subsetOf(List.of(123)), isNotNull);",
                    "await(() -> List.of(List.of(\"ready\"))).%s(singleElement(CollectionConditions.subsetOf(List.of(123))));")) {
                String call = expression.formatted(terminal);
                assertRejected(call);
            }
        }
    }

    @Test
    void nestedComparisonsRejectIncompatibleExpectedTypes() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String expression : List.of(
                    "await(optional::call).%s(hasValue(equalTo(\"wrong\")));",
                    "await(collection::call).%s(singleElement(equalTo(\"wrong\")));",
                    "await(map::call).%s(valueFor(\"key\", equalTo(\"wrong\")));",
                    "await(map::call).%s(singleEntry(equalTo(Map.entry(123, 1))));",
                    "await(map::call).%s(singleEntry(equalTo(Map.entry(\"key\", \"wrong\"))));")) {
                String call = expression.formatted(terminal);
                assertRejected(call);
            }
        }
    }

    @Test
    void mixedValueStagesRetainBothInputConstraints() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String arguments : List.of(
                    "equalTo(123), isNotNull",
                    "isNotNull, equalTo(123)",
                    "equalTo(new Object()), matches(String::isBlank)",
                    "equalTo(\"ready\"), matches((Integer value) -> true)",
                    "matches(String::isBlank), notIn(List.of(123))")) {
                assertRejected("await(() -> \"ready\").%s(%s);".formatted(terminal, arguments));
            }
        }
        assertRejected("List<Integer> wrong = await(() -> \"ready\").until(equalTo(\"ready\"), isNotNull);");
        assertRejected("await(() -> \"ready\").<Object>until(equalTo(123), isNotNull);");
    }

    @Test
    void sequencesRejectMixingValueChecksWithTypedTransformations() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            var checks = List.of("equalTo(\"ready\")",
                    "matches(String::isBlank)",
                    "instanceOf(String.class)");
            for (String first : checks) {
                for (String second : checks) {
                    if (first.equals(second)) continue;
                    if (!first.startsWith("instanceOf") && !second.startsWith("instanceOf")) continue;
                    String call = "await(() -> \"ready\").%s(%s, %s);".formatted(terminal, first, second);
                    assertRejected(call);
                }
            }
        }
        assertRejected("Condition.Typed<Object, Object> erased = isNotNull;");
        assertRejected("Condition.Typed<String, String> erased = equalTo(\"ready\");");
    }

    @Test
    void mapLookupsRejectIncompatibleKeyTypes() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String condition : List.of("valueFor(123)", "entryFor(123)",
                    "valueFor(123, equalTo(1))", "valueFor(123, instanceOf(String.class))")) {
                String expression = "await(map::call).%s(%s);".formatted(terminal, condition);
                assertRejected(expression);
            }
        }
    }

    @Test
    void matchesRejectsExistingConditions() throws IOException {
        for (String expression : List.of(
                "matches(equalTo(1));",
                "matches(instanceOf(String.class));",
                "matches(present);",
                "matches(singleElement);",
                "matches(valueFor(\"key\"));")) {
            assertRejected(expression);
        }
    }

    @Test
    void conditionsCannotInventInputOrResultTypes() throws IOException {
        for (String expression : List.of(
                "String wrong = await(numbers).until(equalTo(1));",
                "String wrong = await(collection::call).until(singleElement(equalTo(1)));",
                "String wrong = await(map::call).until(valueFor(\"key\"));",
                "Map.Entry<String, Number> wrong = await(map::call).until(valueFor(\"key\"));",
                "Number wrong = await(map::call).until(entryFor(\"key\"));",
                "Condition.SelectedCondition<Map<?, ?>, Object, io.github.gromoff97.awium.Condition.Reference<?>> wrong = valueFor(\"key\");",
                "await(map::call).until(singleEntry(matches((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null)));",
                "await(map::call).until(singleEntry(matches((Map.Entry<Object, Number> entry) -> true)));",
                "await(map::call).until(singleEntry(matches((Map.Entry<String, Integer> entry) -> true)));",
                "Map.Entry<String, String> wrong = await(map::call).until(singleEntry(matches((Map.Entry<String, Number> entry) -> true)));",
                "await(collection::call).until(singleElement(matches(String::isBlank)));",
                "AwaitResult<Map<String, Number>, String> wrong = await(map::call).tryUntil(valueFor(\"key\"));",
                "List<String> wrong = await(map::call).until(valueFor(\"key\"), valueFor(\"key\"));")) {
            assertRejected(expression);
        }
    }

    @Test
    void typedQuantifiersRejectIncompatibleCallbacks() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String expression : List.of(
                    "await(map::call).%s(allKeys((Integer key) -> key > 0));",
                    "await(map::call).%s(anyKey((Integer key) -> key > 0));",
                    "await(map::call).%s(noKey((Integer key) -> key > 0));",
                    "await(map::call).%s(allValues(String::isBlank));",
                    "await(map::call).%s(anyValue(String::isBlank));",
                    "await(map::call).%s(noValue(String::isBlank));",
                    "await(map::call).%s(allEntries((String key, String value) -> value.isBlank()));",
                    "await(map::call).%s(hasEntry((String key, String value) -> value.isBlank()));",
                    "await(map::call).%s(hasEntry((Map.Entry<Integer, Number> entry) -> true));",
                    "await(map::call).%s(hasEntry((Map.Entry<String, String> entry) -> entry.getValue().isBlank()).because(\"reason\"));",
                    "await(map::call).%s(hasEntry((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null));",
                    "await(map::call).%s(hasNoEntry((String key, String value) -> value.isBlank()));",
                    "await(map::call).%s(hasNoEntry((Map.Entry<Integer, Number> entry) -> true));",
                    "await(map::call).%s(hasNoEntry((Map.Entry<String, String> entry) -> entry.getValue().isBlank()).because(\"reason\"));",
                    "await(map::call).%s(hasNoEntry((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null));",
                    "await(collection::call).%s(CollectionConditions.all(String::isBlank));",
                    "await(collection::call).%s(CollectionConditions.any(String::isBlank));",
                    "await(collection::call).%s(CollectionConditions.none(String::isBlank));")) {
                String call = expression.formatted(terminal);
                assertRejected(call);
            }
        }
    }

    @Test
    void selectorsCannotMasqueradeAsTypedTransformations() throws IOException {
        for (String expression : List.of("Condition.Typed<Optional<?>, Object> erased = present;",
                "Condition.Typed<Map<?, ?>, Object> erased = valueFor(\"key\");",
                "Object value = await(() -> List.of(Optional.of(1))).until(singleElement(present));",
                "Object value = await(() -> Optional.of(Map.of(\"key\", 1))).until(hasValue(valueFor(\"key\")));",
                "List<Object> values = await(collection::call).until(singleElement, isNotNull);",
                "Callable<Map<String, Number>> plain = map; String value = await(plain::call).until(valueFor(\"key\"));",
                "await(collection::call).until(CollectionConditions.sorted(String::compareTo));")) {
            assertRejected(expression);
        }
    }

    private void assertRejected(String body) throws IOException {
        CompilationSupport.assertRejected(directory, """
                import java.util.*;
                import java.util.concurrent.Callable;
                import io.github.gromoff97.awium.*;
                import io.github.gromoff97.awium.Await.*;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.OptionalConditions.*;
                import static io.github.gromoff97.awium.CollectionConditions.singleElement;
                import static io.github.gromoff97.awium.MapConditions.*;
                final class Contract {
                    void check(Callable<Number> numbers, java.util.concurrent.Callable<java.util.Optional<Number>> optional,
                            java.util.concurrent.Callable<List<Number>> collection, java.util.concurrent.Callable<Map<String, Number>> map) {
                        %s
                    }
                }
                """.formatted(body));
    }
}

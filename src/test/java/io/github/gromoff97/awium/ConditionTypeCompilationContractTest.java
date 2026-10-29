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
                    import io.github.gromoff97.awium.result.AttemptStatus;
                    final class Contract {
                        void check() {
                            var failure = new AttemptStatus.Uncontrolled(AttemptStatus.Operation.CONDITION, new IllegalStateException());
                            %s
                        }
                    }
                    """.formatted(expression));
        }
    }

    @Test
    void customChecksCannotTransformTheObservedType() throws IOException {
        for (String expression : List.of(
                "String wrong = await(numbers).until(condition(\"identity\", ignored -> ConditionResult.satisfied()));",
                "AwaitResult<Number, String> wrong = tryAwait(numbers).until(condition(\"identity\", ignored -> ConditionResult.satisfied()));",
                "List<String> wrong = await(numbers).until(captured(condition(\"first\", ignored -> ConditionResult.satisfied()), isNotNull));",
                "Condition.Typed<Number, String> wrong = condition(\"transform\", value -> ConditionResult.satisfied());")) {
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
                "Integer wrong = await(() -> \"ready\").until(instanceOf(String.class).because(\"reason\"));",
                "await(() -> \"ready\").until(captured(isNotNull, equalTo(123)).because(\"reason\"));",
                "List<String> wrong = await(optional::call).until(captured(present, present).because(\"reason\"));",
                "List<String> wrong = await(collection::call).until(captured(singleElement, singleElement).because(\"reason\"));",
                "List<String> wrong = await(map::call).until(captured(valueFor(\"key\"), valueFor(\"key\")).because(\"reason\"));",
                "List<Integer> wrong = await(() -> \"ready\").until(captured(instanceOf(String.class)).because(\"reason\"));")) {
            assertRejected(expression);
        }
    }

    @Test
    void comparisonsRejectIncompatibleExpectedTypes() throws IOException {
        for (String factory : List.of("await", "tryAwait")) {
            for (String condition : List.of("equalTo(123)",
                "notEqualTo(123)",
                "sameAs(123)",
                "notSameAs(123)",
                "in(List.of(123, 456))",
                "notIn(List.of(123, 456))")) {
                String expression = "%s(() -> \"ready\").until(%s);".formatted(factory, condition);
                assertRejected(expression);
            }
        }
    }

    @Test
    void subsetRejectsIncompatibleElementTypes() throws IOException {
        for (String factory : List.of("await", "tryAwait")) {
            for (String expression : List.of(
                    "%s(() -> List.of(\"ready\")).until(CollectionConditions.subsetOf(List.of(123)));",
                    "%s(() -> List.of(\"ready\")).until(captured(CollectionConditions.subsetOf(List.of(123)), isNotNull));",
                    "%s(() -> List.of(List.of(\"ready\"))).until(singleElement(CollectionConditions.subsetOf(List.of(123))));")) {
                String call = expression.formatted(factory);
                assertRejected(call);
            }
        }
    }

    @Test
    void nestedComparisonsRejectIncompatibleExpectedTypes() throws IOException {
        for (String factory : List.of("await", "tryAwait")) {
            for (String expression : List.of(
                    "%s(optional::call).until(hasValue(equalTo(\"wrong\")));",
                    "%s(collection::call).until(singleElement(equalTo(\"wrong\")));",
                    "%s(map::call).until(valueFor(\"key\", equalTo(\"wrong\")));",
                    "%s(map::call).until(singleEntry(equalTo(Map.entry(123, 1))));",
                    "%s(map::call).until(singleEntry(equalTo(Map.entry(\"key\", \"wrong\"))));")) {
                String call = expression.formatted(factory);
                assertRejected(call);
            }
        }
    }

    @Test
    void selectorConstraintsAlsoApplyToViewsAndMixedCaptureSequences() throws IOException {
        for (String factory : List.of("await", "tryAwait")) {
            for (String expression : List.of(
                    "%s(new CollectionViewSource<Number, List<? extends Number>>(() -> List.of(1))).until(singleElement(equalTo(\"wrong\")).because(\"reason\"));",
                    "%s(new MapViewSource<String, Number, Map<String, ? extends Number>>(() -> Map.of(\"key\", 1))).until(valueFor(\"key\", equalTo(\"wrong\")).because(\"reason\"));",
                    "%s(collection::call).until(captured(isNotNull, singleElement(equalTo(\"wrong\"))));",
                    "%s(optional::call).until(captured(isNotNull, hasValue(equalTo(\"wrong\"))));",
                    "%s(map::call).until(captured(isNotNull, valueFor(\"key\", equalTo(\"wrong\"))));",
                    "%s(collection::call).until(captured(singleElement(equalTo(\"wrong\")), singleElement((Number value) -> true), isNotNull));",
                    "%s(optional::call).until(captured(hasValue(equalTo(\"wrong\")), hasValue((Number value) -> true), isNotNull));",
                    "%s(map::call).until(captured(valueFor(\"key\", equalTo(\"wrong\")), valueFor(\"key\", (Number value) -> true), isNotNull));",
                    "%s(map::call).until(captured(isNotNull, singleEntry(matches((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null))));",
                    "%s(collection::call).until(present);",
                    "%s(optional::call).until(singleElement);",
                    "%s(map::call).until(singleElement);")) {
                assertRejected(expression.formatted(factory));
            }
        }
    }

    @Test
    void mixedValueStagesRetainBothInputConstraints() throws IOException {
        for (String factory : List.of("await", "tryAwait")) {
            for (String arguments : List.of(
                    "equalTo(123), isNotNull",
                    "isNotNull, equalTo(123)",
                    "equalTo(new Object()), matches(String::isBlank)",
                    "equalTo(\"ready\"), matches((Integer value) -> true)",
                    "matches(String::isBlank), notIn(List.of(123))")) {
                assertRejected("%s(() -> \"ready\").until(captured(%s));".formatted(factory, arguments));
            }
        }
        assertRejected("List<Integer> wrong = await(() -> \"ready\").until(captured(equalTo(\"ready\"), isNotNull));");
        assertRejected("await(() -> \"ready\").<Object>until(captured(equalTo(123), isNotNull));");
    }

    @Test
    void preservingChecksCannotBeAssignedArbitraryResultTypes() throws IOException {
        assertRejected("Condition.Typed<Object, Object> erased = isNotNull;");
        assertRejected("Condition.Typed<String, String> erased = equalTo(\"ready\");");
    }

    @Test
    void mapLookupsRejectIncompatibleKeyTypes() throws IOException {
        for (String factory : List.of("await", "tryAwait")) {
            for (String condition : List.of("valueFor(123)", "entryFor(123)",
                    "valueFor(123, equalTo(1))", "valueFor(123, instanceOf(String.class))")) {
                String expression = "%s(map::call).until(%s);".formatted(factory, condition);
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
                "String wrong = await(collection::call).until(singleElement(matches((Number value) -> value.intValue() > 0)));",
                "AwaitResult<List<Number>, String> wrong = tryAwait(collection::call).until(singleElement(matches((Number value) -> value.intValue() > 0)));",
                "List<String> wrong = await(collection::call).until(captured(singleElement(matches((Number value) -> value.intValue() > 0)), singleElement(matches((Number value) -> value.intValue() > 0))));",
                "String wrong = await(map::call).until(valueFor(\"key\"));",
                "Map.Entry<String, Number> wrong = await(map::call).until(valueFor(\"key\"));",
                "Number wrong = await(map::call).until(entryFor(\"key\"));",
                "Condition.SelectedCondition<Map<?, ?>> wrong = valueFor(\"key\");",
                "await(map::call).until(singleEntry(matches((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null)));",
                "await(map::call).until(singleEntry(matches((Map.Entry<Object, Number> entry) -> true)));",
                "await(map::call).until(singleEntry(matches((Map.Entry<String, Integer> entry) -> true)));",
                "Map.Entry<String, String> wrong = await(map::call).until(singleEntry(matches((Map.Entry<String, Number> entry) -> true)));",
                "await(collection::call).until(singleElement(matches(String::isBlank)));",
                "AwaitResult<Map<String, Number>, String> wrong = tryAwait(map::call).until(valueFor(\"key\"));",
                "List<String> wrong = await(map::call).until(captured(valueFor(\"key\"), valueFor(\"key\")));")) {
            assertRejected(expression);
        }
    }

    @Test
    void typedQuantifiersRejectIncompatibleCallbacks() throws IOException {
        for (String factory : List.of("await", "tryAwait")) {
            for (String expression : List.of(
                    "%s(map::call).until(allKeys((Integer key) -> key > 0));",
                    "%s(map::call).until(anyKey((Integer key) -> key > 0));",
                    "%s(map::call).until(noKey((Integer key) -> key > 0));",
                    "%s(map::call).until(allValues(String::isBlank));",
                    "%s(map::call).until(anyValue(String::isBlank));",
                    "%s(map::call).until(noValue(String::isBlank));",
                    "%s(map::call).until(allEntries((String key, String value) -> value.isBlank()));",
                    "%s(map::call).until(hasEntry((String key, String value) -> value.isBlank()));",
                    "%s(map::call).until(hasEntry((Map.Entry<Integer, Number> entry) -> true));",
                    "%s(map::call).until(hasEntry((Map.Entry<String, String> entry) -> entry.getValue().isBlank()).because(\"reason\"));",
                    "%s(map::call).until(hasEntry((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null));",
                    "%s(map::call).until(hasNoEntry((String key, String value) -> value.isBlank()));",
                    "%s(map::call).until(hasNoEntry((Map.Entry<Integer, Number> entry) -> true));",
                    "%s(map::call).until(hasNoEntry((Map.Entry<String, String> entry) -> entry.getValue().isBlank()).because(\"reason\"));",
                    "%s(map::call).until(hasNoEntry((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null));",
                    "%s(collection::call).until(CollectionConditions.all(String::isBlank));",
                    "%s(collection::call).until(CollectionConditions.any(String::isBlank));",
                    "%s(collection::call).until(CollectionConditions.none(String::isBlank));")) {
                String call = expression.formatted(factory);
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
                "List<Object> values = await(collection::call).until(captured(singleElement, isNotNull));",
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
                import io.github.gromoff97.awium.result.*;
                import io.github.gromoff97.awium.function.*;
                import io.github.gromoff97.awium.Await.*;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Await.tryAwait;
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

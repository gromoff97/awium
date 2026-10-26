package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectBecauseCompilationContractTest {

    @TempDir
    Path directory;

    @Test
    void supplierReasonsPreserveInputResultAndReasonTypes() throws IOException {
        for (String expression : List.of(
                "await(() -> \"ready\").until(equalTo(123).because(() -> \"wrong input\"));",
                "String wrong = await(numbers).until(equalTo(1).because(() -> \"wrong result\"));",
                "Map.Entry<String, Number> wrong = awaitMap(map).until(valueFor(\"key\").because(() -> \"wrong kind\"));",
                "await(() -> \"ready\").until(isNotNull.because(() -> 123));")) {
            assertRejected(expression);
        }
    }

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
        for (String decoration : List.of("", ".because(\"required value\")")) {
            for (String expression : List.of(
                    "String wrong = await(numbers).until(condition(\"identity\", ConditionResult::satisfied)%s);",
                    "AwaitResult<Number, String> wrong = await(numbers).tryUntil(condition(\"identity\", ConditionResult::satisfied)%s);",
                    "List<String> wrong = await(numbers).until(condition(\"first\", ConditionResult::satisfied)%s, isNotNull);",
                    "Condition.Typed<Number, String> wrong = condition(\"transform\", value -> ConditionResult.satisfied(\"text\"))%s;")) {
                String call = expression.formatted(decoration);
                assertRejected(call);
            }
        }
    }

    @Test
    void comparisonsRejectIncompatibleExpectedTypes() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String condition : List.of("equalTo(123)", "notEqualTo(123)", "sameAs(123)",
                    "notSameAs(123)", "in(List.of(123, 456))", "notIn(List.of(123, 456))",
                    "equalTo(123).because(\"required value\")",
                    "equalTo(123).because(\"%s\", \"required value\")")) {
                String expression = "await(() -> \"ready\").%s(%s);".formatted(terminal, condition);
                assertRejected(expression);
            }
        }
    }

    @Test
    void subsetRejectsIncompatibleElementTypes() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String decoration : List.of("", ".because(\"allowed values\")")) {
                for (String expression : List.of(
                        "awaitCollection(() -> List.of(\"ready\")).%s(CollectionConditions.subsetOf(List.of(123))%s);",
                        "awaitCollection(() -> List.of(\"ready\")).%s(CollectionConditions.subsetOf(List.of(123))%s, isNotNull);",
                        "awaitCollection(() -> List.of(List.of(\"ready\"))).%s(single(CollectionConditions.subsetOf(List.of(123))%s));")) {
                    String call = expression.formatted(terminal, decoration);
                    assertRejected(call);
                }
            }
        }
    }

    @Test
    void nestedComparisonsRejectIncompatibleExpectedTypes() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String decoration : List.of("", ".because(\"required value\")")) {
                for (String expression : List.of(
                        "awaitOptional(optional).%s(hasValue(equalTo(\"wrong\")%s));",
                        "awaitCollection(collection).%s(single(equalTo(\"wrong\")%s));",
                        "awaitMap(map).%s(valueFor(\"key\", equalTo(\"wrong\")%s));",
                        "awaitMap(map).%s(singleEntry(equalTo(Map.entry(123, 1))%s));",
                        "awaitMap(map).%s(singleEntry(equalTo(Map.entry(\"key\", \"wrong\"))%s));")) {
                    String call = expression.formatted(terminal, decoration);
                    assertRejected(call);
                }
            }
        }
    }

    @Test
    void mixedValueStagesRetainBothInputConstraints() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String arguments : List.of(
                    "equalTo(123), isNotNull",
                    "isNotNull, equalTo(123).because(\"wrong type\")",
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
            for (String decoration : List.of("", ".because(\"required value\")")) {
                var checks = List.of("equalTo(\"ready\")" + decoration,
                        "matches(String::isBlank)" + decoration,
                        "instanceOf(String.class)" + decoration);
                for (String first : checks) {
                    for (String second : checks) {
                        if (first.equals(second)) continue;
                        if (!first.startsWith("instanceOf") && !second.startsWith("instanceOf")) continue;
                        String call = "await(() -> \"ready\").%s(%s, %s);".formatted(terminal, first, second);
                        assertRejected(call);
                    }
                }
            }
        }
        assertRejected("Condition.Typed<Object, Object> erased = isNotNull;");
        assertRejected("Condition.Typed<String, String> erased = equalTo(\"ready\");");
    }

    @Test
    void mapLookupsRejectIncompatibleKeyTypes() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String condition : List.of("valueFor(123)", "entryFor(123)", "onlyValueFor(123)",
                    "valueFor(123, equalTo(1))", "valueFor(123, instanceOf(String.class))")) {
                for (String decoration : List.of("", ".because(\"required key\")")) {
                    String expression = "awaitMap(map).%s(%s%s);".formatted(terminal, condition, decoration);
                    assertRejected(expression);
                }
            }
        }
    }

    @Test
    void matchesRejectsExistingConditions() throws IOException {
        for (String expression : List.of(
                "matches(equalTo(1));",
                "matches(instanceOf(String.class));",
                "matches(present);",
                "matches(single);",
                "matches(valueFor(\"key\"));")) {
            assertRejected(expression);
        }
    }

    @Test
    void decorationCannotInventInputOrResultTypes() throws IOException {
        for (String expression : List.of(
                "String wrong = await(numbers).until(equalTo(1).because(\"wrong\"));",
                "String wrong = awaitCollection(collection).until(single(equalTo(1)).because(\"wrong\"));",
                "String wrong = awaitMap(map).until(valueFor(\"key\").because(\"wrong\"));",
                "Map.Entry<String, Number> wrong = awaitMap(map).until(valueFor(\"key\").because(\"wrong\"));",
                "Number wrong = awaitMap(map).until(entryFor(\"key\").because(\"wrong\"));",
                "Condition.SelectedCondition<Map<?, ?>, Object, io.github.gromoff97.awium.Condition.Reference<?>> wrong = valueFor(\"key\").because(\"wrong\");",
                "awaitMap(map).until(singleEntry(matches((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null)).because(\"wrong\"));",
                "awaitMap(map).until(singleEntry(matches((Map.Entry<Object, Number> entry) -> true)).because(\"wrong key input\"));",
                "awaitMap(map).until(singleEntry(matches((Map.Entry<String, Integer> entry) -> true)).because(\"narrow value input\"));",
                "Map.Entry<String, String> wrong = awaitMap(map).until(singleEntry(matches((Map.Entry<String, Number> entry) -> true)));",
                "awaitCollection(collection).until(single(matches(String::isBlank)).because(\"wrong\"));",
                "AwaitResult<Map<String, Number>, String> wrong = awaitMap(map).tryUntil(valueFor(\"key\").because(\"wrong\"));",
                "List<String> wrong = awaitMap(map).until(valueFor(\"key\").because(\"wrong\"), valueFor(\"key\"));")) {
            assertRejected(expression);
        }
    }

    @Test
    void explainedQuantifiersRejectIncompatibleCallbacks() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String expression : List.of(
                    "awaitMap(map).%s(allKeys((Integer key) -> key > 0).because(\"wrong\"));",
                    "awaitMap(map).%s(anyKey((Integer key) -> key > 0).because(\"wrong\"));",
                    "awaitMap(map).%s(noKey((Integer key) -> key > 0).because(\"wrong\"));",
                    "awaitMap(map).%s(allValues(String::isBlank).because(\"wrong\"));",
                    "awaitMap(map).%s(anyValue(String::isBlank).because(\"wrong\"));",
                    "awaitMap(map).%s(noValue(String::isBlank).because(\"wrong\"));",
                    "awaitMap(map).%s(allEntries((String key, String value) -> value.isBlank()).because(\"wrong\"));",
                    "awaitMap(map).%s(anyEntry((String key, String value) -> value.isBlank()).because(\"wrong\"));",
                    "awaitMap(map).%s(noEntry((String key, String value) -> value.isBlank()).because(\"wrong\"));",
                    "awaitCollection(collection).%s(CollectionConditions.all(String::isBlank).because(\"wrong\"));",
                    "awaitCollection(collection).%s(CollectionConditions.any(String::isBlank).because(\"wrong\"));",
                    "awaitCollection(collection).%s(CollectionConditions.none(String::isBlank).because(\"wrong\"));")) {
                String call = expression.formatted(terminal);
                assertRejected(call);
            }
        }
    }

    @Test
    void selectorsCannotMasqueradeAsTypedTransformations() throws IOException {
        for (String expression : List.of(
                "Condition.Typed<Optional<?>, Object> erased = present;",
                "Condition.Typed<Map<?, ?>, Object> erased = valueFor(\"key\");",
                "Object value = awaitCollection(() -> List.of(Optional.of(1))).until(single(present));",
                "Object value = awaitCollection(() -> List.of(Optional.of(1))).until(single(present.because(\"inner\")).because(\"outer\"));",
                "Object value = awaitOptional(() -> Optional.of(Map.of(\"key\", 1))).until(hasValue(valueFor(\"key\")));",
                "Object value = awaitOptional(() -> Optional.of(Map.of(\"key\", 1))).until(hasValue(valueFor(\"key\").because(\"inner\")).because(\"outer\"));",
                "List<Object> values = awaitCollection(collection).until(single, isNotNull);",
                "List<Object> values = awaitCollection(collection).until(single.because(\"first\"), isNotNull.because(\"second\"));",
                "Callable<Map<String, Number>> plain = map; String value = awaitMap(plain).until(valueFor(\"key\"));",
                "Callable<Map<String, Number>> plain = map; String value = awaitMap(plain).until(valueFor(\"key\").because(\"required\"));",
                "awaitCollection(collection).until(CollectionConditions.sorted(String::compareTo).because(\"wrong type\"));")) {
            assertRejected(expression);
        }
    }

    private void assertRejected(String body) throws IOException {
        CompilationSupport.assertRejected(directory, """
                import java.util.*;
                import java.util.concurrent.Callable;
                import io.github.gromoff97.awium.*;
                import io.github.gromoff97.awium.Await.*;
                import static io.github.gromoff97.awium.Conditions.await;
                import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                import static io.github.gromoff97.awium.MapConditions.awaitMap;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.OptionalConditions.*;
                import static io.github.gromoff97.awium.CollectionConditions.single;
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

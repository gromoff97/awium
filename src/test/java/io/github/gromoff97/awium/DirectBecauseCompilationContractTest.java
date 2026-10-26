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
                "Map.Entry<String, Number> wrong = await(map).until(valueFor(\"key\").because(() -> \"wrong kind\"));",
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
                    "Condition<Number, String> wrong = condition(\"transform\", value -> ConditionResult.satisfied(\"text\"))%s;")) {
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
                        "await(() -> List.of(\"ready\")).%s(CollectionConditions.subsetOf(List.of(123))%s);",
                        "await(() -> List.of(\"ready\")).%s(CollectionConditions.subsetOf(List.of(123))%s, isNotNull);",
                        "await(() -> List.of(List.of(\"ready\"))).%s(single(CollectionConditions.subsetOf(List.of(123))%s));")) {
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
                        "await(optional).%s(hasValue(equalTo(\"wrong\")%s));",
                        "await(collection).%s(single(equalTo(\"wrong\")%s));",
                        "await(map).%s(valueFor(\"key\", equalTo(\"wrong\")%s));",
                        "await(map).%s(singleEntry(equalTo(Map.entry(123, 1))%s));",
                        "await(map).%s(singleEntry(equalTo(Map.entry(\"key\", \"wrong\"))%s));")) {
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
        assertRejected("Condition<Object, Object> erased = isNotNull;");
        assertRejected("Condition<String, String> erased = equalTo(\"ready\");");
    }

    @Test
    void mapLookupsRejectIncompatibleKeyTypes() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String condition : List.of("valueFor(123)", "entryFor(123)", "onlyValueFor(123)",
                    "valueFor(123, equalTo(1))", "valueFor(123, instanceOf(String.class))")) {
                for (String decoration : List.of("", ".because(\"required key\")")) {
                    String expression = "await(map).%s(%s%s);".formatted(terminal, condition, decoration);
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
                "String wrong = await(collection).until(single(equalTo(1)).because(\"wrong\"));",
                "String wrong = await(map).until(valueFor(\"key\").because(\"wrong\"));",
                "Map.Entry<String, Number> wrong = await(map).until(valueFor(\"key\").because(\"wrong\"));",
                "Number wrong = await(map).until(entryFor(\"key\").because(\"wrong\"));",
                "Condition.SelectedCondition<Map<?, ?>> wrong = valueFor(\"key\").because(\"wrong\");",
                "await(map).until(singleEntry(matches((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null)).because(\"wrong\"));",
                "await(collection).until(single(matches(String::isBlank)).because(\"wrong\"));",
                "AwaitResult<Map<String, Number>, String> wrong = await(map).tryUntil(valueFor(\"key\").because(\"wrong\"));",
                "List<String> wrong = await(map).until(valueFor(\"key\").because(\"wrong\"), valueFor(\"key\"));")) {
            assertRejected(expression);
        }
    }

    @Test
    void explainedQuantifiersRejectIncompatibleCallbacks() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String expression : List.of(
                    "await(map).%s(allKeys((Integer key) -> key > 0).because(\"wrong\"));",
                    "await(map).%s(anyKey((Integer key) -> key > 0).because(\"wrong\"));",
                    "await(map).%s(noKey((Integer key) -> key > 0).because(\"wrong\"));",
                    "await(map).%s(allValues(String::isBlank).because(\"wrong\"));",
                    "await(map).%s(anyValue(String::isBlank).because(\"wrong\"));",
                    "await(map).%s(noValue(String::isBlank).because(\"wrong\"));",
                    "await(map).%s(allEntries((String key, String value) -> value.isBlank()).because(\"wrong\"));",
                    "await(map).%s(anyEntry((String key, String value) -> value.isBlank()).because(\"wrong\"));",
                    "await(map).%s(noEntry((String key, String value) -> value.isBlank()).because(\"wrong\"));",
                    "await(collection).%s(CollectionConditions.all(String::isBlank).because(\"wrong\"));",
                    "await(collection).%s(CollectionConditions.any(String::isBlank).because(\"wrong\"));",
                    "await(collection).%s(CollectionConditions.none(String::isBlank).because(\"wrong\"));")) {
                String call = expression.formatted(terminal);
                assertRejected(call);
            }
        }
    }

    @Test
    void selectorsCannotMasqueradeAsTypedTransformations() throws IOException {
        for (String expression : List.of(
                "Condition<Optional<?>, Object> erased = present;",
                "Condition<Map<?, ?>, Object> erased = valueFor(\"key\");",
                "Object value = await(() -> List.of(Optional.of(1))).until(single(present));",
                "Object value = await(() -> List.of(Optional.of(1))).until(single(present.because(\"inner\")).because(\"outer\"));",
                "Object value = await(() -> Optional.of(Map.of(\"key\", 1))).until(hasValue(valueFor(\"key\")));",
                "Object value = await(() -> Optional.of(Map.of(\"key\", 1))).until(hasValue(valueFor(\"key\").because(\"inner\")).because(\"outer\"));",
                "List<Object> values = await(collection).until(single, isNotNull);",
                "List<Object> values = await(collection).until(single.because(\"first\"), isNotNull.because(\"second\"));",
                "Callable<Map<String, Number>> plain = map; Object value = await(plain).until(valueFor(\"key\"));",
                "Callable<Map<String, Number>> plain = map; Object value = await(plain).until(valueFor(\"key\").because(\"required\"));",
                "await(collection).until(CollectionConditions.sorted(String::compareTo).because(\"wrong type\"));")) {
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
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import static io.github.gromoff97.awium.MapConditions.*;
                final class Contract {
                    void check(Callable<Number> numbers, OptionalSource<Number> optional,
                            CollectionSource<List<Number>> collection, MapSource<Map<String, Number>> map) {
                        %s
                    }
                }
                """.formatted(body));
    }
}

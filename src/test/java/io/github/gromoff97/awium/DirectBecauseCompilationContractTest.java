package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DirectBecauseCompilationContractTest {

    @TempDir
    Path directory;

    @Test
    void matchesRejectsExistingConditions() throws IOException {
        for (String expression : List.of(
                "matches(equalTo(1));",
                "matches(yields(Number::toString));",
                "matches(present);",
                "matches(single);",
                "matches(valueFor(\"key\"));")) {
            assertFalse(compiles(expression), expression);
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
            assertFalse(compiles(expression), expression);
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
                assertFalse(compiles(call), call);
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
                "Source<Map<String, Number>> plain = map; Object value = await(plain).until(valueFor(\"key\"));",
                "Source<Map<String, Number>> plain = map; Object value = await(plain).until(valueFor(\"key\").because(\"required\"));",
                "await(collection).until(CollectionConditions.sorted(String::compareTo).because(\"wrong type\"));")) {
            assertFalse(compiles(expression), expression);
        }
    }

    private boolean compiles(String body) throws IOException {
        return CompilationSupport.compiles(directory, """
                import java.util.*;
                import io.github.gromoff97.awium.*;
                import io.github.gromoff97.awium.Source.*;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.OptionalConditions.*;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import static io.github.gromoff97.awium.MapConditions.*;
                final class Contract {
                    void check(Source<Number> numbers, OptionalSource<Number> optional,
                            CollectionSource<List<Number>> collection, MapSource<Map<String, Number>> map) {
                        %s
                    }
                }
                """.formatted(body));
    }
}

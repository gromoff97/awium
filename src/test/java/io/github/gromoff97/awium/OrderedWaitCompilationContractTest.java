package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderedWaitCompilationContractTest {

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"until", "tryUntil"})
    void predicatesMustBeExpressedThroughConditions(String terminal) throws IOException {
        for (String arguments : List.of("value -> true", "String::isBlank", "value -> true, value -> false")) {
            assertRejected("await(Contract::text).%s(%s);".formatted(terminal, arguments));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"until", "tryUntil"})
    void rejectsIncompatibleCallbackAndSourceTypes(String terminal) throws IOException {
        for (String expression : List.of(
                "await(Contract::text).%s(matches((Integer value) -> true));",
                "await(Contract::text).%s(matches(value -> value.length()));",
                "awaitOptional(Contract::optional).%s(first, last);",
                "awaitCollection(Contract::collection).%s(present, present);",
                "awaitMap(Contract::map).%s(first, last);",
                "awaitCollection(Contract::collection).%s(singleEntry, singleEntry);")) {
            assertRejected(expression.formatted(terminal));
        }
    }

    @Test
    void sequenceResultsCannotBeAssignedToSingleOrUnrelatedValues() throws IOException {
        for (String expression : List.of(
                "String value = await(Contract::text).until(isNotNull, isNotNull);",
                "AwaitResult<String, String> value = await(Contract::text).tryUntil(isNotNull, isNotNull);",
                "List<Integer> value = awaitCollection(Contract::collection).until(first, last);",
                "Callable<List<String>> source = Contract::collection; List<Integer> wrong = awaitCollection(source).until(first, last);",
                "Callable<List<String>> source = Contract::collection; AwaitResult<List<String>,List<Integer>> wrong = awaitCollection(source).tryUntil(first, last);",
                "List<Integer> result = await(Contract::text).until(instanceOf(String.class), instanceOf(String.class));")) {
            assertRejected(expression);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"until", "tryUntil"})
    void sequencesRejectDifferentResultTypes(String terminal) throws IOException {
        for (String decoration : List.of("", ".because(\"required type\")")) {
            for (String expression : List.of(
                    "await(Contract::text).%s(instanceOf(String.class)%s, instanceOf(Integer.class)%s);",
                    "await(Contract::text).<Object>%s(instanceOf(String.class)%s, instanceOf(Integer.class)%s);",
                    "awaitCollection(Contract::collection).%s(single(instanceOf(String.class))%s, single(instanceOf(Integer.class))%s);",
                    "awaitOptional(Contract::optional).%s(hasValue(instanceOf(String.class))%s, hasValue(instanceOf(Integer.class))%s);",
                    "awaitMap(Contract::map).%s(valueFor(\"ready\", instanceOf(String.class))%s, valueFor(\"ready\", instanceOf(Integer.class))%s);",
                    "awaitMap(Contract::map).%s(singleEntry(instanceOf(String.class))%s, singleEntry(instanceOf(Integer.class))%s);")) {
                assertRejected(expression.formatted(terminal, decoration, decoration));
            }
        }
    }

    @Test
    void emptyTerminalsAreRejected() throws IOException {
        assertRejected("await(Contract::text).until();");
        assertRejected("await(Contract::text).tryUntil();");
    }

    private void assertRejected(String body) throws IOException {
        CompilationSupport.assertRejected(directory, """
                import java.util.*;
                import java.util.concurrent.Callable;
                import io.github.gromoff97.awium.AwaitResult;
                import static io.github.gromoff97.awium.Conditions.await;
                import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                import static io.github.gromoff97.awium.MapConditions.awaitMap;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import static io.github.gromoff97.awium.OptionalConditions.present;
                import static io.github.gromoff97.awium.OptionalConditions.hasValue;
                import static io.github.gromoff97.awium.MapConditions.singleEntry;
                import static io.github.gromoff97.awium.MapConditions.valueFor;
                final class Contract {
                    static String text() { return "ready"; }
                    static Optional<String> optional() { return Optional.of("ready"); }
                    static List<String> collection() { return List.of("ready"); }
                    static Map<String,Integer> map() { return Map.of("ready", 1); }
                    void check() { %s }
                }
                """.formatted(body));
    }
}

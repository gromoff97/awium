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
                "await(Contract::optional).%s(firstElement, lastElement);",
                "await(Contract::collection).%s(present, present);",
                "await(Contract::map).%s(firstElement, lastElement);",
                "await(Contract::collection).%s(singleEntry, singleEntry);")) {
            assertRejected(expression.formatted(terminal));
        }
    }

    @Test
    void sequenceResultsCannotBeAssignedToSingleOrUnrelatedValues() throws IOException {
        for (String expression : List.of(
                "String value = await(Contract::text).until(isNotNull, isNotNull);",
                "AwaitResult<String, String> value = await(Contract::text).tryUntil(isNotNull, isNotNull);",
                "List<Integer> value = await(Contract::collection).until(firstElement, lastElement);",
                "Callable<List<String>> source = Contract::collection; List<Integer> wrong = await(source::call).until(firstElement, lastElement);",
                "Callable<List<String>> source = Contract::collection; AwaitResult<List<String>,List<Integer>> wrong = await(source::call).tryUntil(firstElement, lastElement);",
                "List<Integer> result = await(Contract::text).until(instanceOf(String.class), instanceOf(String.class));")) {
            assertRejected(expression);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"until", "tryUntil"})
    void sequencesRejectDifferentResultTypes(String terminal) throws IOException {
        for (String expression : List.of(
                "await(Contract::text).%s(instanceOf(String.class), instanceOf(Integer.class));",
                "await(Contract::text).<Object>%s(instanceOf(String.class), instanceOf(Integer.class));",
                "await(Contract::collection).%s(singleElement(instanceOf(String.class)), singleElement(instanceOf(Integer.class)));",
                "await(Contract::optional).%s(hasValue(instanceOf(String.class)), hasValue(instanceOf(Integer.class)));",
                "await(Contract::map).%s(valueFor(\"ready\", instanceOf(String.class)), valueFor(\"ready\", instanceOf(Integer.class)));",
                "await(Contract::map).%s(singleEntry(instanceOf(String.class)), singleEntry(instanceOf(Integer.class)));")) {
            assertRejected(expression.formatted(terminal));
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
                import static io.github.gromoff97.awium.Await.await;
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

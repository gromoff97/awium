package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;

class OrderedWaitCompilationContractTest {

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"until", "tryUntil"})
    void predicatesMustBeExpressedThroughConditions(String terminal) throws IOException {
        for (String arguments : List.of("value -> true", "String::isBlank", "value -> true, value -> false")) {
            assertFalse(compiles("await(Contract::text).%s(%s);".formatted(terminal, arguments)), arguments);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"until", "tryUntil"})
    void rejectsIncompatibleCallbackAndSourceTypes(String terminal) throws IOException {
        for (String expression : List.of(
                "await(Contract::text).%s(matches((Integer value) -> true));",
                "await(Contract::text).%s(matches(value -> value.length()));",
                "await(Contract::optional).%s(first, last);",
                "await(Contract::collection).%s(present, present);",
                "await(Contract::map).%s(first, last);",
                "await(Contract::collection).%s(singleEntry, singleEntry);",
                "Callable<List<String>> source = Contract::collection; List<String> wrong = await(source).%s(first, last);")) {
            assertFalse(compiles(expression.formatted(terminal)), expression);
        }
    }

    @Test
    void sequenceResultsCannotBeAssignedToSingleOrUnrelatedValues() throws IOException {
        for (String expression : List.of(
                "String value = await(Contract::text).until(isNotNull, isNotNull);",
                "AwaitResult<String, String> value = await(Contract::text).tryUntil(isNotNull, isNotNull);",
                "List<Integer> value = await(Contract::collection).until(first, last);",
                "List<Integer> result = await(Contract::text).until(instanceOf(String.class), instanceOf(String.class));")) {
            assertFalse(compiles(expression), expression);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"until", "tryUntil"})
    void genericResultSequencesAreNotAvailable(String terminal) throws IOException {
        for (String decoration : List.of("", ".because(\"required type\")")) {
            String expression = "await(Contract::text).%s(instanceOf(String.class)%s, instanceOf(String.class)%s);"
                    .formatted(terminal, decoration, decoration);
            assertFalse(compiles(expression), expression);
        }
    }

    @Test
    void transformationFactoriesAndPreservingAliasAreNotAvailable() throws IOException {
        assertFalse(CompilationSupport.compiles(directory,
                source("io.github.gromoff97.awium.Conditions.yields(String::trim);"), "yields"));
        assertFalse(CompilationSupport.compiles(directory,
                source("io.github.gromoff97.awium.Conditions.preserving(\"identity\", value -> io.github.gromoff97.awium.ConditionResult.satisfied(value));"), "preserving"));
    }

    @Test
    void sequenceDoesNotIntroduceAnEmptyTerminalOrPublicCapturedFactory() throws IOException {
        assertFalse(compiles("await(Contract::text).until();"));
        assertFalse(compiles("await(Contract::text).tryUntil();"));
        assertFalse(CompilationSupport.compiles(directory,
                source("io.github.gromoff97.awium.Conditions.captured(isNotNull, isNotNull);"), "captured"));
    }

    private boolean compiles(String body) throws IOException {
        return CompilationSupport.compiles(directory, source(body));
    }

    private static String source(String body) {
        return """
                import java.util.*;
                import java.util.concurrent.Callable;
                import java.util.function.Predicate;
                import io.github.gromoff97.awium.Condition;
                import io.github.gromoff97.awium.AwaitResult;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.ConditionResult.satisfied;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import static io.github.gromoff97.awium.OptionalConditions.present;
                import static io.github.gromoff97.awium.MapConditions.singleEntry;
                final class Contract {
                    static String text() { return "ready"; }
                    static Optional<String> optional() { return Optional.of("ready"); }
                    static List<String> collection() { return List.of("ready"); }
                    static Map<String,Integer> map() { return Map.of("ready", 1); }
                    void check() { %s }
                }
                """.formatted(body);
    }
}

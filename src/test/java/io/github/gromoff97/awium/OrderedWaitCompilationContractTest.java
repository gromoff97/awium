package io.github.gromoff97.awium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedWaitCompilationContractTest {

    @TempDir
    Path directory;

    @Test
    void oneConditionReturnsAValueAndSeveralReturnTypedOrderedLists() throws IOException {
        assertTrue(compiles("""
                String one = await(Contract::text).until(isNotNull);
                List<String> predicates = await(Contract::text).until(value -> value.startsWith("a"), value -> value.endsWith("z"));
                List<String> preserving = await(Contract::text).until(isNotNull, matches(String::isBlank).because("final"));
                List<String> expected = await(Contract::text).until(equalTo("created").because("creation"), equalTo("paid").because("payment"));
                List<String> many = await(Contract::text).until(equalTo("created"), equalTo("paid"), equalTo("finished"));
                List<Integer> transformed = await(Contract::text).until(yields(String::length), yields(String::length));
                List<String> optional = await(Contract::optional).until(present, present);
                List<String> collection = await(Contract::collection).until(first, last);
                List<Map.Entry<String,Integer>> map = await(Contract::map).until(singleEntry, singleEntry);
                List<Number> numbers = await(() -> (Number) 1).until(equalTo(1), equalTo(2L));
                Condition<Object, Integer> broad = condition("length", value -> satisfied(value.toString().length()));
                List<Integer> broadInput = await(Contract::text).until(broad, broad);
                List<Number> commonResult = await(Contract::text).until(yields(String::length), yields(value -> (long) value.length()));
                """));
    }

    @Test
    void diagnosticSequencesPreserveTheSourceAndCapturedResultTypes() throws IOException {
        assertTrue(compiles("""
                AwaitResult<String, String> one = await(Contract::text).tryUntil(isNotNull);
                AwaitResult<String, List<String>> predicates = await(Contract::text).tryUntil(value -> true, value -> false);
                AwaitResult<String, List<String>> preserving = await(Contract::text).tryUntil(isNotNull, matches(String::isBlank));
                AwaitResult<String, List<String>> expected = await(Contract::text).tryUntil(equalTo("created"), equalTo("paid"));
                AwaitResult<String, List<Integer>> transformed = await(Contract::text).tryUntil(yields(String::length), yields(String::length));
                AwaitResult<Optional<String>, List<String>> optional = await(Contract::optional).tryUntil(present, present);
                AwaitResult<List<String>, List<String>> collection = await(Contract::collection).tryUntil(first, last);
                AwaitResult<Map<String,Integer>, List<Map.Entry<String,Integer>>> map = await(Contract::map).tryUntil(singleEntry, singleEntry);
                """));
    }

    @ParameterizedTest
    @ValueSource(strings = {"until", "tryUntil"})
    void rejectsIncompatibleOperandsAndMixedConditionFamilies(String terminal) throws IOException {
        for (String expression : List.of(
                "await(Contract::text).%s(equalTo(1), equalTo(2));",
                "await(Contract::optional).%s(first, last);",
                "await(Contract::collection).%s(present, present);",
                "await(Contract::map).%s(first, last);",
                "await(Contract::collection).%s(singleEntry, singleEntry);",
                "await(Contract::collection).%s(single, isNotNull);",
                "await(Contract::optional).%s(present, matches((Optional<String> value) -> true));",
                "await(Contract::text).%s(equalTo(\"ready\"), isNotNull);",
                "await(Contract::text).%s((String value) -> true, matches((String value) -> true));",
                "await(Contract::text).%s(isNotNull, yields(String::length));",
                "Source<List<String>> source = Contract::collection; await(source).%s(first, last);")) {
            assertFalse(compiles(expression.formatted(terminal)), expression);
        }
    }

    @Test
    void sequenceResultsCannotBeAssignedToSingleOrUnrelatedValues() throws IOException {
        for (String expression : List.of(
                "String value = await(Contract::text).until(isNotNull, isNotNull);",
                "AwaitResult<String, String> value = await(Contract::text).tryUntil(isNotNull, isNotNull);",
                "List<Integer> value = await(Contract::collection).until(first, last);",
                "List<Integer> result = await(Contract::text).until(yields(String::length), yields(value -> (long) value.length()));")) {
            assertFalse(compiles(expression), expression);
        }
    }

    @Test
    void sequenceDoesNotIntroduceAnEmptyTerminalOrPublicCapturedFactory() throws IOException {
        assertFalse(compiles("await(Contract::text).until();"));
        assertFalse(compiles("await(Contract::text).tryUntil();"));
        assertFalse(compiles("await(Contract::text).until((String value) -> true);"));
        assertFalse(CompilationSupport.compiles(directory,
                source("io.github.gromoff97.awium.Conditions.captured(isNotNull, isNotNull);"), "captured"));
    }

    private boolean compiles(String body) throws IOException {
        return CompilationSupport.compiles(directory, source(body));
    }

    private static String source(String body) {
        return """
                import java.util.*;
                import io.github.gromoff97.awium.Condition;
                import io.github.gromoff97.awium.AwaitResult;
                import io.github.gromoff97.awium.Source;
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

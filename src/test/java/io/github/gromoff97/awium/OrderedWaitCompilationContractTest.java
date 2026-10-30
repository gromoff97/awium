package io.github.gromoff97.awium;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderedWaitCompilationContractTest {

    @ParameterizedTest
    @ValueSource(strings = {"await", "tryAwait"})
    void predicatesMustBeExpressedThroughConditions(String factory) {
        for (String arguments : List.of("value -> true", "String::isBlank", "value -> true, value -> false")) {
            assertRejected("%s(Contract::text).until(%s);".formatted(factory, arguments));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"await", "tryAwait"})
    void rejectsIncompatibleCallbackAndSourceTypes(String factory) {
        for (String expression : List.of(
                "%s(Contract::text).until(matches((Integer value) -> true));",
                "%s(Contract::text).until(matches(value -> value.length()));",
                "%s(Contract::optional).until(captured(firstElement, lastElement));",
                "%s(Contract::collection).until(captured(present, present));",
                "%s(Contract::map).until(captured(firstElement, lastElement));",
                "%s(Contract::collection).until(captured(singleEntry, singleEntry));")) {
            assertRejected(expression.formatted(factory));
        }
    }

    @Test
    void sequenceResultsCannotBeAssignedToSingleOrUnrelatedValues() {
        for (String expression : List.of(
                "String value = await(Contract::text).until(captured(isNotNull, isNotNull));",
                "AwaitResult<String, String> value = tryAwait(Contract::text).until(captured(isNotNull, isNotNull));",
                "List<Integer> value = await(Contract::collection).until(captured(firstElement, lastElement));",
                "Callable<List<String>> source = Contract::collection; List<Integer> wrong = await(source::call).until(captured(firstElement, lastElement));",
                "Callable<List<String>> source = Contract::collection; AwaitResult<List<String>, List<Integer>> wrong = tryAwait(source::call).until(captured(firstElement, lastElement));",
                "List<Integer> result = await(Contract::text).until(captured(instanceOf(String.class), instanceOf(String.class)));")) {
            assertRejected(expression);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"await", "tryAwait"})
    void terminalsRequireOneExplicitCondition(String factory) {
        for (String arguments : List.of("", "isNotNull, isNotNull", "present, present",
                "singleElement, singleElement", "valueFor(\"ready\"), valueFor(\"ready\")",
                "instanceOf(String.class), instanceOf(String.class)",
                "new io.github.gromoff97.awium.Condition.PreservingCondition<?>[]{isNotNull}")) {
            assertRejected("%s(Contract::text).until(%s);".formatted(factory, arguments));
        }
    }

    private void assertRejected(String body) {
        CompilationSupport.assertRejected("""
                import java.util.*;
                import java.util.concurrent.Callable;
                import io.github.gromoff97.awium.result.AwaitResult;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Await.tryAwait;
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

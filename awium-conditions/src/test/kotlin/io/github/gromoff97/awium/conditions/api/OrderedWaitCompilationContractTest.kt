package io.github.gromoff97.awium.conditions.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OrderedWaitCompilationContractTest {

    @ParameterizedTest
    @ValueSource(strings = ["await", "tryAwait"])
    fun `rejects incompatible callback and source types`(factory: String) {
        for (expression in listOf(
                "%s(Contract::text).until(matches((Integer value) -> true));",
                """%s(Contract::text).until(captured(captured(equalTo("ready"))));""",
                "%s(Contract::text).until(matches(value -> value.length()));",
                "%s(Contract::optional).until(captured(firstElement, lastElement));",
                "%s(Contract::collection).until(captured(present, present));",
                "%s(Contract::map).until(captured(firstElement, lastElement));",
                "%s(Contract::collection).until(captured(singleEntry, singleEntry));")) {
            assertRejected(expression.format(factory))
        }
    }

    @Test
    fun `sequence results cannot be assigned to single or unrelated values`() {
        for (expression in listOf(
                "String value = await(Contract::text).until(captured(isNotNull, isNotNull));",
                "AwaitResult<String, String> value = tryAwait(Contract::text).until(captured(isNotNull, isNotNull));",
                "List<Integer> value = await(Contract::collection).until(captured(firstElement, lastElement));",
                "Callable<List<String>> source = Contract::collection; List<Integer> wrong = await(source::call).until(captured(firstElement, lastElement));",
                "Callable<List<String>> source = Contract::collection; AwaitResult<List<String>, List<Integer>> wrong = tryAwait(source::call).until(captured(firstElement, lastElement));",
                "List<Integer> result = await(Contract::text).until(captured(instanceOf(String.class), instanceOf(String.class)));")) {
            assertRejected(expression)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["await", "tryAwait"])
    fun `terminals require one explicit condition`(factory: String) {
        for (arguments in listOf("", "isNotNull, isNotNull", "present, present",
                "singleElement, singleElement", """valueFor("ready"), valueFor("ready")""",
                "instanceOf(String.class), instanceOf(String.class)",
                "new io.github.gromoff97.awium.AwaitCondition.PreservingCondition<?>[]{isNotNull}")) {
            assertRejected("%s(Contract::text).until(%s);".format(factory, arguments))
        }
    }

    private fun assertRejected(body: String) {
        CompilationSupport.assertRejected("""
                import java.util.*;
                import java.util.concurrent.Callable;
                import io.github.gromoff97.awium.result.AwaitResult;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Await.tryAwait;
                import static io.github.gromoff97.awium.conditions.Conditions.*;
                import static io.github.gromoff97.awium.conditions.CollectionConditions.*;
                import static io.github.gromoff97.awium.conditions.OptionalConditions.present;
                import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
                import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
                import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
                final class Contract {
                    static String text() { return "ready"; }
                    static Optional<String> optional() { return Optional.of("ready"); }
                    static List<String> collection() { return List.of("ready"); }
                    static Map<String,Integer> map() { return Map.of("ready", 1); }
                    void check() { %s }
                }
                """.trimIndent().format(body))
    }
}

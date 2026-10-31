package io.github.gromoff97.awium.api;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class CompilationContractTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "() -> (Integer actual) -> satisfied(actual)",
            "() -> (String actual) -> satisfied(actual)"
    })
    void executionFactoriesMustMatchTheirInputAndResultTypes(String factory) {
        assertRejected("""
                import io.github.gromoff97.awium.execution.ConditionExecution;
                import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
                final class Contract {
                    void check() {
                        new ConditionExecution<String, Integer>(%s);
                    }
                }
                """.formatted(factory));
    }

    @ParameterizedTest
    @MethodSource("invalidFixtures")
    void negativeFixturesRejectValidCodeAndUnrelatedErrors(String source) {
        assertThrows(AssertionError.class, () -> assertRejected(source));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "String source = \"value\";",
            "java.util.function.Supplier<String> source = () -> \"value\";"
    })
    void excludedDirectAndJdkSourceTypesDoNotCompile(String declaration) {
        assertRejected("""
                final class Contract {
                    void check() {
                        %s
                        await(source);
                    }
                }
                """.formatted(declaration));
    }

    @ParameterizedTest
    @ValueSource(strings = {"await(StringBuilder::new);", "await(Optional::empty);", "await(() -> null);"})
    void ambiguousSourcesNeedALambdaOrAnExplicitSourceCategory(String expression) {
        assertRejected("""
                final class Contract { void check() { %s } }
                """.formatted(expression));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SelectedCondition<Optional<?>>", "SelectedCondition<Collection<?>>", "SelectedCondition<Map<?, ?>>"
    })
    void categorySpecificTerminalsRejectWrongConditions(String type) {
        assertRejected("""
                import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
                final class Contract {
                    void check(Callable<String> source, %s condition) {
                        await(source).until(condition);
                    }
                }
                """.formatted(type));
    }

    static Stream<Arguments> invalidFixtures() {
        return Stream.of(
                argumentSet("valid code", "final class Contract {}"),
                argumentSet("syntax error", "final class Contract { void broken( }"),
                argumentSet("missing import", "import missing.Type; final class Contract {}"),
                argumentSet("unknown method", """
                        final class Contract {
                            void check(Callable<String> source) { await(source).utnil(); }
                        }
                        """));
    }

    private static void assertRejected(String source) {
        CompilationSupport.assertRejected("""
                import java.util.*;
                import java.util.concurrent.Callable;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Await.tryAwait;
                """ + source);
    }
}

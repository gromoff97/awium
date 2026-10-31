package io.github.gromoff97.awium;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void negativeFixturesRejectValidCodeAndUnrelatedErrors() {
        assertThrows(AssertionError.class,
                () -> assertRejected("final class Contract {}"));
        assertThrows(AssertionError.class,
                () -> assertRejected("final class Contract { void broken( }"));
        assertThrows(AssertionError.class, () -> assertRejected("""
                import missing.Type;
                final class Contract {}
                """));
        assertThrows(AssertionError.class, () -> assertRejected("""

                final class Contract {
                    void check(Callable<String> source) {
                        await(source).utnil();
                    }
                }
                """));
    }

    @Test
    void excludedDirectAndJdkSourceTypesDoNotCompile() {
        for (String declaration : new String[] {
                "String source = \"value\";",
                "java.util.function.Supplier<String> source = () -> \"value\";"
        }) {
            assertRejected("""
                    final class Contract {
                        void check() {
                            %s
                            await(source);
                        }
                    }
                    """.formatted(declaration));
        }
    }

    @Test
    void ambiguousSourcesNeedALambdaOrAnExplicitSourceCategory() {
        for (String expression : List.of("await(StringBuilder::new);", "await(Optional::empty);", "await(() -> null);")) {
            assertRejected("""
                    final class Contract { void check() { %s } }
                    """.formatted(expression));
        }
    }

    @Test
    void categorySpecificTerminalsRejectWrongConditions() {
        for (String type : List.of(
                "SelectedCondition<Optional<?>>",
                "SelectedCondition<Collection<?>>",
                "SelectedCondition<Map<?, ?>>")) {
            assertRejected("""
                    import io.github.gromoff97.awium.*;
                    import io.github.gromoff97.awium.result.*;
                    import io.github.gromoff97.awium.function.*;
                    import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
                    final class Contract {
                        void check(Callable<String> source, %s condition) {
                            await(source).until(condition);
                        }
                    }
                    """.formatted(type));
        }
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

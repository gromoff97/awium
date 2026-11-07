package io.github.gromoff97.awium.api;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class CompilationContractTest {

    @TestFactory
    Stream<DynamicTest> predicatesMustBeExpressedThroughConditions() {
        return Stream.of("await", "tryAwait").flatMap(factory ->
                Stream.of("value -> true", "String::isBlank", "value -> true, value -> false")
                        .map(arguments -> "%s(Contract::text).until(%s);".formatted(factory, arguments))
                        .map(expression -> dynamicTest(expression, () -> assertRejected("""
                                final class Contract {
                                    static String text() { return "ready"; }
                                    void check() { %s }
                                }
                                """.formatted(expression)))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(Integer actual) -> satisfied(actual)",
            "(String actual) -> satisfied(actual)"
    })
    void attemptsMustMatchTheirInputAndResultTypes(String attempt) {
        assertRejected("""
                import io.github.gromoff97.awium.function.AwaitAttempt;
                import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
                final class Contract {
                    AwaitAttempt<String, Integer> attempt = %s;
                }
                """.formatted(attempt));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AwaitAttempt<String, String> attempt = bind(definition);",
            "AwaitAttempt<String, String> attempt = AwaitAttempt.<String, String>bind(length);"
    })
    void bindingCannotInventAResultType(String expression) {
        assertRejected("""
                import io.github.gromoff97.awium.Condition;
                import io.github.gromoff97.awium.function.AwaitAttempt;
                import static io.github.gromoff97.awium.function.AwaitAttempt.bind;
                final class Contract {
                    void check(Condition<String, Integer> length) {
                        Condition<?, ?> definition = length;
                        %s
                    }
                }
                """.formatted(expression));
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

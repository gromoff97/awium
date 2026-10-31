package io.github.gromoff97.awium.api;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class OrderedWaitCompilationContractTest {

    @TestFactory
    Stream<DynamicTest> predicatesMustBeExpressedThroughConditions() {
        return Stream.of("await", "tryAwait").flatMap(factory ->
                List.of("value -> true", "String::isBlank", "value -> true, value -> false").stream()
                        .map(arguments -> "%s(Contract::text).until(%s);".formatted(factory, arguments))
                        .map(expression -> dynamicTest(expression, () -> assertRejected(expression))));
    }

    private void assertRejected(String body) {
        CompilationSupport.assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Await.tryAwait;
                final class Contract {
                    static String text() { return "ready"; }
                    void check() { %s }
                }
                """.formatted(body));
    }
}

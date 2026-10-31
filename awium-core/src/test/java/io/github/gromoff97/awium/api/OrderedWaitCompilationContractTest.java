package io.github.gromoff97.awium.api;

import java.util.List;
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

    private void assertRejected(String body) {
        CompilationSupport.assertRejected("""
                import java.util.*;
                import java.util.concurrent.Callable;
                import io.github.gromoff97.awium.result.AwaitResult;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Await.tryAwait;
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

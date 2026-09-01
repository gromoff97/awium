package io.github.gromoff97.awium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExpectedConditionCompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void expectedConditionsRetainCompatibleSourceTypes() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.*;
                import static io.github.gromoff97.awium.conditions.Conditions.*;
                import io.github.gromoff97.awium.results.AwaitResult;
                import io.github.gromoff97.awium.sources.Source;

                final class Contract {
                    static class Parent {}
                    static final class Child extends Parent {}

                    void check(Source<Number> numbers, Source<Parent> parents, Source<Object> objects) {
                        Number number = await(numbers).until(equalTo(42));
                        Parent child = await(parents).until(equalTo(new Child()).because("business identity"));
                        Object anything = await(objects).until(in(42, "ready"));
                        Number absent = await(numbers).until(equalTo(null));
                        AwaitResult<Number, Number> attempted = tryAwait(numbers).until(notEqualTo(0));
                    }
                }
                """));
    }

    @Test
    void expectedConditionsRejectUnrelatedAndWiderTypes() throws IOException {
        for (String invocation : List.of(
                "await(strings).until(equalTo(42))",
                "await(strings).until(notEqualTo(42))",
                "await(strings).until(sameAs(42))",
                "await(strings).until(notSameAs(42))",
                "await(strings).until(in(1, 2))",
                "await(strings).until(notIn(1, 2))",
                "await(children).until(equalTo(new Parent()))")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.await.Await.await;
                    import static io.github.gromoff97.awium.conditions.Conditions.*;
                    import io.github.gromoff97.awium.sources.Source;

                    final class Contract {
                        static class Parent {}
                        static final class Child extends Parent {}

                        void check(Source<String> strings, Source<Child> children) {
                            %s;
                        }
                    }
                    """.formatted(invocation)), invocation);
        }
    }

    @Test
    void nestedExpectedConditionsRetainContainerValueTypes() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditions.Conditions.equalTo;
                import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
                import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
                import io.github.gromoff97.awium.sources.Source.MapSource;
                import io.github.gromoff97.awium.sources.Source.OptionalSource;
                import java.util.Map;

                final class Contract {
                    void check(OptionalSource<Number> optional, MapSource<Map<String, Number>> map) {
                        Number optionalValue = await(optional).until(hasValue(equalTo(42)));
                        Number mapValue = await(map).until(valueFor("answer", equalTo(42)));
                    }
                }
                """));

        for (String invocation : List.of(
                "await(optional).until(hasValue(equalTo(42)))",
                "await(map).until(valueFor(\"answer\", equalTo(42)))")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.await.Await.await;
                    import static io.github.gromoff97.awium.conditions.Conditions.equalTo;
                    import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
                    import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
                    import io.github.gromoff97.awium.sources.Source.MapSource;
                    import io.github.gromoff97.awium.sources.Source.OptionalSource;
                    import java.util.Map;

                    final class Contract {
                        void check(OptionalSource<String> optional, MapSource<Map<String, String>> map) {
                            %s;
                        }
                    }
                    """.formatted(invocation)), invocation);
        }
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source);
    }
}

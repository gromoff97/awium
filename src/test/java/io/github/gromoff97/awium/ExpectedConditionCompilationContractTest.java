package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpectedConditionCompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void expectedConditionsRetainCompatibleSourceTypes() throws IOException {
        assertTrue(compiles("""
                static class Parent {}
                static final class Child extends Parent {}

                void check(Source<Number> numbers, Source<Parent> parents, Source<Object> objects) {
                    Number number = await(numbers).until(equalTo(42));
                    Condition<Parent, Parent> childCondition = equalTo(new Child());
                    Parent child = await(parents).until(childCondition.because("business identity"));
                    Object anything = await(objects).until(in(42, "ready"));
                    Number absent = await(numbers).until(equalTo(null));
                    AwaitResult<Number, Number> attempted = await(numbers).tryUntil(notEqualTo(0));
                }
                """));
    }

    @Test
    void expectedConditionsAllowRuntimeEqualityAcrossTypes() throws IOException {
        for (String invocation : List.of(
                "await(strings).until(equalTo(42))",
                "await(strings).until(notEqualTo(42))",
                "await(strings).until(sameAs(42))",
                "await(strings).until(notSameAs(42))",
                "await(strings).until(in(1, 2))",
                "await(strings).until(notIn(1, 2))",
                "await(children).until(equalTo(new Parent()))")) {
            assertTrue(compiles("""
                    static class Parent {}
                    static final class Child extends Parent {}

                    void check(Source<String> strings, Source<Child> children) {
                        %s;
                    }
                    """.formatted(invocation)), invocation);
        }
    }

    @Test
    void nestedExpectedConditionsRetainContainerValueTypes() throws IOException {
        assertTrue(compiles("""
                void check(OptionalSource<Number> optional, MapSource<Map<String, Number>> map) {
                    Number optionalValue = await(optional).until(hasValue(equalTo(42)));
                    Number mapValue = await(map).until(valueFor("answer", equalTo(42)));
                }
                """));

        for (String invocation : List.of(
                "await(optional).until(hasValue(equalTo(42)))",
                "await(map).until(valueFor(\"answer\", equalTo(42)))")) {
            assertTrue(compiles("""
                    void check(OptionalSource<String> optional, MapSource<Map<String, String>> map) {
                        String result = %s;
                    }
                    """.formatted(invocation)), invocation);
        }
    }

    @Test
    void equalityConditionsKeepTheObservedResultTypeThroughTheCommonModel() throws IOException {
        assertTrue(compiles("""
                void check(Source<String> strings) {
                    Condition<String, String> ready = equalTo("ready");
                    String value = await(strings).until(ready.because("required"));
                    java.util.List<String> states = await(strings).until(isNotNull, equalTo("ready"));
                    AwaitResult<String, java.util.List<String>> captured = await(strings).tryUntil(ready, yields(String::trim));
                }
                """));
        assertFalse(compiles("""
                void check(Source<String> strings) {
                    Integer wrong = await(strings).until(equalTo(42));
                }
                """));
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.Await.*;
                import static io.github.gromoff97.awium.Conditions.*;
                import io.github.gromoff97.awium.AwaitResult;
                import io.github.gromoff97.awium.Condition;
                import io.github.gromoff97.awium.Source;
                import static io.github.gromoff97.awium.MapConditions.valueFor;
                import static io.github.gromoff97.awium.OptionalConditions.hasValue;
                import io.github.gromoff97.awium.Source.MapSource;
                import io.github.gromoff97.awium.Source.OptionalSource;
                import java.util.Map;
                final class Contract {
                    %s
                }
                """.formatted(source));
    }
}

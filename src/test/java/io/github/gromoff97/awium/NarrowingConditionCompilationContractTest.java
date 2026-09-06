package io.github.gromoff97.awium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NarrowingConditionCompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void narrowingConditionsReturnCompatibleSubtypes() throws IOException {
        assertTrue(compiles("""
                void check(Source<Number> numbers, Source<Object> objects,
                        OptionalSource<Number> optional, MapSource<Map<String, Number>> map,
                        CollectionSource<List<Number>> collection) {
                    Integer number = await(numbers).until(instanceOf(Integer.class).because("business subtype"));
                    String object = await(objects).until(exactInstanceOf(String.class));
                    Integer present = await(optional).until(hasValue(instanceOf(Integer.class)));
                    AwaitResult<Optional<Number>, Integer> nested = await(optional).tryUntil(hasValue(instanceOf(Integer.class).because("business subtype")));
                    Integer value = await(map).until(valueFor("answer", instanceOf(Integer.class)));
                    Integer element = await(collection).until(single(instanceOf(Integer.class)));
                    AwaitResult<Number, Integer> attempted = await(numbers).tryUntil(instanceOf(Integer.class));
                }
                """));
    }

    @Test
    void narrowingConditionsRejectUnrelatedAndWiderTypes() throws IOException {
        for (String invocation : List.of(
                "await(text).until(instanceOf(Integer.class))",
                "await(integer).until(instanceOf(Number.class))",
                "await(optional).until(hasValue(instanceOf(Integer.class)))",
                "await(optional).tryUntil(hasValue(instanceOf(Integer.class)))",
                "await(map).until(valueFor(\"answer\", instanceOf(Integer.class)))",
                "await(collection).until(single(instanceOf(Integer.class)))")) {
            assertFalse(compiles("""
                    void check(Source<String> text, Source<Integer> integer,
                            OptionalSource<String> optional, MapSource<Map<String, String>> map,
                            CollectionSource<List<String>> collection) {
                        %s;
                    }
                    """.formatted(invocation)), invocation);
        }
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.await.Await.*;
                import static io.github.gromoff97.awium.conditions.CollectionConditions.single;
                import static io.github.gromoff97.awium.conditions.Conditions.*;
                import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
                import static io.github.gromoff97.awium.conditions.OptionalConditions.*;
                import io.github.gromoff97.awium.results.AwaitResult;
                import io.github.gromoff97.awium.sources.Source;
                import io.github.gromoff97.awium.sources.Source.*;
                import java.util.*;
                final class Contract {
                    %s
                }
                """.formatted(source));
    }
}

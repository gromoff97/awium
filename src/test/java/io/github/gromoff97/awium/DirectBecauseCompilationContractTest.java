package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DirectBecauseCompilationContractTest {

    @TempDir
    Path directory;

    @Test
    void decorationCannotInventInputOrResultTypes() throws IOException {
        for (String expression : List.of(
                "String wrong = await(numbers).until(equalTo(1).because(\"wrong\"));",
                "String wrong = await(collection).until(single(equalTo(1)).because(\"wrong\"));",
                "String wrong = await(map).until(valueFor(\"key\").because(\"wrong\"));",
                "Map.Entry<String, Number> wrong = await(map).until(valueFor(\"key\").because(\"wrong\"));",
                "Number wrong = await(map).until(entryFor(\"key\").because(\"wrong\"));",
                "Condition.SelectedCondition<Map<?, ?>> wrong = valueFor(\"key\").because(\"wrong\");",
                "await(map).until(singleEntry(matches((Map.Entry<String, Object> entry) -> entry.setValue(new Object()) != null)).because(\"wrong\"));",
                "await(collection).until(single(matches(String::isBlank)).because(\"wrong\"));",
                "AwaitResult<Map<String, Number>, String> wrong = await(map).tryUntil(valueFor(\"key\").because(\"wrong\"));",
                "List<String> wrong = await(map).until(valueFor(\"key\").because(\"wrong\"), valueFor(\"key\"));")) {
            assertFalse(compiles(expression), expression);
        }
    }

    private boolean compiles(String body) throws IOException {
        return CompilationSupport.compiles(directory, """
                import java.util.*;
                import io.github.gromoff97.awium.*;
                import io.github.gromoff97.awium.Source.*;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.OptionalConditions.*;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import static io.github.gromoff97.awium.MapConditions.*;
                final class Contract {
                    void check(Source<Number> numbers, OptionalSource<Number> optional,
                            CollectionSource<List<Number>> collection, MapSource<Map<String, Number>> map) {
                        %s
                    }
                }
                """.formatted(body));
    }
}

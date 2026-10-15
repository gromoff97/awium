package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NestedSelectionCompilationContractTest {

    @TempDir
    Path directory;

    @Test
    void nestedSelectionRejectsIncompatibleChecksAndResults() throws IOException {
        for (String body : List.of(
                "await(Contract::payments).until(single(matches(String::isBlank)));",
                "String wrong = await(Contract::payments).until(single(matches(Payment::paid)));",
                "AwaitResult<List<Payment>, String> wrong = await(Contract::payments).tryUntil(single(matches(Payment::paid)));",
                "List<String> wrong = await(Contract::payments).until(single(matches(Payment::paid)), single(matches(Payment::paid)));")) {
            assertFalse(compiles(body), body);
        }
    }

    private boolean compiles(String body) throws IOException {
        return CompilationSupport.compiles(directory, """
                import java.util.*;
                import io.github.gromoff97.awium.AwaitResult;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import static io.github.gromoff97.awium.MapConditions.singleEntry;
                final class Contract {
                    record Payment(int id, boolean paid) {}
                    static List<Payment> payments() { return List.of(new Payment(1, true)); }
                    static Map<String,Payment> map() { return Map.of("key", new Payment(1, true)); }
                    void check() { %s }
                }
                """.formatted(body));
    }
}

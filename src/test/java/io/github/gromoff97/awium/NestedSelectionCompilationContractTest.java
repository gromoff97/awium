package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NestedSelectionCompilationContractTest {

    @TempDir
    Path directory;

    @Test
    void nestedSelectionRejectsIncompatibleChecksAndResults() throws IOException {
        for (String body : List.of(
                "awaitCollection(Contract::payments).until(single(matches(String::isBlank)));",
                "String wrong = awaitCollection(Contract::payments).until(single(matches(Payment::paid)));",
                "AwaitResult<List<Payment>, String> wrong = awaitCollection(Contract::payments).tryUntil(single(matches(Payment::paid)));",
                "List<String> wrong = awaitCollection(Contract::payments).until(single(matches(Payment::paid)), single(matches(Payment::paid)));")) {
            CompilationSupport.assertRejected(directory, """
                    import java.util.List;
                    import io.github.gromoff97.awium.AwaitResult;
                    import static io.github.gromoff97.awium.Conditions.await;
                    import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                    import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                    import static io.github.gromoff97.awium.MapConditions.awaitMap;
                    import static io.github.gromoff97.awium.Conditions.*;
                    import static io.github.gromoff97.awium.CollectionConditions.single;
                    final class Contract {
                        record Payment(int id, boolean paid) {}
                        static List<Payment> payments() { return List.of(new Payment(1, true)); }
                        void check() { %s }
                    }
                    """.formatted(body));
        }
    }
}

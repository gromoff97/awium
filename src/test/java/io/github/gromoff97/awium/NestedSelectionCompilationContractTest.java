package io.github.gromoff97.awium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedSelectionCompilationContractTest {

    @TempDir
    Path directory;

    @Test
    void nestedSelectionInfersValuesAndSequencesWithoutPreparatoryVariables() throws IOException {
        assertTrue(compiles("""
                Payment one = await(Contract::payments).until(single(Payment::paid));
                Payment checked = await(Contract::payments).until(single(matches(Payment::paid).because("paid")));
                var inferred = await(Contract::payments).until(single(matches(value -> value.paid())));
                boolean paid = inferred.paid();
                int id = await(Contract::payments).until(single(yields(Payment::id)));
                Payment expected = await(Contract::payments).until(single(equalTo(new Payment(1, true))));
                Integer narrowed = await(() -> List.<Number>of(1)).until(single(instanceOf(Integer.class)));
                Number number = await(() -> List.<Number>of(1)).until(single(equalTo(1)));
                String key = await(Contract::map).until(singleEntry(yields(Map.Entry::getKey)));
                List<Payment> stages = await(Contract::payments).until(single(Payment::paid), single(matches(Payment::paid).because("final")));
                AwaitResult<List<Payment>, Payment> diagnostic = await(Contract::payments).tryUntil(single(matches(Payment::paid).because("paid")));
                AwaitResult<List<Payment>, List<Integer>> ids = await(Contract::payments).tryUntil(single(yields(Payment::id)), single(yields(Payment::id)));
                AwaitResult<Map<String, Payment>, String> map = await(Contract::map).tryUntil(singleEntry(yields(Map.Entry::getKey)));
                """));
    }

    @Test
    void nestedSelectionRejectsIncompatibleChecksAndResults() throws IOException {
        for (String body : List.of(
                "await(Contract::payments).until(single(equalTo(42)));",
                "await(Contract::payments).tryUntil(single(equalTo(42)));",
                "await(Contract::payments).until(single(instanceOf(String.class)));",
                "await(Contract::payments).until(single(matches(String::isBlank)));",
                "String wrong = await(Contract::payments).until(single(Payment::paid));",
                "AwaitResult<List<Payment>, String> wrong = await(Contract::payments).tryUntil(single(Payment::paid));",
                "await(Contract::map).until(singleEntry(equalTo(42)));",
                "List<String> wrong = await(Contract::payments).until(single(Payment::paid), single(Payment::paid));")) {
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

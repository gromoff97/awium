package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryUntilCompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void infersEverySourceAndConditionResult() throws IOException {
        assertTrue(compiles("""
                record Payment(String status) {}

                void check(Source<String> text, Source.OptionalSource<Payment> payment,
                        Source.CollectionSource<List<Payment>> payments,
                        Source.MapSource<Map<String, Payment>> paymentMap,
                        Source<Payment> rawPayment) {
                    var important = isNotNull.because("business availability");
                    AwaitResult<String, String> ordinary =
                            await(text).every(ofMillis(1)).upTo(ofSeconds(1)).persisting(ZERO).tryUntil(important);
                    AwaitResult<Optional<Payment>, Payment> optional = await(payment).tryUntil(present);
                    AwaitResult<List<Payment>, Payment> collection = await(payments).tryUntil(single);
                    AwaitResult<List<Payment>, Payment> firstPayment = await(payments).tryUntil(first);
                    AwaitResult<List<Payment>, Payment> lastPayment = await(payments).tryUntil(last);
                    AwaitResult<Map<String, Payment>, Map.Entry<String, Payment>> map =
                            await(paymentMap).tryUntil(singleEntry);
                    AwaitResult<Payment, String> transformed =
                            await(rawPayment).tryUntil(yields(Payment::status));
                    await(text).tryUntil(isNotNull);
                    await(text).upTo(ofSeconds(1)).every(ofMillis(1)).tryUntil(isNotNull);
                }
                """));
    }

    @Test
    void rejectsAnUnrelatedOptionalResultType() throws IOException {
        assertFalse(compiles("""
                void check(OptionalSource<Integer> source) {
                    AwaitResult<Optional<Integer>, String> result = await(source).tryUntil(present);
                }
                """));
    }

    @Test
    void specializedSourcesRetainSelectedResultTypes() throws IOException {
        assertTrue(compiles("""
                void check(OptionalSource<String> optional,
                        CollectionSource<List<String>> collection,
                        MapSource<Map<String, Integer>> map) {
                    AwaitResult<Optional<String>, String> optionalResult = await(optional).tryUntil(present);
                    AwaitResult<List<String>, String> collectionResult = await(collection).tryUntil(single);
                    AwaitResult<List<String>, String> firstResult = await(collection).tryUntil(first);
                    AwaitResult<Map<String, Integer>, Map.Entry<String, Integer>> mapResult =
                            await(map).tryUntil(singleEntry);
                }
                """));
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.first;
                import static io.github.gromoff97.awium.CollectionConditions.last;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import static io.github.gromoff97.awium.Conditions.yields;
                import static io.github.gromoff97.awium.MapConditions.singleEntry;
                import static io.github.gromoff97.awium.Conditions.isNotNull;
                import static io.github.gromoff97.awium.OptionalConditions.present;
                import static java.time.Duration.ZERO;
                import static java.time.Duration.ofMillis;
                import static java.time.Duration.ofSeconds;
                import io.github.gromoff97.awium.AwaitResult;
                import io.github.gromoff97.awium.Source;
                import io.github.gromoff97.awium.Source.*;
                import java.util.*;
                final class Contract {
                    %s
                }
                """.formatted(source));
    }
}

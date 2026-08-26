package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryAwaitCompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void infersEverySourceAndConditionResult() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.TryAwait.tryAwait;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.first;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.last;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.single;
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.yields;
                import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.singleEntry;
                import static io.github.gromoff97.awium.conditioning.conditions.ObjectCondition.isNotNull;
                import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.present;
                import static java.time.Duration.ZERO;
                import static java.time.Duration.ofMillis;
                import static java.time.Duration.ofSeconds;
                import io.github.gromoff97.awium.await.AwaitResult;
                import io.github.gromoff97.awium.sources.Source;
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;

                final class Contract {
                    record Payment(String status) {}

                    void check(Source<String> text, Source.OptionalSource<Payment> payment,
                            Source.CollectionSource<List<Payment>> payments,
                            Source.MapSource<Map<String, Payment>> paymentMap,
                            Source<Payment> rawPayment) {
                        AwaitResult<String, String> ordinary = tryAwait(text).every(ofMillis(1))
                                .upTo(ofSeconds(1)).stableFor(ZERO)
                                .until(isNotNull.because("business availability"));
                        AwaitResult<Optional<Payment>, Payment> optional = tryAwait(payment).until(present);
                        AwaitResult<List<Payment>, Payment> collection = tryAwait(payments).until(single);
                        AwaitResult<List<Payment>, Payment> firstPayment = tryAwait(payments).until(first);
                        AwaitResult<List<Payment>, Payment> lastPayment = tryAwait(payments).until(last);
                        AwaitResult<Map<String, Payment>, Map.Entry<String, Payment>> map =
                                tryAwait(paymentMap).until(singleEntry);
                        AwaitResult<Payment, String> transformed =
                                tryAwait(rawPayment).until(yields(Payment::status));
                        tryAwait(text).until(isNotNull);
                        tryAwait(text).upTo(ofSeconds(1)).every(ofMillis(1)).until(isNotNull);
                    }
                }
                """));
    }

    @Test
    void rejectsAnUnrelatedOptionalResultType() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.TryAwait.tryAwait;
                import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.present;
                import io.github.gromoff97.awium.await.AwaitResult;
                import io.github.gromoff97.awium.sources.Source.OptionalSource;
                import java.util.Optional;
                final class Contract {
                    void check(OptionalSource<Integer> source) {
                        AwaitResult<Optional<Integer>, String> result = tryAwait(source).until(present);
                    }
                }
                """));
    }

    @Test
    void specializedSourcesRetainSelectedResultTypes() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.TryAwait.tryAwait;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.first;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.single;
                import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.singleEntry;
                import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.present;
                import io.github.gromoff97.awium.await.AwaitResult;
                import io.github.gromoff97.awium.sources.Source.*;
                import java.util.*;
                final class Contract {
                    void check(OptionalSource<String> optional,
                            CollectionSource<List<String>> collection,
                            MapSource<Map<String, Integer>> map) {
                        AwaitResult<Optional<String>, String> optionalResult = tryAwait(optional).until(present);
                        AwaitResult<List<String>, String> collectionResult = tryAwait(collection).until(single);
                        AwaitResult<List<String>, String> firstResult = tryAwait(collection).until(first);
                        AwaitResult<Map<String, Integer>, Map.Entry<String, Integer>> mapResult =
                                tryAwait(map).until(singleEntry);
                    }
                }
                """));
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source);
    }
}

package io.github.gromoff97.awium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void excludedDirectAndJdkSourceTypesDoNotCompile() throws IOException {
        for (String declaration : new String[] {
                "String source = \"value\";",
                "java.util.function.Supplier<String> source = () -> \"value\";"
        }) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.await.Await.await;
                    final class Contract {
                        void check() {
                            %s
                            await(source);
                        }
                    }
                    """.formatted(declaration)), declaration);
        }
    }

    @Test
    void ambiguousNullSourcesAndConditionsDoNotCompile() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                final class Contract { void check() { await(() -> null); } }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                final class Contract { void check() { await(null); } }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.sources.Source;
                final class Contract {
                    void check(Source<String> source) {
                        await(source).until(null);
                    }
                }
                """));
    }

    @Test
    void categorySpecificTerminalsRejectWrongConditions() throws IOException {
        for (String type : List.of("PresentCondition", "StructuralCondition")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.await.Await.await;
                    import io.github.gromoff97.awium.sources.Source;
                    import io.github.gromoff97.awium.conditioning.conditions.*;
                    final class Contract {
                        void check(Source<String> source, %s condition) {
                            await(source).until(condition);
                        }
                    }
                    """.formatted(type)), type);
        }
    }

    @Test
    void collectionExactFactoriesRespectOrderedSourceTyping()
            throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
                import io.github.gromoff97.awium.sources.*;
                import java.util.*;

                final class Contract {
                    static List<String> list() { return List.of("a", "b"); }

                    void check(CollectionSource<Collection<String>> collection,
                            Collection<String> expected) {
                        List<String> ordered = await(Contract::list)
                                .until(containsExactly("a", "b"));
                        await(Contract::list).until(doesNotContainExactly("b", "a")
                                .because("ordered"));
                        await(Contract::list).until(containsExactlyElementsOf(expected));
                        await(Contract::list).until(doesNotContainExactlyElementsOf(
                                expected).because("ordered"));

                        Collection<String> anyOrder = await(collection)
                                .until(containsExactlyInAnyOrder("a", "b"));
                        await(collection).until(doesNotContainExactlyInAnyOrder(
                                "a", "b").because("any order"));
                        await(collection).until(
                                containsExactlyInAnyOrderElementsOf(expected));
                        await(collection).until(
                                doesNotContainExactlyInAnyOrderElementsOf(expected)
                                        .because("any order"));

                    }
                }
                """));
    }

    @Test
    void orderedExactFactoriesRejectCollectionOnlySources() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
                import io.github.gromoff97.awium.sources.CollectionSource;
                import java.util.*;

                final class Contract {
                    void check(CollectionSource<Set<String>> source) {
                        await(source).until(containsExactly("a"));
                    }
                }
                """));
    }

    @Test
    void assertionAdaptersMayBeDecoratedOnce() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;

                final class Contract {
                    record Payment(long id) {}

                    void check() {
                        asserted((Payment value) -> {}).because("first");
                        passed((Payment value) -> value).because("first");
                    }
                }
                """));
    }

    @Test
    void explainedConditionsCannotBeDecoratedAgain() throws IOException {
        for (String condition : List.of(
                "condition(\"x\", (Object value) -> Evaluation.satisfied(value))",
                "asserted((Object value) -> {})",
                "passed((Object value) -> value)",
                "present",
                "nonEmpty")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.conditioning.conditions.StructuralCondition.*;
                    import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
                    import static io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider.*;
                    import io.github.gromoff97.awium.conditioning.Evaluation;
                    final class Contract {
                        void check() {
                            %s.because("first").because("second");
                        }
                    }
                    """.formatted(condition)), condition);
        }
    }

    @Test
    void structuralSingletonsAreFieldsNotFactories() throws IOException {
        for (String condition : List.of("empty()", "nonEmpty()")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.conditioning.conditions.StructuralCondition.*;
                    final class Contract {
                        void check() { Object condition = %s; }
                    }
                    """.formatted(condition)), condition);
        }
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source);
    }
}

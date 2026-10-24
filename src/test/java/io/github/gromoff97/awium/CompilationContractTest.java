package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void conditionsAndResultsRejectUnrelatedTypes() throws IOException {
        for (String expression : List.of(
                "Integer wrong = await(strings).until(equalTo(42));",
                "String wrong = await(objects).until(instanceOf(Integer.class));",
                "AwaitResult<Optional<Integer>, String> wrong = await(optional).tryUntil(present);",
                "await(characters).until(equalTo(42));",
                "await(characters).until(equalTo(42).because(\"incompatible\"));",
                "await(characters).tryUntil(equalTo(42).because(\"%s\", \"incompatible\"));")) {
            assertFalse(compiles("""
                    import java.util.concurrent.Callable;
                    import io.github.gromoff97.awium.*;
                    import io.github.gromoff97.awium.Await.OptionalSource;
                    import java.util.Optional;
                    import static io.github.gromoff97.awium.Await.await;
                    import static io.github.gromoff97.awium.Conditions.*;
                    import static io.github.gromoff97.awium.OptionalConditions.present;
                    final class Contract {
                        void check(Callable<String> strings, Callable<Object> objects,
                                Callable<CharSequence> characters, OptionalSource<Integer> optional) {
                            %s
                        }
                    }
                    """.formatted(expression)), expression);
        }
    }

    @Test
    void unrelatedFixtureFailuresCannotMasqueradeAsTypeRejections() {
        assertThrows(AssertionError.class,
                () -> compiles("final class Contract { void broken( }"));
        assertThrows(AssertionError.class, () -> compiles("""
                import missing.Type;
                final class Contract {}
                """));
        assertThrows(AssertionError.class, () -> compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import java.util.concurrent.Callable;

                final class Contract {
                    void check(Callable<String> source) {
                        await(source).utnil();
                    }
                }
                """));
    }

    @Test
    void excludedDirectAndJdkSourceTypesDoNotCompile() throws IOException {
        for (String declaration : new String[] {
                "String source = \"value\";",
                "java.util.function.Supplier<String> source = () -> \"value\";"
        }) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.Await.await;
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
    void ambiguousNullSourcesDoNotCompile() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                final class Contract { void check() { await(() -> null); } }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                final class Contract { void check() { await(null); } }
                """));
    }

    @Test
    void categorySpecificTerminalsRejectWrongConditions() throws IOException {
        for (String type : List.of(
                "SelectedCondition<java.util.Optional<?>>",
                "SelectedCondition<java.util.Collection<?>>",
                "SelectedCondition<java.util.Map<?, ?>>")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.Await.await;
                    import java.util.concurrent.Callable;
                    import io.github.gromoff97.awium.*;
                    import io.github.gromoff97.awium.Condition.SelectedCondition;
                    final class Contract {
                        void check(Callable<String> source, %s condition) {
                            await(source).until(condition);
                        }
                    }
                    """.formatted(type)), type);
        }
    }

    @Test
    void plainSourcesRejectSelectionsInsteadOfReturningObjects() throws IOException {
        for (String condition : List.of("present", "single", "first", "last", "singleEntry")) {
            String source = condition.equals("present") ? "optional" : condition.equals("singleEntry") ? "map" : "collection";
            String contract = """
                    import static io.github.gromoff97.awium.Await.await;
                    import static io.github.gromoff97.awium.CollectionConditions.*;
                    import static io.github.gromoff97.awium.MapConditions.singleEntry;
                    import static io.github.gromoff97.awium.OptionalConditions.present;
                    import java.util.concurrent.Callable;
                    import java.util.*;
                    final class Contract {
                        void check(Callable<Optional<String>> optional,
                                Callable<List<String>> collection,
                                Callable<Map<String, String>> map) {
                            %s result = await(%s).until(%s);
                        }
                    }
                    """;
            assertFalse(compiles(contract.formatted("Object", source, condition)), condition);
            assertFalse(compiles(contract.formatted("Object", source, condition + ".because(\"required\")")), condition);
        }
    }

    @Test
    void collectionAndMapConditionsCannotBeMixed() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.MapConditions;
                import io.github.gromoff97.awium.Await.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        await(source).until(MapConditions.nonEmpty);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.CollectionConditions;
                import io.github.gromoff97.awium.Await.MapSource;
                import java.util.Map;
                final class Contract {
                    void check(MapSource<Map<String, String>> source) {
                        await(source).until(CollectionConditions.nonEmpty);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.MapConditions;
                import io.github.gromoff97.awium.Await.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        await(source).until(MapConditions.singleEntry);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.CollectionConditions;
                import io.github.gromoff97.awium.Await.MapSource;
                import java.util.Map;
                final class Contract {
                    void check(MapSource<Map<String, String>> source) {
                        await(source).until(CollectionConditions.single);
                    }
                }
                """));
    }

    @Test
    void collectionSelectionRejectsAnUnrelatedElementType() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import java.util.List;
                final class Contract {
                    void check() {
                        Integer wrong = await((io.github.gromoff97.awium.Await.CollectionSource<List<String>>)
                                () -> List.of("value")).until(single);
                    }
                }
                """));
    }

    @Test
    void orderedSelectionRejectsUnrelatedResultsAndUnorderedSources() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.first;
                import io.github.gromoff97.awium.Await.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        Integer wrong = await(source).until(first);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.first;
                import io.github.gromoff97.awium.Await.CollectionSource;
                import java.util.HashSet;
                final class Contract {
                    void check(CollectionSource<HashSet<String>> source) {
                        await(source).until(first);
                    }
                }
                """));
    }

    @Test
    void mapMembershipConditionsRejectIncompatibleKeysAndValues() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String condition : List.of("containsKeys(1)", "containsValues(\"wrong\")",
                    "containsEntries(1, 1)", "containsEntries(\"x\", \"wrong\")",
                    "containsEntries(Map.of(1, 1))", "containsEntries(Map.of(\"x\", \"wrong\"))")) {
                assertFalse(compiles("""
                        import static io.github.gromoff97.awium.Await.await;
                        import static io.github.gromoff97.awium.MapConditions.*;
                        import io.github.gromoff97.awium.Await.MapSource;
                        import java.util.Map;

                        final class Contract {
                            void check(MapSource<Map<String, Integer>> source) {
                                await(source).%s(%s);
                            }
                        }
                        """.formatted(terminal, condition)), terminal + ": " + condition);
            }
        }
    }

    @Test
    void lazyConditionsRejectAnIncompatibleObservedType() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.Await.await;
                    import static io.github.gromoff97.awium.Conditions.*;
                    import io.github.gromoff97.awium.CheckedFunction;
                    import io.github.gromoff97.awium.ConditionResult;
                    import java.util.concurrent.Callable;

                    final class Contract {
                        static CheckedFunction<String, ConditionResult<String>> evaluator() {
                            return ConditionResult::satisfied;
                        }
                        void check(Callable<Integer> source) {
                            await(source).%s(condition("typed", Contract::evaluator));
                        }
                    }
                    """.formatted(terminal)), terminal);
        }
    }

    @Test
    void orderedExactFactoriesRejectCollectionOnlySources() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import io.github.gromoff97.awium.Await.CollectionSource;
                import java.util.*;

                final class Contract {
                    void check(CollectionSource<Set<String>> source) {
                        await(source).until(containsExactly("a"));
                    }
                }
                """));
    }

    @Test
    void constantConditionsAreFieldsNotFactories() throws IOException {
        for (String condition : List.of("CollectionConditions.empty()",
                "CollectionConditions.nonEmpty()", "MapConditions.empty()",
                "MapConditions.nonEmpty()",
                "CollectionConditions.single()",
                "CollectionConditions.first()", "CollectionConditions.last()",
                "MapConditions.singleEntry()")) {
            String method = condition.substring(condition.lastIndexOf('.') + 1,
                    condition.indexOf('('));
            assertFalse(compiles("""
                    import io.github.gromoff97.awium.CollectionConditions;
                    import io.github.gromoff97.awium.MapConditions;
                    final class Contract {
                        void check() { Object condition = %s; }
                    }
                    """.formatted(condition), method), condition);
        }
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source);
    }

    private boolean compiles(String source, String expectedMissingMethod)
            throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source,
                expectedMissingMethod);
    }
}

package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
            assertRejected("""
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
                    """.formatted(expression));
        }
    }

    @Test
    void singleAndBulkMembershipRejectUnrelatedElementTypes() throws IOException {
        for (String condition : List.of("contains(42)", "containsAll(List.of(42))",
                "doesNotContain(42)", "doesNotContainAny(List.of(42))",
                "doesNotContainAll(List.of(42))", "containsAny(List.of(42))",
                "containsExactly(List.of(42))", "doesNotContainExactly(List.of(42))",
                "containsExactlyInAnyOrder(List.of(42))", "doesNotContainExactlyInAnyOrder(List.of(42))",
                "containsOnly(List.of(42))")) {
            for (String explanation : List.of("", ".because(\"incompatible\")", ".because(\"%s\", \"incompatible\")")) {
                for (String terminal : List.of("until", "tryUntil")) {
                    String expression = "await(source)." + terminal + "(CollectionConditions."
                            + condition + explanation + ");";
                    assertRejected("""
                            import java.util.List;
                            import java.util.concurrent.Callable;
                            import io.github.gromoff97.awium.CollectionConditions;
                            import static io.github.gromoff97.awium.Await.await;
                            final class Contract {
                                void check(Callable<List<String>> source) {
                                    %s
                                }
                            }
                            """.formatted(expression));
                }
            }
        }
    }

    @Test
    void negativeFixturesRejectValidCodeAndUnrelatedErrors() {
        assertThrows(AssertionError.class,
                () -> assertRejected("final class Contract {}"));
        assertThrows(AssertionError.class,
                () -> assertRejected("final class Contract { void broken( }"));
        assertThrows(AssertionError.class, () -> assertRejected("""
                import missing.Type;
                final class Contract {}
                """));
        assertThrows(AssertionError.class, () -> assertRejected("""
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
            assertRejected("""
                    import static io.github.gromoff97.awium.Await.await;
                    final class Contract {
                        void check() {
                            %s
                            await(source);
                        }
                    }
                    """.formatted(declaration));
        }
    }

    @Test
    void ambiguousNullSourcesDoNotCompile() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                final class Contract { void check() { await(() -> null); } }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                final class Contract { void check() { await(null); } }
                """);
    }

    @Test
    void categorySpecificTerminalsRejectWrongConditions() throws IOException {
        for (String type : List.of(
                "SelectedCondition<java.util.Optional<?>>",
                "SelectedCondition<java.util.Collection<?>>",
                "SelectedCondition<java.util.Map<?, ?>>")) {
            assertRejected("""
                    import static io.github.gromoff97.awium.Await.await;
                    import java.util.concurrent.Callable;
                    import io.github.gromoff97.awium.*;
                    import io.github.gromoff97.awium.Condition.SelectedCondition;
                    final class Contract {
                        void check(Callable<String> source, %s condition) {
                            await(source).until(condition);
                        }
                    }
                    """.formatted(type));
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
            assertRejected(contract.formatted("Object", source, condition));
            assertRejected(contract.formatted("Object", source, condition + ".because(\"required\")"));
        }
    }

    @Test
    void collectionAndMapConditionsCannotBeMixed() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.MapConditions;
                import io.github.gromoff97.awium.Await.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        await(source).until(MapConditions.nonEmpty);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.CollectionConditions;
                import io.github.gromoff97.awium.Await.MapSource;
                import java.util.Map;
                final class Contract {
                    void check(MapSource<Map<String, String>> source) {
                        await(source).until(CollectionConditions.nonEmpty);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.MapConditions;
                import io.github.gromoff97.awium.Await.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        await(source).until(MapConditions.singleEntry);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.CollectionConditions;
                import io.github.gromoff97.awium.Await.MapSource;
                import java.util.Map;
                final class Contract {
                    void check(MapSource<Map<String, String>> source) {
                        await(source).until(CollectionConditions.single);
                    }
                }
                """);
    }

    @Test
    void collectionSelectionRejectsAnUnrelatedElementType() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import java.util.List;
                final class Contract {
                    void check() {
                        Integer wrong = await((io.github.gromoff97.awium.Await.CollectionSource<List<String>>)
                                () -> List.of("value")).until(single);
                    }
                }
                """);
    }

    @Test
    void orderedSelectionRejectsUnrelatedResultsAndUnorderedSources() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.first;
                import io.github.gromoff97.awium.Await.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        Integer wrong = await(source).until(first);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.first;
                import io.github.gromoff97.awium.Await.CollectionSource;
                import java.util.HashSet;
                final class Contract {
                    void check(CollectionSource<HashSet<String>> source) {
                        await(source).until(first);
                    }
                }
                """);
    }

    @Test
    void mapMembershipConditionsRejectIncompatibleKeysAndValues() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String explanation : List.of("", ".because(\"incompatible\")")) {
                for (String condition : List.of("containsKey(1)", "containsValue(\"wrong\")",
                        "containsKeys(List.of(1))", "containsValues(List.of(\"wrong\"))",
                        "containsOnlyKeys(List.of(1))",
                        "doesNotContainKey(1)", "doesNotContainValue(\"wrong\")",
                        "doesNotContainKeys(List.of(1))", "doesNotContainValues(List.of(\"wrong\"))",
                        "containsEntries(1, 1)", "containsEntries(\"x\", \"wrong\")",
                        "containsEntries(Map.of(1, 1))", "containsEntries(Map.of(\"x\", \"wrong\"))")) {
                    assertRejected("""
                            import static io.github.gromoff97.awium.Await.await;
                            import static io.github.gromoff97.awium.MapConditions.*;
                            import io.github.gromoff97.awium.Await.MapSource;
                            import java.util.Map;
                            import java.util.List;

                            final class Contract {
                                void check(MapSource<Map<String, Integer>> source) {
                                    await(source).%s(%s%s);
                                }
                            }
                            """.formatted(terminal, condition, explanation));
                }
            }
        }
    }

    @Test
    void lazyConditionsRejectAnIncompatibleObservedType() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            assertRejected("""
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
                    """.formatted(terminal));
        }
    }

    @Test
    void orderedExactFactoriesRejectCollectionOnlySources() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import io.github.gromoff97.awium.Await.CollectionSource;
                import java.util.*;

                final class Contract {
                    void check(CollectionSource<Set<String>> source) {
                        await(source).until(containsExactly(List.of("a")));
                    }
                }
                """);
    }

    @Test
    void covariantEntryViewsKeepIndependentValueTypesAcrossStages() throws IOException {
        for (String decoration : List.of("", ".because(\"independent entries\")")) {
            assertRejected("""
                    import java.util.*;
                    import io.github.gromoff97.awium.Await.MapViewSource;
                    import static io.github.gromoff97.awium.Await.await;
                    import static io.github.gromoff97.awium.MapConditions.singleEntry;
                    final class Contract {
                        static <KEY, VALUE> void overwrite(List<Map.Entry<KEY, VALUE>> entries) {
                            entries.getFirst().setValue(entries.getLast().getValue());
                        }
                        void check(MapViewSource<Number, CharSequence,
                                Map<? extends Number, ? extends CharSequence>> source) {
                            overwrite(await(source).until(singleEntry%s, singleEntry%s));
                        }
                    }
                    """.formatted(decoration, decoration));
        }
    }

    private void assertRejected(String source) throws IOException {
        CompilationSupport.assertRejected(temporaryDirectory, source);
    }

}

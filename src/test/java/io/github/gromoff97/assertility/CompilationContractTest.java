package io.github.gromoff97.assertility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompilationContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void typedSourcesAcceptTheirIntendedLambdaShapes() throws Exception {
        var result = CompilationSupport.compile(temporaryDirectory, "SourceShapes", """
                import io.github.gromoff97.assertility.AwaitSources;

                final class SourceShapes {
                    static void assignments() {
                        AwaitSources.StringSource string = () -> "ready";
                        AwaitSources.ComparableSource<Integer> number = () -> 42;
                        AwaitSources.OptionalSource<String> optional =
                                () -> java.util.Optional.of("x");
                        AwaitSources.CollectionSource<String, java.util.Set<String>> set =
                                java.util.Set::of;
                        AwaitSources.SequencedCollectionSource<String, java.util.List<String>> list =
                                java.util.List::of;
                        AwaitSources.MapSource<String, Integer, java.util.Map<String, Integer>> map =
                                java.util.Map::of;
                        AwaitSources.FutureSource<java.util.concurrent.CompletableFuture<String>> future =
                                () -> java.util.concurrent.CompletableFuture.completedFuture("x");
                        AwaitSources.Executable executable = () -> { };
                    }
                }
                """);

        assertThat(result.exitCode())
                .as(result.diagnostics())
                .isZero();
    }

    @Test
    void orderedContentRequiresASequencedCollectionSource() throws Exception {
        var positive = CompilationSupport.compile(
                temporaryDirectory.resolve("ordered-positive"), "OrderedCollections", """
                        import io.github.gromoff97.assertility.AwaitSources;
                        import java.util.ArrayDeque;
                        import java.util.List;

                        import static io.github.gromoff97.assertility.Assertility.awaitUntil;

                        final class OrderedCollections {
                            static void assertions() {
                                AwaitSources.SequencedCollectionSource<String, List<String>> list =
                                        () -> List.of("a", "b");
                                AwaitSources.SequencedCollectionSource<String, ArrayDeque<String>> deque =
                                        () -> new ArrayDeque<>(List.of("a", "b"));
                                awaitUntil(list).containsExactly("a", "b");
                                awaitUntil(deque).containsExactlyElementsOf(List.of("a", "b"));
                            }
                        }
                        """);
        var negative = CompilationSupport.compile(
                temporaryDirectory.resolve("ordered-negative"), "GeneralCollection", """
                        import io.github.gromoff97.assertility.AwaitSources;
                        import java.util.Collection;
                        import java.util.List;

                        import static io.github.gromoff97.assertility.Assertility.awaitUntil;

                        final class GeneralCollection {
                            static void assertion() {
                                AwaitSources.CollectionSource<String, Collection<String>> source =
                                        () -> List.of("a", "b");
                                awaitUntil(source).containsExactly("a", "b");
                            }
                        }
                        """);

        assertThat(positive.exitCode())
                .as(positive.diagnostics())
                .isZero();
        assertThat(negative.exitCode()).isNotZero();
        assertThat(negative.diagnostics()).contains("containsExactly");
    }

    @Test
    void everyResultChannelAndSourceFamilyCompilesWithVar() throws Exception {
        var result = CompilationSupport.compile(
                temporaryDirectory.resolve("complete-grammar"), "CompleteGrammar", """
                        import io.github.gromoff97.assertility.AwaitSources;
                        import java.util.ArrayDeque;
                        import java.util.ArrayList;
                        import java.util.List;
                        import java.util.Map;
                        import java.util.Optional;
                        import java.util.Set;
                        import java.util.concurrent.Callable;
                        import java.util.concurrent.CompletableFuture;
                        import org.awaitility.core.ConditionFactory;

                        import static io.github.gromoff97.assertility.Assertility.*;

                        final class CompleteGrammar {
                            static void compile(ConditionFactory factory) {
                                AwaitSources.Source<Object> objectSource = Object::new;
                                AwaitSources.OptionalSource<String> optionalSource =
                                        () -> Optional.empty();
                                Callable<String> callable = () -> "ready";

                                var object = awaitUntil(objectSource).isNotNull();
                                var tryObject = tryAwaitUntil(objectSource).isNotNull();
                                var bool = awaitUntil(() -> true).isTrue();
                                var tryBool = tryAwaitUntil(() -> false).isFalse();
                                var comparable = awaitUntil(() -> 42).isGreaterThan(1);
                                var tryComparable = tryAwaitUntil(() -> 42).isLessThan(100);
                                var string = awaitUntil(callable::call).isNotEmpty();
                                var tryString = tryAwaitUntil(() -> "ready").contains("read");
                                var optionalState = awaitUntil(optionalSource).isEmpty();
                                var optionalValue = awaitUntil(() -> Optional.of("ready")).isPresent();
                                var tryOptionalState = tryAwaitUntil(optionalSource).isEmpty();
                                var tryOptionalValue = tryAwaitUntil(() -> Optional.of("ready"))
                                        .contains("ready");
                                var listState = awaitUntil(() -> new ArrayList<>(List.of("a", "b")))
                                        .hasSize(2);
                                var setState = awaitUntil(() -> Set.of("a", "b")).isNotEmpty();
                                var selected = awaitUntil(() -> List.of("a")).single();
                                var selectedMany = awaitUntil(() -> List.of("a", "b"))
                                        .exactly(2, value -> true);
                                var tryCollection = tryAwaitUntil(() -> Set.of("a")).isNotEmpty();
                                var trySelected = tryAwaitUntil(() -> List.of("a")).single();
                                var trySelectedMany = tryAwaitUntil(() -> List.of("a", "b"))
                                        .exactly(2, value -> true);
                                var orderedList = awaitUntil(() -> List.of("a", "b"))
                                        .containsExactly("a", "b");
                                var orderedDeque = awaitUntil(() ->
                                                new ArrayDeque<>(List.of("a", "b")))
                                        .containsExactlyElementsOf(List.of("a", "b"));
                                var tryOrdered = tryAwaitUntil(() -> List.of("a", "b"))
                                        .containsExactly("a", "b");
                                var map = awaitUntil(() -> Map.of("a", 1)).containsKey("a");
                                var tryMap = tryAwaitUntil(() -> Map.of("a", 1))
                                        .containsEntry("a", 1);
                                var future = awaitUntil(() ->
                                                CompletableFuture.completedFuture("ready"))
                                        .isDone();
                                var tryFuture = tryAwaitUntil(() ->
                                                CompletableFuture.completedFuture("ready"))
                                        .isDone();
                                awaitUntil(CompleteGrammar::execute).doesNotThrowAnyException();
                                var tryExecutable = tryAwaitUntil(CompleteGrammar::execute)
                                        .doesNotThrowAnyException();

                                var explicit = await(factory).until(() -> "ready").isNotEmpty();
                                var tryExplicit = tryAwait(factory).until(() -> "ready").isNotEmpty();
                            }

                            static void execute() {
                            }
                        }
                        """);

        assertThat(result.exitCode()).as(result.diagnostics()).isZero();
    }

    @Test
    void awaitilityAndAssertilityAwaitStaticImportsCoexist() throws Exception {
        var result = CompilationSupport.compile(
                temporaryDirectory.resolve("await-imports"), "AwaitImports", """
                        import java.time.Duration;
                        import org.awaitility.core.ConditionFactory;

                        import static io.github.gromoff97.assertility.Assertility.await;
                        import static org.awaitility.Awaitility.await;

                        final class AwaitImports {
                            static void compile() {
                                ConditionFactory factory = await()
                                        .atMost(Duration.ofMillis(100));
                                var value = await(factory).until(() -> "ready").isNotEmpty();
                            }
                        }
                        """);

        assertThat(result.exitCode()).as(result.diagnostics()).isZero();
    }

    @Test
    void restrictedGrammarDoesNotCompile() throws Exception {
        var result = CompilationSupport.compile(
                temporaryDirectory.resolve("restricted-grammar"), "RestrictedGrammar", """
                        import io.github.gromoff97.assertility.AwaitSources;
                        import java.util.Collection;
                        import java.util.List;
                        import org.awaitility.core.ConditionFactory;

                        import static io.github.gromoff97.assertility.Assertility.*;

                        final class RestrictedGrammar {
                            static void tryDirect() {
                                tryAwaitUntil(() -> "ready").as("forbidden");
                            }

                            static void tryFactory(ConditionFactory factory) {
                                tryAwait(factory).until(() -> "ready").as("forbidden");
                            }

                            static void secondAs() {
                                awaitUntil(() -> "ready").as("one").as("two");
                            }

                            static void terminalBeforeSource(ConditionFactory factory) {
                                await(factory).isNotNull();
                            }

                            static void orderedOnGeneralCollection() {
                                AwaitSources.CollectionSource<String, Collection<String>> source =
                                        () -> List.of("a", "b");
                                awaitUntil(source).containsExactly("a", "b");
                            }
                        }
                        """);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.diagnostics())
                .contains("as")
                .contains("isNotNull")
                .contains("containsExactly");
    }

    @Test
    void forbiddenConvenienceMethodsDoNotCompile() throws Exception {
        var result = CompilationSupport.compile(
                temporaryDirectory.resolve("forbidden-methods"), "ForbiddenMethods", """
                        import java.time.Duration;
                        import java.util.List;
                        import org.awaitility.core.ConditionFactory;

                        import static io.github.gromoff97.assertility.Assertility.*;

                        final class ForbiddenMethods {
                            static void compile(ConditionFactory factory) {
                                var facade = awaitUntil(() -> List.of("a", "b"));
                                facade.first();
                                facade.last();
                                facade.element(0);
                                facade.returning(value -> value);
                                facade.usingFactory(factory);
                                facade.withConditionFactory(factory);
                                facade.timeout(Duration.ofSeconds(1));
                                facade.pollInterval(Duration.ofMillis(10));
                                facade.during(Duration.ofMillis(20));
                                facade.ignoring(RuntimeException.class);
                            }
                        }
                        """);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.diagnostics())
                .contains("first")
                .contains("last")
                .contains("element")
                .contains("returning")
                .contains("usingFactory")
                .contains("withConditionFactory")
                .contains("timeout")
                .contains("pollInterval")
                .contains("during")
                .contains("ignoring");
    }

    @Test
    void valueReturningLambdaDoesNotSilentlyBecomeExecutable() throws Exception {
        var result = CompilationSupport.compile(
                temporaryDirectory.resolve("value-executable"), "ValueExecutable", """
                        import static io.github.gromoff97.assertility.Assertility.awaitUntil;

                        final class ValueExecutable {
                            static void compile() {
                                awaitUntil(() -> "value").doesNotThrowAnyException();
                            }
                        }
                        """);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.diagnostics()).contains("doesNotThrowAnyException");
    }
}

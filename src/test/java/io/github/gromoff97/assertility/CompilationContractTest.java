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
}

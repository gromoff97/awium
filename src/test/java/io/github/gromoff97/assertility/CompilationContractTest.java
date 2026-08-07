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
}

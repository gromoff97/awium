package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.condition.ConditionRuntime;

import static io.github.gromoff97.awium.CompilationSupport.compiles;
import static io.github.gromoff97.awium.CompilationSupport.compilesModule;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.isRegularFile;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.spi.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactContractIT {

    static final Path JAR = Path.of(requireNonNull(System.getProperty("awium.test.jar"),
            "awium.test.jar must be configured"));

    @Test
    void currentBuildJarIsAnExplicitJavaBaseOnlyModule()
            throws Exception {
        assertTrue(isRegularFile(JAR), JAR.toString());

        ModuleReference module = ModuleFinder.of(JAR)
                .find("io.github.gromoff97.awium").orElseThrow();
        assertFalse(module.descriptor().isAutomatic());
        assertFalse(module.descriptor().exports().isEmpty());
        assertTrue(module.descriptor().exports().stream()
                .noneMatch(export -> export.source().startsWith("io.github.gromoff97.awium.internal.")));
        assertEquals(Set.of("java.base"),
                Set.copyOf(module.descriptor().requires().stream()
                        .map(require -> require.name()).toList()));
    }

    @Test
    void executionDoesNotDependOnFluentCatalogOrFormattingCode() {
        var output = new ByteArrayOutputStream();
        var writer = new PrintWriter(output, true, UTF_8);
        int exit = ToolProvider.findFirst("jdeps").orElseThrow().run(writer, writer,
                "--multi-release", "21", "-verbose:package", JAR.toString());
        String dependencies = output.toString(UTF_8);

        assertEquals(0, exit, dependencies);
        Map<String, Set<String>> packages = packageDependencies(dependencies);
        assertFalse(packages.isEmpty(), dependencies);
        // The sealed condition API and its implementation form one unit with intentional mutual references.
        for (String execution : Set.of("condition", "internal.condition", "internal.engine")) {
            Set<String> used = packages.get("io.github.gromoff97.awium." + execution);
            assertFalse(used == null || used.isEmpty(), dependencies);
            assertTrue(used.stream().noneMatch(Set.of(
                    "io.github.gromoff97.awium.await",
                    "io.github.gromoff97.awium.conditions",
                    "io.github.gromoff97.awium.internal.diagnostics")::contains), dependencies);
        }
    }

    @Test
    void packagedJarCompilesDirectMethodReferences(@TempDir Path directory)
            throws Exception {
        assertTrue(compiles(directory, """
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditions.CollectionConditions.*;
                import static io.github.gromoff97.awium.conditions.Conditions.*;
                import static io.github.gromoff97.awium.conditions.MapConditions.*;
                import static io.github.gromoff97.awium.conditions.OptionalConditions.*;
                import static io.github.gromoff97.awium.conditions.StringConditions.*;

                import io.github.gromoff97.awium.conditions.CollectionConditions;
                import io.github.gromoff97.awium.conditions.MapConditions;

                import java.util.List;
                import java.util.Map;
                import java.util.Optional;

                final class Contract {
                    String object() {
                        return await(this::loadObject).until(equalTo("value"));
                    }
                    String optional() {
                        return await(this::loadOptional).until(present);
                    }
                    List<String> collection() {
                        return await(this::loadCollection).until(CollectionConditions.nonEmpty);
                    }
                    String single() {
                        return await(this::loadCollection).until(single);
                    }
                    Map<String, Integer> map() {
                        return await(this::loadMap).until(MapConditions.nonEmpty);
                    }
                    Map.Entry<String, Integer> singleEntry() {
                        return await(this::loadMap).until(singleEntry);
                    }
                    String loadObject() { return "value"; }
                    Optional<String> loadOptional() {
                        return Optional.of("value");
                    }
                    List<String> loadCollection() { return List.of("value"); }
                    Map<String, Integer> loadMap() { return Map.of("value", 1); }
                }
                """, JAR));
    }

    @Test
    void packagedJarCompilesAsAnExplicitModule(@TempDir Path directory) throws Exception {
        assertTrue(compilesModule(directory, """
                module consumer {
                    requires io.github.gromoff97.awium;
                }
                """, """
                package consumer;

                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditions.Conditions.*;
                import static io.github.gromoff97.awium.conditions.OptionalConditions.present;
                import static io.github.gromoff97.awium.conditions.CollectionConditions.single;
                import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
                import static java.time.Duration.ofNanos;
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;
                import java.io.IOException;
                import io.github.gromoff97.awium.results.AwaitResult;

                final class Contract {
                    String value() {
                        return await(() -> "ready").usingTime(() -> 0L, nanos -> {}).until(isNotNull);
                    }
                    String optional() {
                        return await(() -> Optional.of("ready")).upTo(ofNanos(5))
                                .usingTime(() -> 0L, nanos -> {}).every(ofNanos(1)).until(present);
                    }
                    String collection() {
                        return await(() -> List.of("ready")).usingTime(() -> 0L, nanos -> {}).until(single);
                    }
                    Map.Entry<String, Integer> map() {
                        return await(() -> Map.of("ready", 1)).usingTime(() -> 0L, nanos -> {}).until(singleEntry);
                    }
                    AwaitResult<Optional<String>, String> attempted() {
                        return await(() -> Optional.of("ready")).usingTime(() -> 0L, nanos -> {}).tryUntil(present);
                    }
                    String checkedAssertion() {
                        return await(() -> "ready").until(asserted(this::verify).because("checked verification"));
                    }
                    int checkedSelection() {
                        return await(() -> "ready").until(yields(this::length).because("checked selection"));
                    }
                    int composition() {
                        return await(() -> List.of("ready")).until(single(yields(this::length).because("checked length")));
                    }
                    AwaitResult<Optional<String>, String> diagnosticComposition() {
                        return await(() -> Optional.of("ready")).tryUntil(io.github.gromoff97.awium.conditions.OptionalConditions.hasValue(
                                equalTo("ready").because("required value")));
                    }
                    AwaitResult<Optional<String>, List<String>> ordered() {
                        return await(() -> Optional.of("ready")).tryUntil(present.because("first"), present.because("second"));
                    }
                    List<Integer> orderedLengths() {
                        return await(() -> List.of("ready")).until(single(yields(this::length)), single(yields(this::length)));
                    }
                    void verify(String value) throws IOException {}
                    int length(String value) throws IOException { return value.length(); }
                }
                """, JAR));
    }

    @Test
    void conditionRuntimeIsNotAccessibleFromAnotherModule(@TempDir Path directory) throws Exception {
        assertFalse(compilesModule(directory, """
                module consumer {
                    requires io.github.gromoff97.awium;
                }
                """, """
                package consumer;
                import %s;
                final class Contract {
                    ConditionRuntime runtime;
                }
                """.formatted(ConditionRuntime.class.getName()), JAR));
    }

    private static Map<String, Set<String>> packageDependencies(String output) {
        var dependencies = new HashMap<String, Set<String>>();
        output.lines().map(String::strip).map(line -> line.split("\\s+"))
                .filter(parts -> parts.length >= 3 && parts[1].equals("->")
                        && parts[0].startsWith("io.github.gromoff97.awium.")
                        && parts[2].startsWith("io.github.gromoff97.awium.")
                        && !parts[0].equals(parts[2]))
                .forEach(parts -> dependencies.computeIfAbsent(parts[0], ignored -> new HashSet<>()).add(parts[2]));
        return dependencies;
    }
}

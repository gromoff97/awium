package io.github.gromoff97.awium;

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
        assertEquals(Set.of(
                        "io.github.gromoff97.awium.condition",
                        "io.github.gromoff97.awium.exceptions",
                        "io.github.gromoff97.awium.results",
                        "io.github.gromoff97.awium.sources"),
                Set.copyOf(module.descriptor().exports().stream()
                        .map(export -> export.source()).toList()));
        assertEquals(Set.of("java.base"),
                Set.copyOf(module.descriptor().requires().stream()
                        .map(require -> require.name()).toList()));
    }

    @Test
    void packagedJarHasNoPackageDependencyCycles() {
        var output = new ByteArrayOutputStream();
        var writer = new PrintWriter(output, true, UTF_8);
        int exit = ToolProvider.findFirst("jdeps").orElseThrow().run(writer, writer,
                "--multi-release", "21", "-verbose:package", JAR.toString());
        String dependencies = output.toString(UTF_8);

        assertEquals(0, exit, dependencies);
        Map<String, Set<String>> packages = packageDependencies(dependencies);
        assertFalse(packages.isEmpty(), dependencies);
        assertTrue(isAcyclic(packages), dependencies);
    }

    @Test
    void packagedJarCompilesDirectMethodReferences(@TempDir Path directory)
            throws Exception {
        assertTrue(compiles(directory, """
                import static io.github.gromoff97.awium.condition.Await.await;
                import static io.github.gromoff97.awium.condition.CollectionConditions.*;
                import static io.github.gromoff97.awium.condition.Conditions.*;
                import static io.github.gromoff97.awium.condition.MapConditions.*;
                import static io.github.gromoff97.awium.condition.OptionalConditions.*;
                import static io.github.gromoff97.awium.condition.StringConditions.*;

                import io.github.gromoff97.awium.condition.CollectionConditions;
                import io.github.gromoff97.awium.condition.MapConditions;

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

                import static io.github.gromoff97.awium.condition.Await.await;
                import static io.github.gromoff97.awium.condition.Conditions.isNotNull;

                final class Contract {
                    String value() {
                        return await(() -> "ready").until(isNotNull);
                    }
                }
                """, JAR));
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

    private static boolean isAcyclic(Map<String, Set<String>> dependencies) {
        var remaining = new HashMap<>(dependencies);
        while (!remaining.isEmpty()) {
            Set<String> leaves = remaining.entrySet().stream()
                    .filter(entry -> entry.getValue().stream().noneMatch(remaining::containsKey))
                    .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
            if (leaves.isEmpty()) {
                return false;
            }
            leaves.forEach(remaining::remove);
        }
        return true;
    }
}

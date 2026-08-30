package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.CompilationSupport.compiles;
import static io.github.gromoff97.awium.CompilationSupport.compilesModule;
import static java.nio.file.Files.isRegularFile;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.Set;
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
        assertEquals(Set.of("java.base"),
                Set.copyOf(module.descriptor().requires().stream()
                        .map(require -> require.name()).toList()));
    }

    @Test
    void packagedJarCompilesDirectMethodReferences(@TempDir Path directory)
            throws Exception {
        assertTrue(compiles(directory, """
                import static io.github.gromoff97.awium.fluent.Await.await;
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

                import static io.github.gromoff97.awium.fluent.Await.await;
                import static io.github.gromoff97.awium.conditions.Conditions.isNotNull;

                final class Contract {
                    String value() {
                        return await(() -> "ready").until(isNotNull);
                    }
                }
                """, JAR));
    }
}

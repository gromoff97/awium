package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.CompilationSupport.compiles;
import static java.nio.file.Files.isRegularFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactContractIT {

    private static final Path JAR = Path.of(
            "build", "libs", "awium-0.1.0-SNAPSHOT.jar");
    @Test
    void currentBuildJarIsAnExplicitModuleWithOnlySupportedExports()
            throws Exception {
        assertTrue(isRegularFile(JAR), JAR.toString());

        ModuleReference module = ModuleFinder.of(JAR)
                .find("io.github.gromoff97.awium").orElseThrow();
        assertEquals(Set.of("io.github.gromoff97.awium",
                        "io.github.gromoff97.awium.await",
                        "io.github.gromoff97.awium.sources",
                        "io.github.gromoff97.awium.conditioning",
                        "io.github.gromoff97.awium.conditioning.conditions",
                        "io.github.gromoff97.awium.conditioning.providers",
                        "io.github.gromoff97.awium.exceptions"),
                Set.copyOf(module.descriptor().exports().stream()
                        .map(export -> export.source()).toList()));
    }

    @Test
    void packagedJarCompilesDirectMethodReferences(@TempDir Path directory)
            throws Exception {
        assertTrue(compiles(directory, """
                import static io.github.gromoff97.awium.Awium.await;
                import static io.github.gromoff97.awium.conditioning.conditions.StructuralCondition.*;
                import static io.github.gromoff97.awium.conditioning.providers.ObjectConditionProvider.*;
                import static io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider.*;

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
                        return await(this::loadCollection).until(nonEmpty);
                    }
                    Map<String, Integer> map() {
                        return await(this::loadMap).until(nonEmpty);
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
}

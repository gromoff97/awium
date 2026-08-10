package io.github.gromoff97.awium;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

class ArtifactContractIT {

    private static final Path JAR = Path.of(
            "build", "libs", "awium-0.1.0-SNAPSHOT.jar");
    private static final Path MAIN_CLASS = Path.of("build", "classes", "java",
            "main", "io", "github", "gromoff97", "awium", "Awium.class");

    @Test
    void currentBuildJarIsAnExplicitModuleWithOnlySupportedExports()
            throws Exception {
        assertTrue(Files.isRegularFile(JAR), JAR.toString());
        assertTrue(Files.isRegularFile(MAIN_CLASS), MAIN_CLASS.toString());

        try (JarFile jar = new JarFile(JAR.toFile())) {
            Manifest manifest = jar.getManifest();
            assertNotNull(manifest);
            assertNull(manifest.getMainAttributes()
                    .getValue("Automatic-Module-Name"));
            assertTrue(jar.stream().anyMatch(entry ->
                    entry.getName().equals("module-info.class")
                            || entry.getName().endsWith("/module-info.class")));

            JarEntry packagedClass = jar.getJarEntry(
                    "io/github/gromoff97/awium/Awium.class");
            assertNotNull(packagedClass);
            try (InputStream content = jar.getInputStream(packagedClass)) {
                assertArrayEquals(Files.readAllBytes(MAIN_CLASS),
                        content.readAllBytes());
            }
        }

        Set<ModuleReference> modules = ModuleFinder.of(JAR).findAll();
        assertEquals(1, modules.size());
        ModuleReference module = modules.iterator().next();
        assertEquals("io.github.gromoff97.awium",
                module.descriptor().name());
        assertFalse(module.descriptor().isAutomatic());
        assertEquals(Set.of("io.github.gromoff97.awium",
                        "io.github.gromoff97.awium.exception"),
                module.descriptor().exports().stream()
                        .map(export -> export.source())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void currentProjectUsesOnlyTheGradleBuild() {
        for (Path required : List.of(Path.of("build.gradle.kts"),
                Path.of("settings.gradle.kts"), Path.of("gradlew"),
                Path.of("gradlew.bat"))) {
            assertTrue(Files.isRegularFile(required), required.toString());
        }
        for (Path forbidden : List.of(Path.of("pom.xml"), Path.of("mvnw"),
                Path.of("mvnw.cmd"), Path.of(".mvn"))) {
            assertFalse(Files.exists(forbidden), forbidden.toString());
        }
    }
}

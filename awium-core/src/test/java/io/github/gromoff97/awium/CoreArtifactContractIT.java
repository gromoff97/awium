package io.github.gromoff97.awium;

import java.lang.module.ModuleFinder;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static java.lang.ClassLoader.getPlatformClassLoader;
import static java.lang.ModuleLayer.boot;
import static java.lang.System.getProperty;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CoreArtifactContractIT {
    private static final Path JAR = Path.of(requireNonNull(getProperty("awium.core.jar")));

    @Test
    void coreRunsUserConditionsWithoutTheBuiltInLibraryOnClasspath() throws Exception {
        try (var loader = new URLClassLoader(new URL[]{consumerClasses(), JAR.toUri().toURL()}, getPlatformClassLoader())) {
            assertConsumer(loader);
        }
    }

    @Test
    void coreRunsUserConditionsAsAnIndependentNamedModule() throws Exception {
        var finder = ModuleFinder.of(JAR);
        var descriptor = finder.find("io.github.gromoff97.awium.core").orElseThrow().descriptor();
        assertFalse(descriptor.isAutomatic());
        assertEquals(Set.of("java.base"), Set.copyOf(descriptor.requires().stream().map(require -> require.name()).toList()));
        var configuration = boot().configuration().resolve(finder, ModuleFinder.of(), Set.of(descriptor.name()));
        var layer = boot().defineModulesWithOneLoader(configuration, getPlatformClassLoader());
        try (var loader = new URLClassLoader(new URL[]{consumerClasses()}, layer.findLoader(descriptor.name()))) {
            assertConsumer(loader);
        }
    }

    private static URL consumerClasses() {
        return consumer.CoreConsumer.class.getProtectionDomain().getCodeSource().getLocation();
    }

    private static void assertConsumer(ClassLoader loader) throws Exception {
        var consumer = loader.loadClass("consumer.CoreConsumer");
        assertArrayEquals(new Object[]{5, 5, 1, 1, false}, (Object[]) consumer.getMethod("run").invoke(null));
    }
}

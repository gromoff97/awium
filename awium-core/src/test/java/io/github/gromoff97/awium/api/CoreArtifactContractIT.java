package io.github.gromoff97.awium.api;

import java.lang.module.ModuleFinder;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.tools.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.google.testing.compile.Compiler.javac;
import static com.google.testing.compile.JavaFileObjects.forSourceString;
import static java.lang.ClassLoader.getPlatformClassLoader;
import static java.lang.ModuleLayer.boot;
import static java.lang.System.getProperty;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
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
        assertEquals(Set.of("io.github.gromoff97.awium", "io.github.gromoff97.awium.result",
                        "io.github.gromoff97.awium.function", "io.github.gromoff97.awium.execution"),
                Set.copyOf(descriptor.exports().stream().map(export -> export.source()).toList()));
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
        assertArrayEquals(new Object[]{5, 5, 1, 1, false, 5}, (Object[]) consumer.getMethod("run").invoke(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"io.github.gromoff97.awium.Explained", "io.github.gromoff97.awium.internal.AwaitRun",
            "io.github.gromoff97.awium.internal.ExecutionStep", "io.github.gromoff97.awium.internal.WaitCompletion"})
    void implementationTypesAreHiddenFromClasspathConsumers(String type) {
        var consumer = forSourceString("consumer.Contract", """
                package consumer;
                import %s;
                final class Contract {}
                """.formatted(type));
        var compilation = javac().withOptions("--release", "21", "-proc:none", "-classpath", JAR.toString()).compile(consumer);
        assertThat(compilation.errors()).extracting(Diagnostic::getCode).contains("compiler.err.not.def.public.cant.access");
    }

    @Test
    void engineInternalsAreHiddenFromModuleConsumers() {
        for (var type : List.of("WaitConfiguration", "WaitEngine", "AwaitRun", "GuardedAttempt",
                "ExecutionStep", "WaitCompletion", "DiagnosticText")) {
            var consumer = forSourceString("consumer.Contract", """
                    package consumer;
                    import %s;
                    final class Contract {}
                    """.formatted("io.github.gromoff97.awium.internal." + type));
            var module = forSourceString("module-info", "module consumer { requires io.github.gromoff97.awium.core; }");
            var compilation = javac().withOptions("--release", "21", "-proc:none", "--module-path", JAR.toString())
                    .compile(consumer, module);
            assertThat(compilation.errors()).extracting(Diagnostic::getCode).contains("compiler.err.package.not.visible");
        }
    }
}

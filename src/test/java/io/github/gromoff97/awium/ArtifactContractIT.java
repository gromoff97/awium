package io.github.gromoff97.awium;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.spi.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.isRegularFile;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(Set.of("io.github.gromoff97.awium"),
                Set.copyOf(module.descriptor().exports().stream().map(export -> export.source()).toList()));
        assertEquals(Set.of("java.base"),
                Set.copyOf(module.descriptor().requires().stream()
                        .map(require -> require.name()).toList()));
    }

    @Test
    void executionDoesNotDependOnFluentCatalogOrFormattingCode() {
        var output = new ByteArrayOutputStream();
        var writer = new PrintWriter(output, true, UTF_8);
        int exit = ToolProvider.findFirst("jdeps").orElseThrow().run(writer, writer,
                "--multi-release", "21", "-verbose:class", "-filter:none", JAR.toString());
        String dependencies = output.toString(UTF_8);

        assertEquals(0, exit, dependencies);
        Map<String, Set<String>> classes = classDependencies(dependencies);
        assertFalse(classes.isEmpty(), dependencies);
        Set<String> facade = Set.of("Await", "Conditions", "CollectionConditions", "MapConditions",
                "OptionalConditions", "StringConditions", "FailureDiagnostics");
        for (String execution : Set.of("Condition", "AwaitAttempt", "ConditionAwaitAttempt", "ConditionSupport",
                "ConditionResult", "AttemptResult", "SequenceExecution", "WaitEngine",
                "WaitCompletion", "WaitConfiguration")) {
            Set<String> used = classes.getOrDefault(execution, Set.of());
            assertTrue(used.stream().noneMatch(facade::contains), execution + " -> " + used);
        }
        for (String condition : Set.of("ConditionResult")) {
            assertTrue(classes.get(condition).stream().noneMatch(Set.of("AttemptRecord", "AwaitResult")::contains),
                    condition + " -> " + classes.get(condition));
        }
        Set<String> definition = classes.get("Condition");
        assertFalse(definition.contains("ConditionAwaitAttempt"), "Condition -> " + definition);
        // Condition construction is independent of source protocols, timing, diagnostics, and value comparison.
        Set<String> support = classes.get("ConditionSupport");
        assertTrue(support.stream().noneMatch(Set.of("WaitEngine", "WaitCompletion", "FailureDiagnostics", "ValueMatching")::contains),
                "ConditionSupport -> " + support);
        Set<String> sequence = classes.get("SequenceExecution");
        assertFalse(AwaitAttempt.class.isAssignableFrom(SequenceExecution.class));
        assertTrue(sequence.stream().noneMatch(Set.of("ConditionSupport", "WaitEngine", "WaitConfiguration")::contains),
                "SequenceExecution -> " + sequence);
        for (String catalog : Set.of("Conditions", "CollectionConditions", "MapConditions",
                "OptionalConditions", "StringConditions")) {
            assertTrue(classes.get(catalog).stream().noneMatch(Set.of("FailureDiagnostics")::contains),
                    catalog + " -> " + classes.get(catalog));
        }
        // Diagnostics describe failures; only the fluent facade chooses a terminal result or throws.
        Set<String> terminals = Set.of("Await", "AwaitResult", "AwaitAssertionError", "AwaitExecutionException");
        Set<String> diagnostics = classes.get("FailureDiagnostics");
        assertTrue(diagnostics.stream().noneMatch(terminals::contains), "FailureDiagnostics -> " + diagnostics);
    }

    @Test
    void packagedJarExecutesConsumerCodeInAnIndependentClassLoader() throws Exception {
        var testClasses = consumer.ArtifactConsumer.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new java.net.URL[]{testClasses, JAR.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            assertConsumerResult(loader);
        }
    }

    @Test
    void packagedJarExecutesAsANamedModule() throws Exception {
        var finder = ModuleFinder.of(JAR);
        var configuration = ModuleLayer.boot().configuration().resolve(
                finder, ModuleFinder.of(), Set.of("io.github.gromoff97.awium"));
        var layer = ModuleLayer.boot().defineModulesWithOneLoader(configuration, ClassLoader.getPlatformClassLoader());
        var moduleLoader = layer.findLoader("io.github.gromoff97.awium");
        assertEquals("io.github.gromoff97.awium", moduleLoader.loadClass(Await.class.getName()).getModule().getName());
        var testClasses = consumer.ArtifactConsumer.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new java.net.URL[]{testClasses}, moduleLoader)) {
            assertConsumerResult(loader);
        }
    }

    private static void assertConsumerResult(ClassLoader loader) throws Exception {
        Object[] result = (Object[]) loader.loadClass("consumer.ArtifactConsumer").getMethod("run").invoke(null);
        assertArrayEquals(new Object[]{"ready", "ready", 5, "ready", List.of("ready", "ready"), true, "ready",
                1, Map.entry("key", 1), "ready", "ready", "ready"}, result);
    }

    @Test
    void implementationTypesAreNotAccessibleToConsumers(@TempDir Path directory) throws Exception {
        for (Class<?> internal : List.of(ConditionSupport.class, AwaitAttempt.class, ConditionAwaitAttempt.class,
                AttemptResult.class, SequenceExecution.class, WaitEngine.class, WaitConfiguration.class,
                WaitCompletion.class,
                FailureDiagnostics.class, ValueMatching.class)) {
            assertFalse(java.lang.reflect.Modifier.isPublic(internal.getModifiers()), internal.getName());
        }
        for (boolean modulePath : List.of(false, true)) {
            Path consumer = java.nio.file.Files.createDirectory(directory.resolve(modulePath ? "module" : "classpath"));
            Path source = consumer.resolve("Contract.java");
            java.nio.file.Files.writeString(source, """
                    package consumer;
                    import java.util.concurrent.Callable;
                    import io.github.gromoff97.awium.ConditionAwaitAttempt;
                    final class Contract {}
                    """);
            var arguments = new java.util.ArrayList<>(List.of("--release", "21", "-proc:none", "-XDrawDiagnostics",
                    modulePath ? "--module-path" : "-classpath", JAR.toString(), "-d", consumer.toString(), source.toString()));
            if (modulePath) {
                Path descriptor = consumer.resolve("module-info.java");
                java.nio.file.Files.writeString(descriptor, "module consumer { requires io.github.gromoff97.awium; }");
                arguments.add(descriptor.toString());
            }
            var output = new ByteArrayOutputStream();
            int exit = javax.tools.ToolProvider.getSystemJavaCompiler().run(null, output, output, arguments.toArray(String[]::new));
            assertEquals(1, exit, output.toString(UTF_8));
            assertTrue(output.toString(UTF_8).contains("compiler.err.not.def.public.cant.access"), output.toString(UTF_8));
        }
    }

    private static Map<String, Set<String>> classDependencies(String output) {
        var dependencies = new HashMap<String, Set<String>>();
        output.lines().map(String::strip).map(line -> line.split("\\s+"))
                .filter(parts -> parts.length >= 3 && parts[1].equals("->")
                        && parts[0].startsWith("io.github.gromoff97.awium.")
                        && parts[2].startsWith("io.github.gromoff97.awium.")
                        && !parts[0].equals(parts[2]))
                .forEach(parts -> dependencies.computeIfAbsent(owner(parts[0]), ignored -> new HashSet<>()).add(owner(parts[2])));
        return dependencies;
    }
    private static String owner(String className) {
        return className.substring("io.github.gromoff97.awium.".length()).split("\\$", 2)[0];
    }

}

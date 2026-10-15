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

import static io.github.gromoff97.awium.CompilationSupport.compiles;
import static io.github.gromoff97.awium.CompilationSupport.compilesModule;
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
                "OptionalConditions", "StringConditions", "FailureDiagnostics", "ValueRendering");
        for (String execution : Set.of("Condition", "ConditionSession", "ConditionSupport",
                "ConditionResult", "ConditionMetadata", "CapturedEvaluator", "WaitEngine",
                "AttemptHistory", "WaitCompletion", "WaitConfiguration")) {
            Set<String> used = classes.getOrDefault(execution, Set.of());
            assertTrue(used.stream().noneMatch(facade::contains), execution + " -> " + used);
        }
        for (String condition : Set.of("Condition", "ConditionSession", "ConditionResult",
                "ConditionMetadata", "ConditionSupport", "CapturedEvaluator")) {
            assertTrue(classes.get(condition).stream().noneMatch(Set.of("AwaitAttempt", "AwaitResult")::contains),
                    condition + " -> " + classes.get(condition));
        }
        // Condition construction and composition are independent of source protocols, execution, and value comparison.
        Set<String> support = classes.get("ConditionSupport");
        assertTrue(support.stream().noneMatch(Set.of("Source", "CapturedEvaluator", "ConditionSession", "ValueMatching")::contains),
                "ConditionSupport -> " + support);
        for (String catalog : Set.of("Conditions", "CollectionConditions", "MapConditions",
                "OptionalConditions", "StringConditions")) {
            assertTrue(classes.get(catalog).stream().noneMatch(Set.of("Source", "FailureDiagnostics")::contains),
                    catalog + " -> " + classes.get(catalog));
        }
        // Diagnostics describe failures; only the fluent facade chooses a terminal result or throws.
        Set<String> terminals = Set.of("Await", "AwaitResult", "AwaitAssertionError", "AwaitExecutionException");
        Set<String> diagnostics = classes.get("FailureDiagnostics");
        assertTrue(diagnostics.stream().noneMatch(terminals::contains), "FailureDiagnostics -> " + diagnostics);
        Set<String> rendering = classes.getOrDefault("ValueRendering", Set.of());
        assertTrue(rendering.stream().allMatch("ValueRendering"::equals), "ValueRendering -> " + rendering);
        Set<String> callbacks = classes.getOrDefault("CheckedCalls", Set.of());
        assertTrue(callbacks.stream().allMatch("CheckedCalls"::equals), "CheckedCalls -> " + callbacks);
    }

    @Test
    void packagedJarCompilesDirectMethodReferences(@TempDir Path directory)
            throws Exception {
        assertTrue(compiles(directory, """
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.MapConditions.*;
                import static io.github.gromoff97.awium.OptionalConditions.*;
                import static io.github.gromoff97.awium.StringConditions.*;

                import io.github.gromoff97.awium.CollectionConditions;
                import io.github.gromoff97.awium.MapConditions;

                import java.util.List;
                import java.util.Map;
                import java.util.Optional;

                final class Contract {
                    String object() {
                        return await(this::loadObject).until(equalTo("value"));
                    }
                    String predicate() {
                        return await(this::loadObject).until(matches(String::isBlank));
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
    void packagedJarExposesTypedObservationsAndStructuredFailures(@TempDir Path directory) throws Exception {
        assertTrue(compiles(directory, """
                import io.github.gromoff97.awium.*;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Conditions.isNotNull;

                final class Contract {
                    AwaitFailure.Reason reason() {
                        AwaitResult<String, String> result = await(() -> "ready").tryUntil(isNotNull);
                        return switch (result) {
                            case AwaitResult.Satisfied<String, String> success -> null;
                            case AwaitResult.Failed<String, String> failed -> failed.failure().reason();
                        };
                    }
                    String observed(AwaitAttempt<String, String> attempt) {
                        return switch (attempt.outcome()) {
                            case AwaitAttempt.Outcome.Evaluated<String, String> value -> {
                                ConditionResult<? extends String> result = value.evaluation();
                                yield result instanceof ConditionResult.Satisfied<? extends String> success
                                        ? success.result() : value.observed();
                            }
                            default -> null;
                        };
                    }
                    AwaitFailure untilFailure() {
                        try {
                            await(() -> "ready").until(isNotNull);
                            return null;
                        } catch (AwaitAssertionError failure) {
                            return failure.failure();
                        } catch (AwaitExecutionException failure) {
                            return failure.failure();
                        }
                    }
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

                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.OptionalConditions.present;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import static io.github.gromoff97.awium.MapConditions.singleEntry;
                import static java.time.Duration.ofNanos;
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;
                import java.io.IOException;
                import io.github.gromoff97.awium.AwaitResult;

                final class Contract {
                    String value() {
                        return await(() -> "ready").usingTime(() -> 0L, nanos -> {}).until(isNotNull);
                    }
                    AwaitResult<String, String> predicate() {
                        return await(() -> "ready").tryUntil(matches(String::isBlank));
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
                        return await(() -> Optional.of("ready")).tryUntil(io.github.gromoff97.awium.OptionalConditions.hasValue(
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
    void packagedJarExecutesConsumerCodeInAnIndependentClassLoader(@TempDir Path directory) throws Exception {
        assertTrue(compiles(directory, """
                import io.github.gromoff97.awium.AwaitResult;
                import io.github.gromoff97.awium.Source;
                import java.io.IOException;
                import java.util.List;
                import java.util.Optional;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.OptionalConditions.*;
                import static java.time.Duration.ofNanos;

                public final class Contract {
                    public static Object[] run() {
                        var values = List.of("ready");
                        String optional = await(() -> Optional.of("ready")).until(present.because("optional %s", "value"));
                        String selected = await(() -> values).until(single.because("one value"));
                        int length = await(Contract::text).until(yields(Contract::length));
                        String narrowed = await((Source<Object>) Contract::text).until(instanceOf(String.class));
                        long[] time = {0};
                        List<String> stages = await(Contract::text).usingTime(() -> time[0], nanos -> time[0] += nanos).every(ofNanos(1)).upTo(ofNanos(5)).until(
                                equalTo("ready"), equalTo("ready"));
                        boolean preserved = await(() -> values).until(asserted(actual -> {
                            if (actual.size() != 1) throw new AssertionError("one value expected");
                        })) == values;
                        AwaitResult<Optional<String>, String> result = await(() -> Optional.of("ready")).tryUntil(present);
                        String captured = ((AwaitResult.Satisfied<Optional<String>, String>) result).result();
                        return new Object[]{optional, selected, length, narrowed, stages, preserved, captured};
                    }
                    private static String text() throws IOException { return "ready"; }
                    private static int length(String value) throws IOException { return value.length(); }
                }
                """, JAR));
        try (var loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL(), JAR.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Object[] result = (Object[]) loader.loadClass("Contract").getMethod("run").invoke(null);
            assertArrayEquals(new Object[]{"ready", "ready", 5, "ready", List.of("ready", "ready"), true, "ready"}, result);
        }
    }

    @Test
    void implementationTypesAreNotAccessibleToConsumers(@TempDir Path directory) throws Exception {
        for (Class<?> internal : List.of(ConditionSupport.class, ConditionSession.class,
                ConditionMetadata.class, CapturedEvaluator.class, WaitEngine.class, WaitConfiguration.class,
                WaitCompletion.class, AttemptHistory.class,
                FailureDiagnostics.class, ValueMatching.class, ValueRendering.class, CheckedCalls.class)) {
            assertFalse(java.lang.reflect.Modifier.isPublic(internal.getModifiers()), internal.getName());
        }
        for (boolean modulePath : List.of(false, true)) {
            Path consumer = java.nio.file.Files.createDirectory(directory.resolve(modulePath ? "module" : "classpath"));
            Path source = consumer.resolve("Contract.java");
            java.nio.file.Files.writeString(source, """
                    package consumer;
                    import io.github.gromoff97.awium.ConditionSession;
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

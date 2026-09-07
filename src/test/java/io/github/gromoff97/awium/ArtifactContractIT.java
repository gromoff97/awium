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
import java.util.List;
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
                "OptionalConditions", "StringConditions", "FailureFactory", "FailureMessageRenderer");
        for (String execution : Set.of("Condition", "ConditionDefinition", "ConditionSession", "ConditionFactories",
                "ConditionResult", "ConditionMetadata", "CapturedEvaluator", "WaitEngine",
                "ObservationEvaluator", "AttemptHistory", "WaitCompletion", "WaitConfiguration")) {
            Set<String> used = classes.getOrDefault(execution, Set.of());
            assertTrue(used.stream().noneMatch(facade::contains), execution + " -> " + used);
        }
        for (String condition : Set.of("Condition", "ConditionDefinition", "ConditionSession", "ConditionResult",
                "ConditionMetadata", "ConditionFactories", "CapturedEvaluator")) {
            assertTrue(classes.get(condition).stream().noneMatch(Set.of("AwaitAttempt", "AwaitResult")::contains),
                    condition + " -> " + classes.get(condition));
        }
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
    void implementationTypesAreNotAccessibleToConsumers(@TempDir Path directory) throws Exception {
        for (Class<?> internal : List.of(ConditionFactories.class, ConditionDefinition.class, ConditionSession.class,
                ConditionMetadata.class, CapturedEvaluator.class, WaitEngine.class, WaitConfiguration.class,
                WaitCompletion.class, ObservationEvaluator.class, AttemptHistory.class,
                FailureFactory.class, FailureMessageRenderer.class, ConditionSupport.class, ValueMatching.class)) {
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

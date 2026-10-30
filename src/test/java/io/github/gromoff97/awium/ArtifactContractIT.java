package io.github.gromoff97.awium;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.gromoff97.awium.internal.BoundAttempt;
import io.github.gromoff97.awium.internal.AttemptFactory;
import io.github.gromoff97.awium.internal.AwaitAttempt;
import io.github.gromoff97.awium.internal.SequenceExecution;
import io.github.gromoff97.awium.internal.WaitEngine;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import javax.tools.Diagnostic;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.google.testing.compile.Compiler.javac;
import static com.google.testing.compile.JavaFileObjects.forSourceString;
import static org.assertj.core.api.Assertions.assertThat;
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
    void currentBuildJarIsAnExplicitJavaBaseOnlyModule() throws Exception {
        assertTrue(isRegularFile(JAR), JAR.toString());

        ModuleReference module = ModuleFinder.of(JAR)
                .find("io.github.gromoff97.awium").orElseThrow();
        assertFalse(module.descriptor().isAutomatic());
        assertEquals(Set.of("io.github.gromoff97.awium", "io.github.gromoff97.awium.result",
                        "io.github.gromoff97.awium.function"),
                Set.copyOf(module.descriptor().exports().stream().map(export -> export.source()).toList()));
        assertEquals(Set.of("java.base"),
                Set.copyOf(module.descriptor().requires().stream()
                        .map(require -> require.name()).toList()));
    }

    @Test
    void executionDoesNotDependOnFluentCatalogOrFormattingCode() throws Exception {
        try (var jar = new JarFile(JAR.toFile())) {
            var classes = new ClassFileImporter().importJar(jar);
            assertIndependent(classes,
                    Set.of("Condition", "AwaitAttempt", "BoundAttempt", "AttemptFactory", "ConditionSupport",
                            "ConditionResult", "AttemptResult", "SequenceExecution", "ExecutionStep", "AttemptExecution",
                            "WaitEngine", "WaitCompletion", "WaitConfiguration"),
                    Set.of("Await", "ValueAwait", "SelectionAwait", "MapAwait", "Conditions", "CollectionConditions",
                            "MapConditions", "OptionalConditions", "StringConditions", "FailureDiagnostics", "DiagnosticText"));
            assertIndependent(classes, Set.of("ConditionResult"), Set.of("BoundAttempt", "Attempt", "AwaitResult"));
            assertIndependent(classes, Set.of("Condition"), Set.of("AttemptFactory", "AttemptResult"));
            assertIndependent(classes, Set.of("Reference", "ConditionResult.Unsatisfied"),
                    Set.of("AwaitAttempt", "BoundAttempt", "AttemptFactory", "SequenceExecution", "AttemptExecution",
                            "WaitEngine", "WaitCompletion"));
            assertIndependent(classes, Set.of("ConditionSupport"),
                    Set.of("WaitEngine", "WaitCompletion", "FailureDiagnostics", "ValueMatching"));
            assertFalse(AwaitAttempt.class.isAssignableFrom(SequenceExecution.class));
            assertIndependent(classes, Set.of("SequenceExecution"),
                    Set.of("ConditionSupport", "WaitEngine", "WaitConfiguration"));
            assertIndependent(classes, Set.of("BoundAttempt"),
                    Set.of("SequenceExecution", "WaitEngine", "AttemptHistory", "Attempt", "WaitConfiguration"));
            assertIndependent(classes,
                    Set.of("Conditions", "CollectionConditions", "MapConditions", "OptionalConditions", "StringConditions"),
                    Set.of("AwaitAttempt", "BoundAttempt", "AttemptFactory", "SequenceExecution", "AttemptExecution",
                            "WaitEngine", "WaitCompletion", "WaitConfiguration", "FailureDiagnostics"));
            assertIndependent(classes, Set.of("FailureDiagnostics"),
                    Set.of("Await", "ValueAwait", "SelectionAwait", "MapAwait", "AwaitResult", "AwaitAssertionError",
                            "AwaitExecutionException"));
        }
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
    void definitionBindingsStayPackagePrivate() {
        for (Class<?> internal : List.of(ConditionSupport.class, ValueMatching.class)) {
            assertFalse(java.lang.reflect.Modifier.isPublic(internal.getModifiers()), internal.getName());
        }
        for (boolean modulePath : List.of(false, true)) {
            assertInaccessible(ConditionSupport.class, modulePath, "compiler.err.not.def.public.cant.access");
        }
    }

    @Test
    void executionPackageIsHiddenFromModuleConsumers() {
        assertInaccessible(AttemptFactory.class, true, "compiler.err.package.not.visible");
        assertInaccessible(BoundAttempt.class, true, "compiler.err.package.not.visible");
        assertInaccessible(WaitEngine.class, true, "compiler.err.package.not.visible");
    }

    private static void assertInaccessible(Class<?> type, boolean modulePath, String diagnostic) {
        var source = forSourceString("consumer.Contract", """
                package consumer;
                import %s;
                final class Contract {}
                """.formatted(type.getName()));
        var sources = modulePath
                ? List.of(source, forSourceString("module-info", "module consumer { requires io.github.gromoff97.awium; }"))
                : List.of(source);
        var compilation = javac().withOptions("--release", "21", "-proc:none",
                        modulePath ? "--module-path" : "-classpath", JAR.toString()).compile(sources);
        assertThat(compilation.errors()).extracting(Diagnostic::getCode).contains(diagnostic);
    }

    private static void assertIndependent(JavaClasses classes, Set<String> origins, Set<String> targets) {
        noClasses().that(inFamilies(origins)).should().dependOnClassesThat(inFamilies(targets)).check(classes);
    }

    private static DescribedPredicate<JavaClass> inFamilies(Set<String> families) {
        return DescribedPredicate.describe("belong to " + families, type -> families.contains(owner(type.getName())));
    }

    private static String owner(String className) {
        // Value records may format themselves without coupling condition execution to formatting.
        if (className.equals(Condition.Reference.class.getName())) return "Reference";
        if (className.equals(io.github.gromoff97.awium.result.ConditionResult.Unsatisfied.class.getName())) {
            return "ConditionResult.Unsatisfied";
        }
        return className.substring(className.lastIndexOf('.') + 1).split("\\$", 2)[0];
    }

}

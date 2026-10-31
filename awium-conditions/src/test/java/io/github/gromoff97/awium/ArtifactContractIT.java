package io.github.gromoff97.awium;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.gromoff97.awium.execution.ConditionExecution;
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
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.Compiler.javac;
import static com.google.testing.compile.JavaFileObjects.forSourceString;
import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static java.lang.ClassLoader.getPlatformClassLoader;
import static java.lang.ModuleLayer.boot;
import static java.lang.System.getProperty;
import static java.nio.file.Files.isRegularFile;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactContractIT {

    @AutoClose
    private AutoCloseable resource = () -> {};

    static final Path CORE_JAR = Path.of(requireNonNull(getProperty("awium.core.jar")));
    static final Path CONDITIONS_JAR = Path.of(requireNonNull(getProperty("awium.conditions.jar")));
    static final String MODULE_PATH = CORE_JAR + java.io.File.pathSeparator + CONDITIONS_JAR;

    @Test
    void artifactsHaveSeparatePackagesAndOnlyAConditionsToCoreDependency() throws Exception {
        assertTrue(isRegularFile(CORE_JAR), CORE_JAR.toString());

        ModuleReference module = ModuleFinder.of(CORE_JAR, CONDITIONS_JAR)
                .find("io.github.gromoff97.awium.core").orElseThrow();
        assertFalse(module.descriptor().isAutomatic());
        assertEquals(Set.of("io.github.gromoff97.awium", "io.github.gromoff97.awium.result",
                        "io.github.gromoff97.awium.function", "io.github.gromoff97.awium.execution"),
                Set.copyOf(module.descriptor().exports().stream().map(export -> export.source()).toList()));
        assertEquals(Set.of("java.base"),
                Set.copyOf(module.descriptor().requires().stream()
                        .map(require -> require.name()).toList()));
        var conditions = ModuleFinder.of(CONDITIONS_JAR)
                .find("io.github.gromoff97.awium.conditions").orElseThrow().descriptor();
        assertFalse(conditions.isAutomatic());
        assertEquals(Set.of("io.github.gromoff97.awium.conditions"),
                Set.copyOf(conditions.exports().stream().map(export -> export.source()).toList()));
        assertEquals(Set.of("java.base", "io.github.gromoff97.awium.core"),
                Set.copyOf(conditions.requires().stream().map(require -> require.name()).toList()));
        assertTrue(conditions.requires().stream().anyMatch(require ->
                require.name().equals("io.github.gromoff97.awium.core")
                        && require.modifiers().contains(java.lang.module.ModuleDescriptor.Requires.Modifier.TRANSITIVE)));
        assertThat(module.descriptor().packages()).doesNotContainAnyElementsOf(conditions.packages());
    }

    @Test
    void executionDoesNotDependOnFluentCatalogOrFormattingCode() throws Exception {
        var coreJar = new JarFile(CORE_JAR.toFile());
        var conditionsJar = new JarFile(CONDITIONS_JAR.toFile());
        resource = () -> { coreJar.close(); conditionsJar.close(); };
        var classes = new ClassFileImporter().importJars(coreJar, conditionsJar);
        noClasses().that().resideOutsideOfPackage("..awium.conditions..")
                .should().dependOnClassesThat().resideInAPackage("..awium.conditions..")
                .check(classes);
        noClasses().that().resideInAPackage("..awium.conditions..")
                .should().dependOnClassesThat().resideInAPackage("io.github.gromoff97.awium.internal..")
                .check(classes);
        var core = Set.of("ConditionRun", "AwaitAttempt", "ConditionExecution", "ProbeFailure", "ExecutionStep",
                "AttemptExecution", "WaitEngine", "WaitCompletion", "WaitConfiguration");
        var catalogs = Set.of("Conditions", "CollectionConditions", "MapConditions", "OptionalConditions", "StringConditions");
        assertIndependent(classes, core,
                Set.of("Await", "ValueAwait", "SelectionAwait", "MapAwait", "Conditions", "CollectionConditions",
                        "MapConditions", "OptionalConditions", "StringConditions", "DiagnosticText"));
        noClasses().that(inFamilies(core)).should().dependOnClassesThat()
                .resideInAPackage("..awium.conditions.internal..").check(classes);
        noClasses().that().resideInAPackage("..awium.conditions.internal..").should().dependOnClassesThat(
                inFamilies(Set.of("WaitEngine", "Await", "ValueAwait", "SelectionAwait", "MapAwait",
                        "DiagnosticAwait", "AwaitSupport", "Conditions", "CollectionConditions", "MapConditions",
                        "OptionalConditions", "StringConditions"))).check(classes);
        assertIndependent(classes, catalogs,
                Set.of("AwaitAttempt", "ConditionRun", "ConditionExecution", "AttemptExecution",
                        "WaitEngine", "WaitCompletion", "WaitConfiguration", "FailureDiagnostics"));
        assertIndependent(classes, Set.of("AwaitSupport"),
                Set.of("CheckRun", "CapturedCondition", "AttemptHistory", "FailureDiagnostics"));
        assertIndependent(classes, Set.of("ConditionResult"), Set.of("ConditionExecution", "Attempt", "AwaitResult"));
        assertIndependent(classes, Set.of("WaitCompletion"), Set.of("AwaitFailure", "Attempt"));
        assertIndependent(classes, Set.of("AttemptHistory"),
                Set.of("ExecutionStep", "ConditionRun", "ConditionExecution", "AttemptExecution",
                        "WaitEngine", "WaitCompletion", "WaitConfiguration"));
        assertIndependent(classes, Set.of("Condition"),
                Set.of("WaitEngine", "WaitCompletion", "WaitConfiguration", "AwaitResult", "FailureDiagnostics"));
        assertIndependent(classes, Set.of("ConditionExecution"),
                Set.of("WaitEngine", "AttemptHistory", "Attempt", "WaitConfiguration"));
        assertIndependent(classes, Set.of("Reference", "ConditionResult.Unsatisfied"),
                Set.of("AwaitAttempt", "ConditionExecution", "AttemptExecution", "WaitEngine", "WaitCompletion"));
        assertIndependent(classes, Set.of("FailureDiagnostics"),
                Set.of("Await", "ValueAwait", "SelectionAwait", "MapAwait", "AwaitResult", "AwaitAssertionError",
                        "AwaitExecutionException"));
    }

    @Test
    void packagedJarExecutesConsumerCodeInAnIndependentClassLoader() throws Exception {
        var testClasses = consumer.ArtifactConsumer.class.getProtectionDomain().getCodeSource().getLocation();
        var loader = new URLClassLoader(new java.net.URL[]{testClasses, CORE_JAR.toUri().toURL(), CONDITIONS_JAR.toUri().toURL()}, getPlatformClassLoader());
        resource = loader;
        assertConsumerResult(loader);
    }

    @Test
    void packagedJarExecutesAsANamedModule() throws Exception {
        var finder = ModuleFinder.of(CORE_JAR, CONDITIONS_JAR);
        var configuration = boot().configuration().resolve(
                finder, ModuleFinder.of(), Set.of("io.github.gromoff97.awium.conditions"));
        var layer = boot().defineModulesWithOneLoader(configuration, getPlatformClassLoader());
        var moduleLoader = layer.findLoader("io.github.gromoff97.awium.core");
        assertEquals("io.github.gromoff97.awium.core", moduleLoader.loadClass(Await.class.getName()).getModule().getName());
        var testClasses = consumer.ArtifactConsumer.class.getProtectionDomain().getCodeSource().getLocation();
        var loader = new URLClassLoader(new java.net.URL[]{testClasses}, moduleLoader);
        resource = loader;
        assertConsumerResult(loader);
    }

    private static void assertConsumerResult(ClassLoader loader) throws Exception {
        loader.setPackageAssertionStatus("consumer", true);
        var consumer = loader.loadClass("consumer.ArtifactConsumer");
        assertTrue(consumer.desiredAssertionStatus());
        Object[] result = (Object[]) consumer.getMethod("run").invoke(null);
        assertArrayEquals(new Object[]{"ready", "ready", 5, "ready", List.of("ready", "ready"), true, "ready",
                1, Map.entry("key", 1), "ready", "ready", "ready", 5, 5, 5, 10, 10}, result);
    }

    @Test
    void conditionImplementationsAreHiddenFromModuleConsumers() {
        assertInaccessible(io.github.gromoff97.awium.conditions.internal.HasSizeCondition.class, true, "compiler.err.package.not.visible");
        assertInaccessible(io.github.gromoff97.awium.conditions.internal.NestedCondition.class, true, "compiler.err.package.not.visible");
    }

    @Test
    void engineInternalsAreHiddenFromModuleConsumers() {
        assertInaccessible(io.github.gromoff97.awium.internal.WaitConfiguration.class, true, "compiler.err.package.not.visible");
        assertInaccessible(WaitEngine.class, true, "compiler.err.package.not.visible");
    }

    private static void assertInaccessible(Class<?> type, boolean modulePath, String diagnostic) {
        var source = forSourceString("consumer.Contract", """
                package consumer;
                import %s;
                final class Contract {}
                """.formatted(type.getName()));
        var sources = modulePath
                ? List.of(source, forSourceString("module-info", "module consumer { requires io.github.gromoff97.awium.conditions; }"))
                : List.of(source);
        var compilation = javac().withOptions("--release", "21", "-proc:none",
                        modulePath ? "--module-path" : "-classpath", MODULE_PATH).compile(sources);
        assertThat(compilation.errors()).extracting(Diagnostic::getCode).contains(diagnostic);
    }

    private static void assertIndependent(JavaClasses classes, Set<String> origins, Set<String> targets) {
        noClasses().that(inFamilies(origins)).should().dependOnClassesThat(inFamilies(targets)).check(classes);
    }

    private static DescribedPredicate<JavaClass> inFamilies(Set<String> families) {
        return describe("belong to " + families, type -> families.contains(owner(type.getName())));
    }

    private static String owner(String className) {
        // Value records may format themselves without coupling condition execution to formatting.
        return Map.of(Reference.class.getName(), "Reference", Unsatisfied.class.getName(), "ConditionResult.Unsatisfied")
                .getOrDefault(className, className.substring(className.lastIndexOf('.') + 1).split("\\$", 2)[0]);
    }

}

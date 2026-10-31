package io.github.gromoff97.awium;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static java.lang.System.getProperty;

class CoreArchitectureTest {
    @Test
    void engineBoundariesDoNotDependOnCatalogsOrFormatting() throws Exception {
        try (var jar = new JarFile(Path.of(getProperty("awium.core.jar")).toFile())) {
            var classes = new ClassFileImporter().importJars(jar);
            noClasses().should().dependOnClassesThat().resideInAPackage("..awium.conditions..").check(classes);
            var core = Set.of("ConditionRun", "AwaitAttempt", "ConditionExecution", "ProbeFailure", "ExecutionStep",
                    "AttemptExecution", "WaitEngine", "WaitCompletion", "WaitConfiguration");
            assertIndependent(classes, core,
                    Set.of("Await", "ValueAwait", "SelectionAwait", "MapAwait", "Conditions", "CollectionConditions",
                            "MapConditions", "OptionalConditions", "StringConditions", "DiagnosticText"));
            noClasses().that(inFamilies(core)).should().dependOnClassesThat()
                    .resideInAPackage("..awium.conditions.internal..").check(classes);
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

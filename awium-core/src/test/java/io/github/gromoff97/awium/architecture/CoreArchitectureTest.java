package io.github.gromoff97.awium.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.result.AttemptResult.Unsatisfied;
import static java.lang.System.getProperty;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class CoreArchitectureTest {
    @TestFactory
    Stream<DynamicTest> componentBoundaries() throws IOException {
        try (var jar = new JarFile(Path.of(getProperty("awium.core.jar")).toFile())) {
            var classes = new ClassFileImporter().importJars(jar);
            return Stream.of(
                    noClasses().should().dependOnClassesThat().resideInAPackage("..awium.conditions..")
                            .as("core has no built-in condition dependencies"),
                    independent(Set.of("AwaitAttempt", "ConditionExecution", "ProbeFailure",
                                    "ExecutionStep", "AttemptExecution", "WaitEngine", "WaitCompletion", "WaitConfiguration"),
                            Set.of("Await", "AwaitSupport", "ValueAwait", "SelectionAwait", "MapAwait", "DiagnosticAwait",
                                    "DiagnosticSelectionAwait", "DiagnosticMapAwait", "DiagnosticText", "FailureDiagnostics"))
                            .as("polling and execution do not depend on facades or formatting"),
                    independent(Set.of("ConditionRun"), Set.of("WaitEngine", "AttemptExecution",
                            "Await", "AwaitSupport", "ValueAwait", "DiagnosticAwait"))
                            .as("run lifecycle owns history and results without polling or facade dependencies"),
                    independent(Set.of("AwaitSupport"), Set.of("AttemptHistory", "FailureDiagnostics"))
                            .as("waiting facades do not depend on run implementations"),
                    independent(Set.of("AttemptResult"), Set.of("ConditionExecution", "Attempt", "AwaitResult"))
                            .as("check outcomes do not depend on execution"),
                    independent(Set.of("WaitCompletion"), Set.of("AwaitFailure", "Attempt"))
                            .as("wait completion does not depend on public result variants"),
                    independent(Set.of("AttemptHistory"), Set.of("ExecutionStep", "ConditionRun", "ConditionExecution", "AttemptExecution",
                            "WaitEngine", "WaitCompletion", "WaitConfiguration"))
                            .as("history recording does not depend on execution"),
                    independent(Set.of("Condition"), Set.of("WaitEngine", "WaitCompletion", "WaitConfiguration", "AwaitResult", "FailureDiagnostics"))
                            .as("the condition contract does not depend on polling or diagnostics"),
                    independent(Set.of("ConditionExecution"), Set.of("WaitEngine", "ConditionRun", "AttemptHistory", "Attempt", "WaitConfiguration"))
                            .as("nested execution does not depend on polling or history"),
                    independent(Set.of("Reference", "AttemptResult.Unsatisfied"),
                            Set.of("AwaitAttempt", "ConditionExecution", "AttemptExecution", "WaitEngine", "WaitCompletion"))
                            .as("condition metadata does not depend on execution"),
                    independent(Set.of("FailureDiagnostics"),
                            Set.of("Await", "AwaitSupport", "ValueAwait", "SelectionAwait", "MapAwait", "DiagnosticAwait",
                                    "DiagnosticSelectionAwait", "DiagnosticMapAwait", "AwaitResult", "AwaitAssertionError", "AwaitExecutionException"))
                            .as("failure formatting does not depend on facades or exception wrappers"))
                    .map(rule -> dynamicTest(rule.getDescription(), () -> rule.check(classes)));
        }
    }

    private static ArchRule independent(Set<String> origins, Set<String> targets) {
        return noClasses().that(inFamilies(origins)).should().dependOnClassesThat(inFamilies(targets));
    }

    private static DescribedPredicate<JavaClass> inFamilies(Set<String> families) {
        return describe("belong to " + families, type -> families.contains(owner(type.getName())));
    }

    private static String owner(String className) {
        // Value records may format themselves without coupling condition execution to formatting.
        return Map.of(Reference.class.getName(), "Reference", Unsatisfied.class.getName(), "AttemptResult.Unsatisfied")
                .getOrDefault(className, className.substring(className.lastIndexOf('.') + 1).split("\\$", 2)[0]);
    }
}

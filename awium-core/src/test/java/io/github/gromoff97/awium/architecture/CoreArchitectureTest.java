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
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.result.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static java.lang.System.getProperty;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class CoreArchitectureTest {
    @TestFactory
    Stream<DynamicTest> componentBoundaries() throws IOException {
        try (var jar = new JarFile(Path.of(getProperty("awium.core.jar")).toFile())) {
            var classes = new ClassFileImporter().importJars(jar);
            return Stream.of(
                    fields().that().areStatic().should().beFinal()
                            .as("core has no reassignable global fields"),
                    fields().that().areNotStatic().and().areDeclaredInClassesThat(not(inFamilies(
                                    Set.of("AwaitRun", "GuardedAttempt", "AttemptHistory"))))
                            .should().beFinal()
                            .as("mutable instance fields belong to run, history or rendering state"),
                    noClasses().should().dependOnClassesThat().resideInAPackage("..awium.conditions..")
                            .as("core has no built-in condition dependencies"),
                    independent(Set.of("AwaitAttempt", "GuardedAttempt", "ProbeFailure",
                                    "ExecutionStep", "WaitEngine", "WaitCompletion", "WaitConfiguration"),
                            Set.of("Await", "AwaitSupport", "ValueAwait", "SelectionAwait", "MapAwait", "DiagnosticAwait",
                                    "DiagnosticSelectionAwait", "DiagnosticMapAwait", "DiagnosticText", "DiagnosticReport"))
                            .as("polling and nested execution do not depend on facades or formatting"),
                    noClasses().that().haveSimpleName("DiagnosticText")
                            .should().dependOnClassesThat(describe("belong to Awium outside scalar formatting", type ->
                                    type.getName().startsWith("io.github.gromoff97.awium.")
                                            && !type.getName().equals("io.github.gromoff97.awium.internal.DiagnosticText")))
                            .as("scalar formatting is independent of product objects and report state"),
                    independent(Set.of("AwaitRun"), Set.of("WaitEngine",
                            "Await", "AwaitSupport", "ValueAwait", "DiagnosticAwait"))
                            .as("run lifecycle owns history and results without polling or facade dependencies"),
                    noClasses().that().haveSimpleName("WaitEngine")
                            .should().callMethodWhere(describe("assemble a result while polling", call ->
                                    !call.getOrigin().getName().equals("execute")
                                            && call.getTargetOwner().getName().equals("io.github.gromoff97.awium.internal.AwaitRun")
                                            && call.getTarget().getName().equals("result")))
                            .as("polling uses step verdicts; final result assembly belongs to execute"),
                    independent(Set.of("AwaitSupport"), Set.of("AttemptHistory", "DiagnosticReport"))
                            .as("waiting facades do not depend on run implementations"),
                    independent(Set.of("AttemptResult"), Set.of("GuardedAttempt", "Attempt", "AwaitResult"))
                            .as("check outcomes do not depend on execution"),
                    independent(Set.of("WaitCompletion"), Set.of("AwaitFailure", "Attempt", "AwaitResult"))
                            .as("wait completion does not depend on public result variants"),
                    independent(Set.of("AttemptHistory"), Set.of("ExecutionStep", "GuardedAttempt", "AwaitRun",
                            "WaitEngine", "WaitCompletion", "WaitConfiguration"))
                            .as("history recording does not depend on execution"),
                    independent(Set.of("Condition"), Set.of("WaitEngine", "WaitCompletion", "WaitConfiguration", "AwaitResult", "DiagnosticReport"))
                            .as("the condition contract does not depend on polling or diagnostics"),
                    independent(Set.of("GuardedAttempt"), Set.of("WaitEngine", "AwaitRun", "AttemptHistory", "Attempt", "WaitConfiguration"))
                            .as("nested execution does not depend on polling or history"),
                    independent(Set.of("Reference", "AttemptResult.Unsatisfied"),
                            Set.of("AwaitAttempt", "GuardedAttempt", "AwaitRun", "WaitEngine", "WaitCompletion"))
                            .as("condition metadata does not depend on execution"),
                    independent(Set.of("DiagnosticReport"),
                            Set.of("AwaitRun", "Await", "AwaitSupport", "ValueAwait", "SelectionAwait", "MapAwait", "DiagnosticAwait",
                                    "DiagnosticSelectionAwait", "DiagnosticMapAwait", "AwaitResult", "AwaitAssertionError", "AwaitExecutionException"))
                            .as("failure formatting does not depend on run orchestration, facades or exception wrappers"))
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
        return Map.of(Reference.class.getName(), "Reference", Unsatisfied.class.getName(), "AttemptResult.Unsatisfied",
                        Reason.class.getName(), "Reason")
                .getOrDefault(className, className.substring(className.lastIndexOf('.') + 1).split("\\$", 2)[0]);
    }
}

package io.github.gromoff97.awium.conditions.architecture

import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.github.gromoff97.awium.AwaitCondition.Reference
import io.github.gromoff97.awium.conditions.support.conditionsJar
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import java.util.jar.JarFile
import org.junit.jupiter.api.Test

class ConditionsArchitectureTest {
    @Test
    fun `product code uses only exported core packages`() {
        noClasses().should().dependOnClassesThat().resideInAPackage("io.github.gromoff97.awium.internal..").check(productClasses)
    }

    @Test
    fun `condition implementations do not call factories or waiting facades`() {
        noClasses().that().resideInAPackage("..awium.conditions.internal..").should().dependOnClassesThat(
            inFamilies(setOf("WaitEngine", "Await", "ValueAwait", "SelectionAwait", "MapAwait", "DiagnosticAwait", "AwaitSupport",
                "Conditions", "CollectionConditions", "MapConditions", "OptionalConditions", "StringConditions"))
        ).check(productClasses)
    }

    @Test
    fun `catalogs leave execution to condition implementations`() {
        val catalogs = inFamilies(setOf("Conditions", "CollectionConditions", "MapConditions", "OptionalConditions", "StringConditions"))
        noClasses().that(catalogs)
            .should().dependOnClassesThat(inFamilies(setOf("ConditionRun", "ConditionExecution", "AttemptExecution",
                "WaitEngine", "WaitCompletion", "WaitConfiguration", "FailureDiagnostics"))).check(productClasses)
        noClasses().that(catalogs).should().accessClassesThat(inFamilies(setOf("AwaitAttempt"))).check(productClasses)
    }

    @Test
    fun `tests use only exported core packages`() {
        val classes = ClassFileImporter().importPackages("io.github.gromoff97.awium.conditions", "consumer")
        noClasses().should().dependOnClassesThat().resideInAPackage("io.github.gromoff97.awium.internal..").check(classes)
    }

    private fun inFamilies(families: Set<String>) = describe<JavaClass>("belong to $families") { families.contains(owner(it.name)) }

    private fun owner(name: String): String = when (name) {
        Reference::class.java.name -> "Reference"
        Unsatisfied::class.java.name -> "ConditionResult.Unsatisfied"
        else -> name.substringAfterLast('.').substringBefore('$')
    }

    companion object {
        private val productClasses by lazy { JarFile(conditionsJar.toFile()).use { ClassFileImporter().importJars(it) } }
    }
}

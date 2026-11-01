package io.github.gromoff97.awium.conditions.architecture

import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.github.gromoff97.awium.conditions.support.conditionsJar
import io.github.gromoff97.awium.conditions.support.coreJar
import java.lang.module.ModuleFinder
import java.util.jar.JarFile
import org.junit.jupiter.api.Test

class ConditionsArchitectureTest {
    @Test
    fun `product code uses only exported core packages`() {
        noClasses().should().dependOnClassesThat(coreInternals).check(productClasses)
    }

    @Test
    fun `condition implementations do not call factories or waiting facades`() {
        noClasses().that().resideInAPackage("..awium.conditions.internal..")
            .or(inFamilies(setOf("CapturedCondition"))).should().dependOnClassesThat(
            inFamilies(setOf("Await", "ValueAwait", "SelectionAwait", "MapAwait", "DiagnosticAwait", "DiagnosticSelectionAwait", "DiagnosticMapAwait",
                "Conditions", "CollectionConditions", "MapConditions", "OptionalConditions", "StringConditions"))
        ).check(productClasses)
    }

    @Test
    fun `catalogs leave execution to condition implementations`() {
        val catalogs = inFamilies(setOf("Conditions", "CollectionConditions", "MapConditions", "OptionalConditions", "StringConditions"))
        noClasses().that(catalogs)
            .should().dependOnClassesThat(inFamilies(setOf("ConditionRun", "ConditionExecution", "WaitCompletion"))).check(productClasses)
        noClasses().that(catalogs).should().accessClassesThat(inFamilies(setOf("AwaitAttempt"))).check(productClasses)
    }

    @Test
    fun `tests use only exported core packages`() {
        val classes = ClassFileImporter().importPackages("io.github.gromoff97.awium.conditions", "consumer")
        noClasses().should().dependOnClassesThat(coreInternals).check(classes)
    }

    private fun inFamilies(families: Set<String>) = describe<JavaClass>("belong to $families") {
        families.contains(it.name.substringAfterLast('.').substringBefore('$'))
    }

    companion object {
        private val productClasses by lazy { JarFile(conditionsJar.toFile()).use { ClassFileImporter().importJars(it) } }
        private val coreModule = ModuleFinder.of(coreJar).find("io.github.gromoff97.awium.core").orElseThrow().descriptor()
        private val unexportedCorePackages = coreModule.packages() - coreModule.exports().filterNot { it.isQualified }.map { it.source() }.toSet()
        private val coreInternals = describe<JavaClass>("belong to a core package without a public export") {
            it.packageName in unexportedCorePackages
        }
    }
}

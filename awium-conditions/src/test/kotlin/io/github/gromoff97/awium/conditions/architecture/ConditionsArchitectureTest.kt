package io.github.gromoff97.awium.conditions.architecture

import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaModifier.PUBLIC
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.support.conditionsJar
import io.github.gromoff97.awium.conditions.support.coreJar
import java.lang.module.ModuleFinder
import java.util.jar.JarFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionsArchitectureTest {
    @Test
    fun `product code uses only exported core packages`() {
        noClasses().should().dependOnClassesThat(coreInternals).check(productClasses)
    }

    @Test
    fun `condition implementations do not call factories or waiting facades`() {
        noClasses().that(inFamilies(catalogs).negate()).should().dependOnClassesThat(
            inFamilies(setOf("Await", "ValueAwait", "SelectionAwait", "MapAwait", "DiagnosticAwait", "DiagnosticSelectionAwait", "DiagnosticMapAwait",
                "CommonConditions", "CollectionConditions", "MapConditions", "OptionalConditions", "StringConditions"))
        ).check(productClasses)
    }

    @Test
    fun `catalogs leave execution to condition implementations`() {
        noClasses().that(inFamilies(catalogs))
            .should().dependOnClassesThat(inFamilies(setOf("ConditionRun", "ConditionExecution", "WaitCompletion"))).check(productClasses)
        noClasses().that(inFamilies(catalogs)).should().accessClassesThat(inFamilies(setOf("AwaitAttempt"))).check(productClasses)
    }

    @Test
    fun `condition packages expose only their factories`() {
        val publicTypes = productClasses.filter { it.packageName.startsWith("io.github.gromoff97.awium.conditions.") && PUBLIC in it.modifiers }
        assertThat(publicTypes.map { it.name }).containsExactlyInAnyOrderElementsOf(factories)
    }

    @Test
    fun `conditions contain only factory packages`() {
        val packages = productClasses.map { it.packageName }.filter { it.startsWith("io.github.gromoff97.awium.conditions") }.toSet()
        assertThat(packages).containsExactlyInAnyOrderElementsOf(factories.map { it.substringBeforeLast('.') })
    }

    @Test
    fun `helpers contain no condition definitions`() {
        noClasses().that().resideInAPackage("..awium.support..")
            .should().beAssignableTo(AwaitCondition::class.java).check(productClasses)
    }

    @Test
    fun `helpers do not depend on condition implementations`() {
        noClasses().that().resideInAPackage("..awium.support..")
            .should().dependOnClassesThat().resideInAnyPackage("..awium.conditions.common..", "..awium.conditions.collection..",
                "..awium.conditions.map..", "..awium.conditions.optional..", "..awium.conditions.string..")
            .check(productClasses)
    }

    @Test
    fun `tests use only exported core packages`() {
        val classes = ClassFileImporter().importPackages("io.github.gromoff97.awium.conditions",
            "io.github.gromoff97.awium.support", "io.github.gromoff97.awium.function.checked", "consumer")
        noClasses().should().dependOnClassesThat(coreInternals).check(classes)
    }

    private fun inFamilies(families: Set<String>) = describe<JavaClass>("belong to $families") {
        families.contains(it.name.substringAfterLast('.').substringBefore('$'))
    }

    companion object {
        private val factories = setOf(
            "io.github.gromoff97.awium.conditions.common.CommonConditions",
            "io.github.gromoff97.awium.conditions.collection.CollectionConditions",
            "io.github.gromoff97.awium.conditions.map.MapConditions",
            "io.github.gromoff97.awium.conditions.optional.OptionalConditions",
            "io.github.gromoff97.awium.conditions.string.StringConditions")
        private val catalogs = setOf("CommonConditions", "CollectionConditions", "MapConditions", "OptionalConditions", "StringConditions")
        private val productClasses by lazy { JarFile(conditionsJar.toFile()).use { ClassFileImporter().importJars(it) } }
        private val coreModule = ModuleFinder.of(coreJar).find("io.github.gromoff97.awium.core").orElseThrow().descriptor()
        private val unexportedCorePackages = coreModule.packages() - coreModule.exports().filterNot { it.isQualified }.map { it.source() }.toSet()
        private val coreInternals = describe<JavaClass>("belong to a core package without a public export") {
            it.packageName in unexportedCorePackages
        }
    }
}

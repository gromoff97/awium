package io.github.gromoff97.awium.conditions.api

import com.google.testing.compile.Compiler.javac
import com.google.testing.compile.JavaFileObjects.forSourceString
import io.github.gromoff97.awium.Await
import io.github.gromoff97.awium.conditions.common.HasSizeCondition
import io.github.gromoff97.awium.conditions.common.NestedCondition
import io.github.gromoff97.awium.conditions.support.conditionsJar
import io.github.gromoff97.awium.conditions.support.coreJar
import java.io.File.pathSeparator
import java.lang.ClassLoader.getPlatformClassLoader
import java.lang.ModuleLayer.boot
import java.lang.module.ModuleDescriptor.Requires.Modifier.TRANSITIVE
import java.lang.module.ModuleFinder
import java.net.URLClassLoader
import java.nio.file.Files.isRegularFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ArtifactContractIT {
    @Test
    fun `the conditions module exports its API and depends only on core`() {
        assertTrue(isRegularFile(coreJar), coreJar.toString())
        val core = ModuleFinder.of(coreJar).find("io.github.gromoff97.awium.core").orElseThrow().descriptor()
        val conditions = ModuleFinder.of(conditionsJar).find("io.github.gromoff97.awium.conditions").orElseThrow().descriptor()

        assertFalse(conditions.isAutomatic)
        assertEquals(setOf("io.github.gromoff97.awium.conditions", "io.github.gromoff97.awium.conditions.function",
            "io.github.gromoff97.awium.conditions.collection", "io.github.gromoff97.awium.conditions.map",
            "io.github.gromoff97.awium.conditions.optional", "io.github.gromoff97.awium.conditions.string"),
            conditions.exports().map { it.source() }.toSet())
        assertEquals(setOf("java.base", "io.github.gromoff97.awium.core"), conditions.requires().map { it.name() }.toSet())
        assertTrue(conditions.requires().single { it.name() == "io.github.gromoff97.awium.core" }.modifiers().contains(TRANSITIVE))
        assertThat(core.packages()).doesNotContainAnyElementsOf(conditions.packages())
    }

    @Test
    fun `packaged jars run a Java client without test dependencies`() {
        URLClassLoader(arrayOf(clientClasses, coreJar.toUri().toURL(), conditionsJar.toUri().toURL()), getPlatformClassLoader()).use {
            assertConsumerResult(it)
        }
    }

    @Test
    fun `packaged jars run as named modules`() {
        val finder = ModuleFinder.of(coreJar, conditionsJar)
        val configuration = boot().configuration().resolve(finder, ModuleFinder.of(), setOf("io.github.gromoff97.awium.conditions"))
        val layer = boot().defineModulesWithOneLoader(configuration, getPlatformClassLoader())
        val moduleLoader = layer.findLoader("io.github.gromoff97.awium.core")
        assertEquals("io.github.gromoff97.awium.core", moduleLoader.loadClass(Await::class.java.name).module.name)
        URLClassLoader(arrayOf(clientClasses), moduleLoader).use { assertConsumerResult(it) }
    }

    @ParameterizedTest
    @ValueSource(classes = [HasSizeCondition::class, NestedCondition::class])
    fun `condition implementations are hidden from module consumers`(type: Class<*>) {
        val source = forSourceString("consumer.Contract", """
            package consumer;
            import ${type.name};
            final class Contract {}
        """.trimIndent())
        val module = forSourceString("module-info", "module consumer { requires io.github.gromoff97.awium.conditions; }")
        val compilation = javac().withOptions("--release", "21", "-proc:none", "--module-path", "$coreJar$pathSeparator$conditionsJar")
            .compile(source, module)
        assertThat(compilation.errors().map { it.code }).contains("compiler.err.package.not.visible")
    }

    @ParameterizedTest
    @ValueSource(strings = ["EqualityCondition", "collection.SortedCondition", "map.SingleEntryCondition",
        "optional.PresentCondition", "string.BlankCondition"])
    fun `family implementations are hidden from classpath consumers`(type: String) {
        val source = forSourceString("consumer.Contract", """
            package consumer;
            import io.github.gromoff97.awium.conditions.$type;
            final class Contract {}
        """.trimIndent())
        val compilation = javac().withOptions("--release", "21", "-proc:none", "-classpath", "$coreJar$pathSeparator$conditionsJar")
            .compile(source)
        assertThat(compilation.errors().map { it.code }).contains("compiler.err.not.def.public.cant.access")
    }

    private fun assertConsumerResult(loader: ClassLoader) {
        val consumer = loader.loadClass("consumer.ArtifactConsumer")
        val result = consumer.getMethod("run").invoke(null) as Array<*>
        assertArrayEquals(arrayOf("ready", "ready", 5, "ready", listOf("ready", "ready"), true, "ready",
            1, java.util.Map.entry("key", 1), "ready", "ready", "ready", 5, 5, 5, 10, 10,
            true, "text has a length", null, listOf("ready", "ready")), result)
    }

    companion object {
        private val clientClasses = consumer.ArtifactConsumer::class.java.protectionDomain.codeSource.location
    }
}

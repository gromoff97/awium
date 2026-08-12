import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "io.github.gromoff97"
version = "0.1.0-SNAPSHOT"
description = "Await-and-retrieve assertions for Java"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testImplementation("org.openrewrite:rewrite-java-21:8.88.3")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.test {
    exclude("**/*IT.class")
}

val artifactTest = tasks.register<Test>("artifactTest") {
    description = "Verifies the packaged Awium artifact"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*IT.class")
    dependsOn(tasks.testClasses, tasks.jar)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

val verifyNoRuntimeDependencies = tasks.register("verifyNoRuntimeDependencies") {
    description = "Verifies that Awium has no runtime dependencies"
    group = "verification"
    doLast {
        check(configurations.runtimeClasspath.get().allDependencies.isEmpty()) {
            "Awium must not declare compile or runtime dependencies"
        }
    }
}

tasks.check {
    dependsOn(artifactTest, verifyNoRuntimeDependencies)
}

pitest {
    targetClasses = setOf("io.github.gromoff97.awium.*")
    targetTests = setOf("io.github.gromoff97.awium.*Test")
    excludedTestClasses = setOf(
        "io.github.gromoff97.awium.ArchitectureContractTest",
        "io.github.gromoff97.awium.CompilationContractTest",
        "io.github.gromoff97.awium.PublicSurfaceTest",
        "io.github.gromoff97.awium.RealTimeIntegrationTest",
        "io.github.gromoff97.awium.VirtualThreadIntegrationTest",
    )
    pitestVersion = "1.25.9"
    junit5PluginVersion = "1.2.3"
    threads = 4
    outputFormats = setOf("HTML", "XML", "CSV")
    timestampedReports = false
}

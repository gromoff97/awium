import org.gradle.api.tasks.compile.JavaCompile

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

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    inputs.file(tasks.jar.flatMap { it.archiveFile })
}

tasks.check {
    doLast {
        check(configurations.runtimeClasspath.get().allDependencies.isEmpty()) {
            "Awium must not declare compile or runtime dependencies"
        }
    }
}

pitest {
    targetClasses = setOf("io.github.gromoff97.awium.*")
    targetTests = setOf("io.github.gromoff97.awium.*Test")
    excludedTestClasses = setOf(
        "io.github.gromoff97.awium.CompilationContractTest",
        "io.github.gromoff97.awium.PublicSurfaceTest",
        "io.github.gromoff97.awium.RealTimeIntegrationTest",
        "io.github.gromoff97.awium.VirtualThreadIntegrationTest",
    )
    pitestVersion = "1.25.9"
    junit5PluginVersion = "1.2.3"
    threads = 4
    mutationThreshold = 90
    coverageThreshold = 90
    testStrengthThreshold = 90
    outputFormats = setOf("HTML")
    timestampedReports = false
}

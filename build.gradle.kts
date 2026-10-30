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
    testImplementation("org.junit-pioneer:junit-pioneer:2.3.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.google.testing.compile:compile-testing:0.23.0")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("org.apache.commons:commons-lang3:3.21.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    val testedJar = tasks.jar.flatMap { it.archiveFile }
    inputs.file(testedJar)
    systemProperty("awium.test.jar", testedJar.get().asFile)
}

tasks.check {
    doLast {
        check(configurations.compileClasspath.get().allDependencies.isEmpty()
                && configurations.runtimeClasspath.get().allDependencies.isEmpty()) {
            "Awium must not declare compile or runtime dependencies"
        }
    }
}

// Run mutation testing only on explicit user request; keep it separate from build/check/test.
pitest {
    targetClasses = setOf("io.github.gromoff97.awium.*")
    targetTests = setOf("io.github.gromoff97.awium.*Test")
    excludedTestClasses = setOf(
        "io.github.gromoff97.awium.*CompilationContractTest",
        "io.github.gromoff97.awium.PublicSurfaceTest",
        "io.github.gromoff97.awium.ThreadIntegrationTest",
    )
    pitestVersion = "1.25.9"
    junit5PluginVersion = "1.2.3"
    threads = 4
    mutators = setOf("ALL")
    mutationThreshold = 90
    coverageThreshold = 90
    testStrengthThreshold = 90
    outputFormats = setOf("HTML")
    timestampedReports = false
}

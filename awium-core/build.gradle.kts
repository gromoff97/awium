plugins {
    `java-library`
    jacoco
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "io.github.gromoff97"
version = "0.1.0-SNAPSHOT"
description = "Await engine and condition extension contracts"

repositories { mavenCentral() }
java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("org.apache.commons:commons-lang3:3.21.0")
    testImplementation("com.google.testing.compile:compile-testing:0.23.0")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
    testRuntimeOnly("org.slf4j:slf4j-jdk14:2.0.20")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    val testedJar = tasks.jar.flatMap { it.archiveFile }
    inputs.file(testedJar)
    systemProperty("awium.core.jar", testedJar.get().asFile)
}

jacoco.toolVersion = "0.8.16"

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

// Mutation testing requires an explicit user request; build/check/test never run it.
pitest {
    targetClasses = setOf("io.github.gromoff97.awium.*")
    targetTests = setOf("io.github.gromoff97.awium.*Test")
    excludedTestClasses = setOf(
        "io.github.gromoff97.awium.*CompilationContractTest",
        "io.github.gromoff97.awium.CoreArchitectureTest",
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

tasks.check {
    doLast {
        check(configurations.compileClasspath.get().allDependencies.isEmpty()
                && configurations.runtimeClasspath.get().allDependencies.isEmpty()) {
            "Core must not depend on other projects or libraries"
        }
    }
}

plugins {
    `java-library`
    jacoco
    kotlin("jvm") version "2.4.21"
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "io.github.gromoff97"
version = "0.1.0-SNAPSHOT"
description = "Ready-to-use Awium conditions"

repositories { mavenCentral() }
java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

dependencies {
    api("io.github.gromoff97:awium-core:0.1.0-SNAPSHOT")
    testImplementation(kotlin("stdlib"))
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

kotlin.compilerOptions.allWarningsAsErrors = true

tasks.test {
    useJUnitPlatform()
    val testedJar = tasks.jar.flatMap { it.archiveFile }
    val coreClasspath = configurations.runtimeClasspath.get()
    inputs.files(testedJar, coreClasspath)
    systemProperty("awium.conditions.jar", testedJar.get().asFile)
    doFirst { systemProperty("awium.core.jar", coreClasspath.singleFile) }
}

jacoco.toolVersion = "0.8.16"

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.check {
    doLast {
        for (name in listOf("compileClasspath", "runtimeClasspath")) {
            val dependencies = configurations.getByName(name).allDependencies.toList()
            check(dependencies.size == 1 && dependencies.single().let {
                it.group == "io.github.gromoff97" && it.name == "awium-core"
            }) { "Conditions must depend only on Awium core" }
        }
    }
}

// Mutation testing requires an explicit user request; build/check/test never run it.
pitest {
    targetClasses = setOf("io.github.gromoff97.awium.conditions.*")
    targetTests = setOf("io.github.gromoff97.awium.conditions.*Test")
    excludedTestClasses = setOf(
        "io.github.gromoff97.awium.conditions.*CompilationContractTest",
        "io.github.gromoff97.awium.conditions.CapturedConcurrencyTest",
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

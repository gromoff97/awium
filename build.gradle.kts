import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
    base
    id("info.solidsoft.pitest") version "1.19.0" apply false
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "info.solidsoft.pitest")

    group = "io.github.gromoff97"
    version = "0.1.0-SNAPSHOT"
    repositories { mavenCentral() }
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:6.1.2")
        "testImplementation"("org.assertj:assertj-core:3.27.7")
        "testImplementation"("org.mockito:mockito-core:5.24.0")
        "testImplementation"("org.apache.commons:commons-lang3:3.21.0")
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    tasks.named("check") {
        doLast {
            for (name in listOf("compileClasspath", "runtimeClasspath")) {
                check(configurations.getByName(name).allDependencies.toList().all { it is ProjectDependency }) {
                    "Product modules must not declare third-party dependencies"
                }
            }
        }
    }
    // Mutation testing is separate from build/check/test and requires an explicit user request.
    extensions.configure<PitestPluginExtension> {
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
}

tasks.named("assemble") { dependsOn(subprojects.map { "${it.path}:assemble" }) }
tasks.named("check") { dependsOn(subprojects.map { "${it.path}:check" }) }

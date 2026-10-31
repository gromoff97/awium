plugins {
    `java-library`
    `java-test-fixtures`
}

description = "Await engine and condition extension contracts"

dependencies {
    testFixturesImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}

tasks.test {
    val testedJar = tasks.jar.flatMap { it.archiveFile }
    inputs.file(testedJar)
    systemProperty("awium.core.jar", testedJar.get().asFile)
}

tasks.check {
    doLast {
        check(configurations.compileClasspath.get().allDependencies.isEmpty()
                && configurations.runtimeClasspath.get().allDependencies.isEmpty()) {
            "Core must not depend on other projects or libraries"
        }
    }
}

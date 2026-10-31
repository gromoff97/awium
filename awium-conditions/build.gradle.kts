plugins { `java-library` }

description = "Ready-to-use Awium conditions"

dependencies {
    api(project(":awium-core"))
    testImplementation(testFixtures(project(":awium-core")))
    testImplementation("com.google.testing.compile:compile-testing:0.23.0")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
    testRuntimeOnly("org.slf4j:slf4j-jdk14:2.0.20")
}

tasks.test {
    val testedJar = tasks.jar.flatMap { it.archiveFile }
    val coreJar = project(":awium-core").tasks.named<Jar>("jar").flatMap { it.archiveFile }
    inputs.files(testedJar, coreJar)
    systemProperty("awium.conditions.jar", testedJar.get().asFile)
    systemProperty("awium.core.jar", coreJar.get().asFile)
}

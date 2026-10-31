rootProject.name = "awium-conditions"

val localCore = file("../awium-core")
if (localCore.resolve("settings.gradle.kts").isFile) {
    includeBuild(localCore)
}

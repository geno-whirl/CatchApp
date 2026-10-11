pluginManagement {
    // `-PcoreOnly` builds just the pure-Kotlin logic, skipping Google's Maven entirely.
    val coreOnly = providers.gradleProperty("coreOnly").isPresent
    repositories {
        if (!coreOnly) google()
        // repo1 mirror first: the default Maven Central host rate-limits some networks.
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.10.1"
        id("org.jetbrains.kotlin.android") version "2.2.21"
        id("org.jetbrains.kotlin.jvm") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
        id("io.github.takahirom.roborazzi") version "1.43.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val coreOnly = providers.gradleProperty("coreOnly").isPresent
    repositories {
        if (!coreOnly) google()
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
}

rootProject.name = "CatchUp"

include(":core")
if (!providers.gradleProperty("coreOnly").isPresent) include(":app")

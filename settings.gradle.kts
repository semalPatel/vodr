pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.9.1"
        id("org.jetbrains.kotlin.android") version "2.2.21"
        id("org.jetbrains.kotlin.kapt") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
        id("com.google.dagger.hilt.android") version "2.57.1"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "vodr-android"

include(
    ":app",
    ":core-data",
    ":core-parser",
    ":core-segmentation",
    ":core-ai",
    ":core-tts",
    ":core-playback",
    ":feature-library",
    ":feature-generate",
    ":feature-player",
)

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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

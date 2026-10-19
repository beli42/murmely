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

rootProject.name = "Murmely"

include(":app", ":pixel_common")

// Media editor (formerly the separate repository android-media-editor)
include(":common", ":photoEditor", ":videoEditor")
project(":common").projectDir = file("media-editor/common")
project(":photoEditor").projectDir = file("media-editor/photoEditor")
project(":videoEditor").projectDir = file("media-editor/videoEditor")

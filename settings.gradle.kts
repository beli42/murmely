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
        // JitPack builds artifacts straight from GitHub repositories. It is only allowed for
        // uCrop (com.github.yalantis), nothing else may be resolved from there.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.yalantis") }
        }
    }
}

rootProject.name = "Bunny Media Editor"

include(":app", ":pixel_common")

// Media editor (formerly the separate repository android-media-editor)
include(":common", ":photoEditor", ":videoEditor")
project(":common").projectDir = file("media-editor/common")
project(":photoEditor").projectDir = file("media-editor/photoEditor")
project(":videoEditor").projectDir = file("media-editor/videoEditor")

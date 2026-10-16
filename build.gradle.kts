buildscript {
    dependencies {
        // AGP 9 compiles Kotlin itself (built-in Kotlin) with the Kotlin Gradle plugin it finds here:
        // our version instead of AGP's minimum, so it matches the Compose compiler and KSP
        classpath(libs.kotlin.gradle.plugin)
    }
}

// Plugin versions for all modules, from gradle/libs.versions.toml
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.aboutlibraries) apply false
}

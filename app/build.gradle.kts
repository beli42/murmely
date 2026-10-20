import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // The licence list of the whole app (pixel_common only sees its own dependencies);
    // the app's aboutlibraries.json replaces the one of pixel_common
    alias(libs.plugins.aboutlibraries)
}

aboutLibraries {
    collect {
        // Stickers and fonts bundled as files (not Gradle dependencies), with their full licences
        configPath = rootProject.file("config/aboutlibraries")
    }
    export {
        // Remove the "generated" timestamp to allow for reproducible builds
        excludeFields.add("generated")
    }
}

/** The app's version: shown in the app and part of the APK file names */
val murmelyVersion = "1.2"

// APK files named after the app and version, e.g. murmely-1.1.4-release.apk
base {
    archivesName = "murmely-$murmelyVersion"
}

/**
 * Release signing, never in the repository: a Gradle property (e.g. in ~/.gradle/gradle.properties,
 * outside the project) or else an environment variable (GitHub Actions secrets). Without all four,
 * release builds are made unsigned.
 */
fun signingValue(property: String, env: String): String? =
    (providers.gradleProperty(property).orNull ?: providers.environmentVariable(env).orNull)?.takeIf { it.isNotBlank() }

val releaseKeystore = signingValue("murmely.keystore", "MURMELY_KEYSTORE")
val releaseKeystorePassword = signingValue("murmely.keystorePassword", "MURMELY_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("murmely.keyAlias", "MURMELY_KEY_ALIAS")
val releaseKeyPassword = signingValue("murmely.keyPassword", "MURMELY_KEY_PASSWORD")
val canSignRelease = listOf(releaseKeystore, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null } &&
    file(releaseKeystore!!).exists()

// One APK for all devices: Murmely has no native code, so APKs per processor type were all the
// same. The version code is 100 * the release number, so it stays above the earlier per-ABI
// codes (100 * release + 1..4) and updates install over them.
val murmelyRelease = 8

android {
    namespace = "eu.artectrex.bunny"
    compileSdk = 37

    defaultConfig {
        // Own application id so this build can be installed next to the official Bunny
        applicationId = "io.github.beli42.murmely"
        minSdk = 29
        targetSdk = 34
        versionCode = murmelyRelease * 100
        versionName = murmelyVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Shown on the about page
        resValue("string", "versionName", versionName!!)
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Signed with the own release key when it is configured, else unsigned
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Needed for the resValue entries in defaultConfig
        resValues = true
    }

    lint {
        // Warnings fail the build. Findings that already existed are listed in lint-baseline.xml
        // and only new ones count. Version freshness is left to Dependabot.
        warningsAsErrors = true
        abortOnError = true
        baseline = file("lint-baseline.xml")
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Jetpack Compose (screens are moved to Compose one by one)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling.debug)

    // Media editor modules, in media-editor/
    implementation(project(":common"))
    implementation(project(":photoEditor"))
    implementation(project(":videoEditor"))
    implementation(project(":pixel_common"))

    // Unit tests (JVM): ./gradlew :app:testDebugUnitTest
    testImplementation(libs.junit)

    // Integration tests on a device/emulator: ./gradlew :app:connectedDebugAndroidTest
    // (they run inside the app; settings are saved and restored around each test)
    androidTestImplementation(libs.bundles.android.instrumented.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

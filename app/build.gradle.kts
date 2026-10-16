import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Version code per ABI (for F-Droid): 100 * versionCode + ABI code, e.g. 802 for arm64-v8a
val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
            val abiCode = abiCodes[abi] ?: 0
            output.versionCode.set(100 * (output.versionCode.get() ?: 0) + abiCode)
        }
    }
}

android {
    namespace = "eu.artectrex.bunny"
    compileSdk = 36

    defaultConfig {
        // Own application id so this build can be installed next to the official Bunny
        applicationId = "eu.artectrex.bunny.beli"
        minSdk = 29
        targetSdk = 34
        versionCode = 15
        versionName = "6.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Shown on the about page
        resValue("string", "versionName", versionName!!)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    splits {
        // One APK per ABI
        abi {
            isEnable = true
            reset()
            //noinspection ChromeOsAbiSupport
            include(*abiCodes.keys.toTypedArray())
            isUniversalApk = false
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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.pixeldroid.media_editor.videoEdit"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("staging")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
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
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(platform(libs.kotlin.bom))
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.androidx.recyclerview)
    implementation(libs.glide.library) {
        exclude(group = "com.android.support")
    }

    implementation(project(":common"))

    // Video playback and conversion (Media3; replaces Media2 and FFmpeg)
    implementation(libs.bundles.media3)

    // Unit tests (JVM): ./gradlew :videoEditor:testDebugUnitTest
    testImplementation(libs.junit)

    // Integration tests on a device/emulator: ./gradlew :videoEditor:connectedDebugAndroidTest
    androidTestImplementation(libs.bundles.android.instrumented.test)
}

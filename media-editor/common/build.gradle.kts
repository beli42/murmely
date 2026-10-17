import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.pixeldroid.media_editor.common"
    compileSdk = 37

    // On-device tests behave like the app: same targetSdk (34, see app/build.gradle.kts).
    // Unset, AGP 9 uses compileSdk (37), and Android then applies the stricter behaviour
    // of its newest version to the tests only
    testOptions {
        targetSdk = 34
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("staging")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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

    // Shared Compose theme (BunnyTheme)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
}

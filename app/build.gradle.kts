import java.util.Properties

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.ksp)
}

composeCompiler {
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

android {
    namespace = "com.enaboapps.switchify"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.enaboapps.switchify"
        minSdk = 29
        targetSdk = 36
        versionCode = gitVersionCode()
        versionName = "1.95.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        } else {
            throw GradleException("local.properties file not found")
        }

        if (localProperties.getProperty(
                "revenuecat.publicKey",
                ""
            ).isEmpty()
        ) {
            throw GradleException("RevenueCat public key is not set in local.properties")
        }
        buildConfigField(
            "String",
            "REVENUECAT_PUBLIC_KEY",
            "\"${localProperties.getProperty("revenuecat.publicKey", "")}\""
        )

        if (localProperties.getProperty(
                "amplitude.apiKey",
                ""
            ).isEmpty()
        ) {
            throw GradleException("Amplitude API key is not set in local.properties")
        }
        buildConfigField(
            "String",
            "AMPLITUDE_API_KEY",
            "\"${localProperties.getProperty("amplitude.apiKey", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

fun gitVersionCode(): Int {
    return runCatching {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()  // Optionally check the exit code if needed
        println("Git version code: $output")
        output.toInt()
    }.getOrElse { exception ->
        println("Warning: Failed to compute git version code: ${exception.message}")
        1  // Fallback value
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ai)
    implementation(libs.gson)
    implementation(libs.androidx.material3.android)
    implementation(libs.app.update)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.reviews)
    implementation(libs.play.services.reviews.ktx)
    implementation(libs.credentials)
    implementation(libs.google.id)
    implementation(libs.revenuecat)
    implementation(libs.revenuecat.ui)
    implementation(libs.amplitude.analytics)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.accompanist.permissions)
    implementation(libs.face.detection)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

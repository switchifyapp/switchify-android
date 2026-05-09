import java.util.Properties

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)

}

composeCompiler {
}

android {
    namespace = "com.enaboapps.switchify"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.enaboapps.switchify"
        minSdk = 29
        targetSdk = 36
        versionCode = gitVersionCode()
        versionName = "2.21.0"

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
                "timberlogs.apiKey",
                ""
            ).isEmpty()
        ) {
            throw GradleException("Timberlogs API key is not set in local.properties")
        }
        buildConfigField(
            "String",
            "TIMBERLOGS_API_KEY",
            "\"${localProperties.getProperty("timberlogs.apiKey", "")}\""
        )

        if (localProperties.getProperty(
                "supabase.projectUrl",
                ""
            ).isEmpty()
        ) {
            throw GradleException("Supabase project URL is not set in local.properties")
        }
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${localProperties.getProperty("supabase.projectUrl", "")}\""
        )

        if (localProperties.getProperty(
                "supabase.publishableKey",
                ""
            ).isEmpty()
        ) {
            throw GradleException("Supabase publishable key is not set in local.properties")
        }
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${localProperties.getProperty("supabase.publishableKey", "")}\""
        )

        if (localProperties.getProperty(
                "google.webClientId",
                ""
            ).isEmpty()
        ) {
            throw GradleException("Google Web Client ID is not set in local.properties")
        }
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${localProperties.getProperty("google.webClientId", "")}\""
        )
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.android)
    implementation(libs.gson)
    implementation(libs.androidx.material3.android)
    implementation(libs.app.update)
    implementation(libs.play.services.reviews)
    implementation(libs.play.services.reviews.ktx)
    implementation(libs.revenuecat)
    implementation(libs.revenuecat.ui)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.accompanist.permissions)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.reorderable)
    // New Google Identity Services with Credential Manager
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}


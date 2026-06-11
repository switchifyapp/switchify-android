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
        versionName = "2.31.0-beta.21"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Dual-channel config: env vars (used by CI from GitHub secrets) take
        // precedence, then local.properties (developer's local secrets). If
        // neither is present the build fails with the missing key name so
        // it's obvious what to set. local.properties is optional now; CI
        // builds don't generate one.
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        fun configValue(envVar: String, propKey: String): String =
            System.getenv(envVar)?.takeIf { it.isNotEmpty() }
                ?: localProperties.getProperty(propKey, "").takeIf { it.isNotEmpty() }
                ?: throw GradleException(
                    "Missing config: set $envVar env var or '$propKey' in local.properties"
                )

        buildConfigField(
            "String",
            "REVENUECAT_PUBLIC_KEY",
            "\"${configValue("REVENUECAT_PUBLIC_KEY", "revenuecat.publicKey")}\""
        )
        buildConfigField(
            "String",
            "TIMBERLOGS_API_KEY",
            "\"${configValue("TIMBERLOGS_API_KEY", "timberlogs.apiKey")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${configValue("SUPABASE_URL", "supabase.projectUrl")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${configValue("SUPABASE_ANON_KEY", "supabase.publishableKey")}\""
        )
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${configValue("GOOGLE_WEB_CLIENT_ID", "google.webClientId")}\""
        )
        buildConfigField(
            "String",
            "AI_MODEL_URL",
            "\"${configValue("AI_MODEL_URL", "aiModel.url")}\""
        )
    }

    // CI-only release signing: activates when UPLOAD_KEYSTORE_PATH points at
    // a real file (the release workflow decodes the base64 secret into
    // RUNNER_TEMP before invoking Gradle). Android Studio's "Generate Signed
    // Bundle" flow continues to work locally — without the env var the
    // signing config is silently absent and the release build type is left
    // unsigned for Studio to handle.
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("UPLOAD_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
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
            signingConfigs.findByName("release")
                ?.takeIf { it.storeFile?.exists() == true }
                ?.let { signingConfig = it }
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
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.gson)
    implementation(libs.androidx.material3.android)
    implementation(libs.app.update)
    implementation(libs.play.services.reviews)
    implementation(libs.play.services.reviews.ktx)
    implementation(libs.play.services.code.scanner)
    implementation(libs.revenuecat)
    implementation(libs.revenuecat.ui)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.accompanist.permissions)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.reorderable)
    // New Google Identity Services with Credential Manager
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.json)
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


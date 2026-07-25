import org.gradle.api.tasks.testing.Test
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.openapi.generator)
}

android {
    namespace = "fr.luteal.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.luteal.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Default folicular base URL for online sync. The debug/dev build
        // targets the local trial server (emulator loopback); the release
        // build overrides this with the production API (see buildTypes).
        buildConfigField("String", "SYNC_BASE_URL", "\"http://10.0.2.2:8080\"")
    }

    signingConfigs {
        create("release") {
            val keystoreProps = Properties().apply {
                val f = rootProject.file("keystore.properties")
                if (f.exists()) load(f.inputStream())
            }
            storeFile = keystoreProps.getProperty("storeFile")?.let { file(it) }
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Production folicular API. Release syncs over HTTPS only
            // (cleartext is not permitted); overrides the local-trial default.
            buildConfigField("String", "SYNC_BASE_URL", "\"https://luteal-api.waldemar.site\"")
            signingConfig = if (rootProject.file("keystore.properties").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
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

    // Generated folicular API contract DTOs (see docs/architecture/BACKEND_INTEGRATION.md).
    // The spec's single source of truth is the folicular repository; the path
    // is overridable with -Pfolicular.spec=... for other machine layouts.
    sourceSets {
        getByName("main") {
            kotlin.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
        }
    }
}

// Contract DTO generation from the folicular OpenAPI spec (models only;
// transport stays hand-written). Runs before every build.
val folicularSpec = providers.gradleProperty("folicular.spec")
    .getOrElse("$rootDir/../../Projects/folicular/openapi/openapi.yaml")

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(folicularSpec)
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
    globalProperties.set(mapOf("models" to ""))
    configOptions.set(
        mapOf(
            "packageName" to "fr.luteal.core.network.contract",
            "serializationLibrary" to "kotlinx_serialization",
            "dateLibrary" to "java8",
            "enumPropertyNaming" to "UPPERCASE",
            "sourceFolder" to "src/main/kotlin",
        )
    )
    // Quiet the per-file log noise; keep warnings and errors.
    verbose.set(false)
}

tasks.named("preBuild") {
    dependsOn("openApiGenerate")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Coroutines & Serialization (serialization also backs the generated
    // contract DTOs in fr.luteal.core.network.contract)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Online sync path. The network permission is declared in the main
    // manifest; the debug build additionally permits cleartext for the local
    // trial server (see app/src/debug). Release syncs over HTTPS only.
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Crash reporting (offline-friendly, user-consented dialog + mail)
    implementation(libs.acra.dialog)
    implementation(libs.acra.mail)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Pass the shared folicular conformance fixtures directory to unit tests
// (ConformanceFixturesTest decodes the same golden responses the backend's
// internal/contract fixtures test validates against the spec). Defaults to the
// sibling folicular checkout; override with -Pfolicular.conformance=/abs/path.
tasks.withType<Test> {
    systemProperty(
        "folicular.conformance",
        providers.gradleProperty("folicular.conformance")
            .getOrElse("$rootDir/../../Projects/folicular/conformance")
    )

    // Opt-in end-to-end trial against a running folicular instance
    // (E2eRoundTripTest). Skipped when unset so ordinary runs stay hermetic:
    //   ./gradlew testDebugUnitTest --tests '*E2eRoundTripTest' \
    //       -Pfolicular.e2e.url=http://127.0.0.1:8099
    providers.gradleProperty("folicular.e2e.url").orNull?.let {
        systemProperty("folicular.e2e.url", it)
    }
}

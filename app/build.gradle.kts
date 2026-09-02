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
        versionCode = 9
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Ship French and English only. French lives in res/values-fr/; the
        // default res/values/ is English, so devices in any other locale fall
        // back to English. AndroidX and Material would bring translations for
        // 84 locales (most of resources.arsc); keep this in step with
        // res/xml/locales_config.xml and the values-* folders.
        @Suppress("DEPRECATION")
        resourceConfigurations += listOf("fr", "en")

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
            // Sign only when real credentials are present. Without
            // keystore.properties the release APK is left unsigned rather than
            // falling back to the debug key: F-Droid signs its own builds, and
            // a silently debug-signed release is a footgun worth failing on.
            signingConfig = if (rootProject.file("keystore.properties").exists()) {
                signingConfigs.getByName("release")
            } else {
                null
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        // Room schema JSONs must reach Robolectric through the merged app
        // assets (android_merged_assets), so they ride on the debug source
        // set: MigrationTestHelper finds them in local unit tests, and
        // release builds ship no schemas.
        getByName("debug") {
            assets.srcDir("$projectDir/schemas")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    dependenciesInfo {
        // Disables Google Play dependency metadata in the APK signing block.
        // F-Droid scanner rejects APKs containing this extra block.
        includeInApk = false
        includeInBundle = false
    }

    // Generated folicular API contract DTOs (see docs/architecture/BACKEND_INTEGRATION.md).
    // The spec is vendored at contract/openapi.yaml; override the path with
    // -Pfolicular.spec=... to build against an upstream working copy.
    sourceSets {
        getByName("main") {
            kotlin.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
        }
    }
}

// Contract DTO generation from the folicular OpenAPI spec (models only;
// transport stays hand-written). Runs before every build.
//
// The spec is vendored at contract/openapi.yaml so a plain clone builds with
// no sibling checkout - see contract/README.md for how to refresh it. The
// folicular repository remains the upstream source of truth; point at it with
// -Pfolicular.spec=/abs/path/openapi.yaml when working on both sides at once.
val folicularSpec = providers.gradleProperty("folicular.spec")
    .getOrElse("$rootDir/contract/openapi.yaml")

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
    implementation(libs.androidx.biometric)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Home-screen widgets. Glance stays inside the existing app module: the
    // feature is small, and a separate module would add build structure without
    // creating a meaningful architectural boundary.
    implementation(libs.androidx.glance.appwidget)

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
    // androidx.security:security-crypto is deliberately absent. Google
    // deprecated it in April 2025 at 1.1.0-alpha07, and it pulled in Google
    // Tink - 1416 classes, about a fifth of this app - to protect a handful of
    // short strings. Secrets now use core/network/auth/KeystoreSecretStore,
    // which wraps an AndroidKeyStore AES-GCM key directly.
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
    testImplementation(libs.androidx.glance.testing)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Export Room schemas to app/schemas so migrations are testable and
// reviewable (see test assets wiring in android.sourceSets above).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Pass the shared folicular conformance fixtures directory to unit tests
// (ConformanceFixturesTest decodes the same golden responses the backend's
// internal/contract fixtures test validates against the spec). Vendored
// alongside the spec so a plain clone runs the full suite; override with
// -Pfolicular.conformance=/abs/path to test against an upstream working copy.
tasks.withType<Test> {
    systemProperty(
        "folicular.conformance",
        providers.gradleProperty("folicular.conformance")
            .getOrElse("$rootDir/contract/conformance")
    )

    // Opt-in end-to-end trial against a running folicular instance
    // (E2eRoundTripTest). Skipped when unset so ordinary runs stay hermetic:
    //   ./gradlew testDebugUnitTest --tests '*E2eRoundTripTest' \
    //       -Pfolicular.e2e.url=http://127.0.0.1:8099
    providers.gradleProperty("folicular.e2e.url").orNull?.let {
        systemProperty("folicular.e2e.url", it)
    }
}

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingPropertiesFile = System.getenv("COMFY_SIGNING_PROPERTIES")?.let { rootProject.file(it) }
    ?: rootProject.file(".local/android/signing/ComfyUIMobile-signing.properties").takeIf { it.isFile }
    ?: rootProject.file("../app/tools/signing/ComfyUIMobile-signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.local.comfyuimobile"
    // Glasmio 0.1.1 AARs declare minCompileSdk=37 and minSdkVersion=33.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.local.comfyuimobile"
        minSdk = 33
        targetSdk = 35
        versionCode = 174
        versionName = "0.1.74"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GITHUB_REPOSITORY", "\"7116-byte/ComfyUIMobile\"")
    }

    signingConfigs {
        if (signingPropertiesFile.isFile) {
            create("release") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            if (signingPropertiesFile.isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric installs Conscrypt; its JDK 17 socket implementation reflects
        // InetAddress when TLS tests run after Android tests in the same worker.
        unitTests.all { it.jvmArgs("--add-opens=java.base/java.net=ALL-UNNAMED") }
    }
}

kotlin {
    jvmToolchain(21)
}

// AGP 9 removed the legacy `applicationVariants` API, so the APK name is now set
// through the stable variant API.
androidComponents {
    onVariants(selector().all()) { variant ->
        val releaseSuffix = if (variant.buildType == "release") "-release" else ""
        variant.outputs.forEach { output ->
            val apkName = "ComfyUIMobile-v${output.versionName.get()}$releaseSuffix.apk"
            output.outputFileName.set(apkName)
        }
    }
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it.name.contains("Release", ignoreCase = true) } && !signingPropertiesFile.isFile) {
        throw GradleException("Release signing configuration is missing: $signingPropertiesFile")
    }
}

// AGP 9 puts the platform android.jar first on the unit test compile classpath, where its
// org.json.JSONObject stub shadows the reference org.json implementation used by the tests.
// Restore the previous order so the reference implementation wins, matching the runtime order.
// The hook runs after AGP registered its own task configuration, so it owns the final order.
afterEvaluate {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        if (!name.endsWith("UnitTestKotlin")) return@configureEach
        val ordered = libraries.files.sortedBy { if (it.name.startsWith("json-")) 0 else 1 }
        libraries.setFrom(ordered)
    }
}

dependencies {
    // Compose BOM 2026.09.00 is the baseline Glasmio 0.1.1 is built against
    // (foundation 1.12.1, material3 1.4.0, ui 1.12.1).
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Glasmio 0.1.1: core/glass/ui/preference/icons only. glasmio-scaffold is deliberately
    // excluded because it drags Navigation3 and MIUIX KMP into the dependency tree.
    implementation("io.github.neoruaa.glasmio:glasmio-core:0.1.1")
    implementation("io.github.neoruaa.glasmio:glasmio-glass:0.1.1")
    implementation("io.github.neoruaa.glasmio:glasmio-ui:0.1.1")
    implementation("io.github.neoruaa.glasmio:glasmio-preference:0.1.1")
    implementation("io.github.neoruaa.glasmio:glasmio-icons:0.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

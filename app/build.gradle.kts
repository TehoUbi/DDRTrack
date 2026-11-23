import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.tehoubi.ddrtracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tehoubi.ddrtracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ONNX Runtime is ~35 MB of native code per ABI. 64-bit ARM covers
        // phones (32-bit-only ones are too slow for the OCR model anyway) and
        // x86_64 covers the emulator.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Compress the native libraries in the APK (extracted on install): the
    // APK is shared as a file, so its size matters more than install time.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Release signing comes from keystore.properties (gitignored) when it
    // exists; otherwise release builds use the debug key so
    // `assembleRelease` always produces an installable APK.
    val keystoreProps = rootProject.file("keystore.properties")
    signingConfigs {
        if (keystoreProps.exists()) {
            val props = Properties().apply { keystoreProps.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // The PP-OCR models, read from assets at their full size on first scan.
    sourceSets["main"].assets.srcDir(rootProject.file("ocr/models"))
    androidResources {
        noCompress += "onnx"
    }
}

// Unit and screenshot tests don't depend on the build type, and the
// screenshot tests need the debug-only ui-test-manifest, so they run for
// debug only. Without this, `./gradlew test` failed in testReleaseUnitTest.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = false
    }
}

tasks.named("preBuild") { dependsOn(":ocr:downloadOcrModels") }

// Robolectric runs the app's tests on a real Android framework jar, which it
// otherwise downloads from Maven Central the first time, with no retry. One
// failed download failed 28 tests in a release build. Gradle fetches the jars
// instead (with retries, cached by CI like any dependency) and Robolectric
// runs offline. Android 14 is what the tests ask for with @Config(sdk = [34]);
// tests without it run on the target SDK, Android 15.
// One configuration per jar: within one, Gradle would keep only the newest version.
val robolectricAndroid = listOf("14-robolectric-10818077-i7", "15-robolectric-12650502-i7").map { version ->
    configurations.create("robolectricAndroid${version.substringBefore('-')}") { isTransitive = false }.also {
        dependencies.add(it.name, "org.robolectric:android-all-instrumented:$version")
    }
}
val robolectricJars = tasks.register<Sync>("robolectricJars") {
    from(robolectricAndroid)
    into(layout.buildDirectory.dir("robolectric-jars"))
}
tasks.withType<Test>().configureEach {
    dependsOn(robolectricJars)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-jars").get().asFile.absolutePath)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ocr"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    // Song jackets, fetched on demand and cached rather than bundled.
    implementation(libs.coil.compose)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // OCR. Photos are read by PP-OCRv6 on ONNX Runtime (the :ocr module),
    // which reads Japanese titles as well as Latin; ML Kit's fast Latin
    // model only watches the live preview to decide when to take the photo.
    implementation(libs.onnxruntime.android)
    implementation(libs.mlkit.text.recognition)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Leaderboard API
    implementation(libs.okhttp)

    // Room (local DB)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
}

/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

import org.lineageos.generatebp.GenerateBpPluginExtension
import org.lineageos.generatebp.models.Module
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application") version "8.7.1"
    id("org.jetbrains.kotlin.android") version "1.9.23"
    id("org.lineageos.generatebp") version "+"
}

val isCi = System.getenv("CI")?.equals("true", ignoreCase = true) == true
val gitCommitCount = try {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    if (process.waitFor() == 0) output.toIntOrNull() else null
} catch (_: Exception) {
    null
}
if (isCi && (gitCommitCount == null || gitCommitCount < 1)) {
    throw GradleException("CI requires a complete Git checkout to derive versionCode")
}
val buildVersionCode = gitCommitCount?.takeIf { it > 0 } ?: 1
val buildVersionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: "${LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)}.$buildVersionCode"

val keystoreFile = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val keystorePassword = System.getenv("KEYSTORE_PASS")?.takeIf { it.isNotBlank() }
val keyAliasName = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val keyPasswordValue = System.getenv("KEY_PASS")?.takeIf { it.isNotBlank() }
val signingValues = listOf(keystoreFile, keystorePassword, keyAliasName, keyPasswordValue)
val hasCompleteReleaseSigning = signingValues.all { it != null }
val hasPartialReleaseSigning = signingValues.any { it != null } && !hasCompleteReleaseSigning

android {
    compileSdk = 35
    namespace = "com.android.calculator2"

    defaultConfig {
        applicationId = "com.android.calculator2"
        minSdk = 30
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val productionReleaseSigning = if (hasCompleteReleaseSigning) {
        signingConfigs.create("productionRelease") {
            storeFile = file(keystoreFile!!)
            storePassword = keystorePassword
            keyAlias = keyAliasName
            keyPasswordValue = keyPassword
        }
    } else {
        null
    }

    buildTypes {
        getByName("release") {
            // A production release never inherits the debug package identity or debug flag.
            applicationIdSuffix = null
            versionNameSuffix = null
            isDebuggable = false
            signingConfig = productionReleaseSigning

            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            // Includes the default ProGuard rules files.
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android.txt"),
                    "proguard.flags"
                )
            )
        }
        getByName("debug") {
            // Append .dev to package name so we won't conflict with AOSP build.
            applicationIdSuffix = ".dev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main") {
            res.srcDirs("res")
            java.srcDirs("src")
            assets.srcDirs("assets")
            manifest.srcFile("AndroidManifest.xml")
        }
        // Pure-JVM unit tests live outside the AOSP-style "src" tree so that main (which globs
        // all of src/) does not compile the JUnit sources. Safe for generateBp: it only reads
        // releaseRuntimeClasspath, so test deps never reach Android.bp.
        getByName("test") {
            java.setSrcDirs(listOf("test/java"))
        }
        // Instrumented (on-device) UI tests, also outside src/.
        getByName("androidTest") {
            java.setSrcDirs(listOf("androidTest/java"))
        }
    }
}

// Do not break ordinary CI/debug builds that do not need release credentials. As soon as a
// release task enters the graph, however, CI requires all production secrets. Local release
// builds without them remain unsigned (never debug-signed) and emit a prominent warning.
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { task ->
        task.name.contains("release", ignoreCase = true)
    }
    if (!releaseRequested) {
        return@whenReady
    }
    if (hasPartialReleaseSigning) {
        val message = "Release signing is incomplete; set KEYSTORE_FILE, KEYSTORE_PASS, " +
                "KEY_ALIAS, and KEY_PASS together"
        if (isCi) {
            throw GradleException(message)
        }
        logger.warn("WARNING: $message. The local release APK will be unsigned.")
    } else if (!hasCompleteReleaseSigning) {
        if (isCi) {
            throw GradleException("CI release builds require production signing credentials")
        }
        logger.warn(
            "WARNING: Production signing credentials are missing; " +
                    "the local release APK will be unsigned (never debug-signed)."
        )
    } else if (!file(keystoreFile!!).isFile) {
        throw GradleException("KEYSTORE_FILE does not exist or is not a file: $keystoreFile")
    }
}

dependencies {
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.webkit:webkit:1.7.0-alpha02")
    implementation("com.google.android.material:material:1.14.0-alpha09")
    implementation("com.hp:crcalc:1.0")

    // Unit tests for the tools' pure logic (temperature/unit/mortgage/tax/rate math).
    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests (TaxTable.parse / RateCache JSON); android.jar's
    // org.json is stubbed and throws "not mocked" under testDebugUnitTest.
    testImplementation("org.json:json:20240303")

    // Instrumented UI tests (run on a device/emulator via ./gradlew connectedDebugAndroidTest).
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

configure<GenerateBpPluginExtension> {
    targetSdk.set(android.defaultConfig.targetSdk!!)
    minSdk.set(android.defaultConfig.minSdk!!)
    versionCode.set(android.defaultConfig.versionCode!!)
    versionName.set(android.defaultConfig.versionName!!)
    availableInAOSP.set { module: Module ->
        when {
            module.group.startsWith("androidx") -> true
            module.group.startsWith("org.jetbrains") -> true
            module.group == "com.google.android.material" -> true
            module.group == "com.google.errorprone" -> true
            module.group == "com.google.guava" -> true
            module.group == "junit" -> true
            else -> false
        }
    }
}

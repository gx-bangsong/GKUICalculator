/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

import org.lineageos.generatebp.GenerateBpPluginExtension
import org.lineageos.generatebp.models.Module

plugins {
    id("com.android.application") version "8.7.1"
    id("org.jetbrains.kotlin.android") version "1.9.23"
    id("org.lineageos.generatebp") version "+"
}

android {
    compileSdk = 35
    namespace = "com.android.calculator2"

    defaultConfig {
        applicationId = "com.android.calculator2"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
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

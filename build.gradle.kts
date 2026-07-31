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
        getByName("test") {
            java.srcDirs("test/src")
        }
    }
}

dependencies {
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.webkit:webkit:1.7.0-alpha02")
    implementation("com.google.android.material:material:1.14.0-alpha09")
    implementation("com.hp:crcalc:1.0")

    // Added for new ToolBox features
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
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

gradle.buildFinished {
    try {
        println("=== ALWAYS CLEANING GIT WORKSPACE AT THE END OF BUILD ===")
        Runtime.getRuntime().exec(arrayOf("git", "checkout", "--", "Android.bp", "libs/Android.bp")).waitFor()
        Runtime.getRuntime().exec(arrayOf("git", "clean", "-fd", "libs/")).waitFor()
    } catch (e: Exception) {
        // Ignore
    }
}

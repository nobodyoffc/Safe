plugins {
    id("com.android.application")
}

android {
    namespace = "com.fc.safe"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fc.safe"
        minSdk = 28
        targetSdk = 34
        versionCode = 203
        versionName = "2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Credentials live outside the repo, in ~/.gradle/gradle.properties.
            // Nothing here falls back to the debug key: a release that cannot be
            // signed privately fails in packageRelease instead of shipping.
            val storePath = providers.gradleProperty("SAFE_RELEASE_STORE_FILE").orNull
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("SAFE_RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("SAFE_RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("SAFE_RELEASE_KEY_PASSWORD").orNull
            }
            // v3 carries a rotation proof, so this key can be replaced later
            // without forcing every user to uninstall. minSdk 28 makes v1 dead weight.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.hawk)
    implementation("com.tencent:mmkv:1.3.9")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity:1.8.2")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation(project(":FC-AJDK"))
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // ZXing for QR code generation
    implementation(libs.core)

    // CameraX dependencies
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.extensions)
    
    // Guava for ListenableFuture
    implementation(libs.guava)
    implementation("com.google.guava:guava:32.1.3-android")
}
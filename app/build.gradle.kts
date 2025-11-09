plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.chaquopy) // Add this line
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.vehicledatarecorder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.vehicledatarecorder"
        minSdk = 21
        targetSdk = 27
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // On Apple silicon, you can omit x86_64.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

// Add this chaquopy block
chaquopy {
    defaultConfig {
        buildPython("C:/Users/HP/AppData/Local/Programs/Python/Python310/python.exe")
        pip {
            install("opencv-python")
            install("numpy") // OpenCV membutuhkan numpy
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    // Lifecycle
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    
    // Room dependencies
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Image Cropper
    implementation("com.vanniktech:android-image-cropper:4.5.0")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    implementation("com.serenegiant:common:1.5.20") {
        exclude(module = "support-v4")
    }

    // To recognize Latin script
    implementation("com.google.mlkit:text-recognition:16.0.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
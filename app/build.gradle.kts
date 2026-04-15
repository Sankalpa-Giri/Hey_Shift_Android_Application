plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.audibot"
<<<<<<< HEAD
    compileSdk = 36
=======
    compileSdk {
        version = release(36)
    }
>>>>>>> ec9c58831b83e21122cc7affc0a4e19d1e7c7676

    defaultConfig {
        applicationId = "com.example.audibot"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
<<<<<<< HEAD
=======

>>>>>>> ec9c58831b83e21122cc7affc0a4e19d1e7c7676
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
<<<<<<< HEAD

=======
>>>>>>> ec9c58831b83e21122cc7affc0a4e19d1e7c7676
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.core.splashscreen)
    implementation(libs.firebase.crashlytics.buildtools)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
<<<<<<< HEAD

    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.code.gson:gson:2.13.2")

    // FIX: 4.0.1 does not exist on Maven Central. The only v4 release is 4.0.0,
    // available on Sonatype. The settings.gradle.kts repositories block must
    // include mavenCentral() — 4.0.0 resolves from there.
    // Your .ppn asset is hey-shift_en_android_v4_0_0.ppn (v4 model),
    // so we need the v4 SDK. 4.0.0 is the correct version to use.
    implementation("ai.picovoice:porcupine-android:3.0.1")
=======
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.code.gson:gson:2.13.2")
    //implementation(libs.picovoice.porcupine)
    implementation("ai.picovoice:porcupine-android:4.0.0")
    implementation("ai.picovoice:android-voice-processor:1.0.2")
>>>>>>> ec9c58831b83e21122cc7affc0a4e19d1e7c7676
}
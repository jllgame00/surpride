plugins {
    alias(libs.plugins.android.application)
}

android {
    useLibrary("android.car", false)
    namespace = "com.surpride.navhostpoc"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.surpride.navhostpoc"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-host-feasibility"
    }
    // This module is an emulator-only static UI experiment, not a distributable navigator.
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-automotive:1.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}

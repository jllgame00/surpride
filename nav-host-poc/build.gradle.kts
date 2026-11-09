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
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-automotive:1.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("androidx.car.app:app-testing:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}

// Test-JVM-only homes keep Robolectric's Maven cache/download lock inside the workspace.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Keep test JVM files under a small project-local directory.
    val isolatedHome = rootProject.layout.projectDirectory.dir(".gradle/rt").asFile
    systemProperty("user.home", isolatedHome.absolutePath)
    systemProperty("maven.repo.local", isolatedHome.resolve("m2").absolutePath)
    systemProperty("java.io.tmpdir", isolatedHome.resolve("t").absolutePath)
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    doFirst { isolatedHome.mkdirs(); isolatedHome.resolve("t").mkdirs() }
}

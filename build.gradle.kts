// Plugins go on the root buildscript classpath so the Kotlin and Android plugins share one
// classloader. AGP is only added when an Android SDK is present (see settings.gradle.kts).
buildscript {
    val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        rootProject.file("local.properties").exists()

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
        if (hasAndroidSdk) {
            classpath("com.android.tools.build:gradle:8.7.3")
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.0")
        }
    }
}

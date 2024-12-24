package castle.sefirah

import org.gradle.api.JavaVersion as GradleJavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget as KotlinJvmTarget


object AndroidConfig {
    const val COMPILE_SDK = 35
    const val TARGET_SDK = 34
    const val MIN_SDK = 26

    val JavaVersion = GradleJavaVersion.VERSION_17
    val JvmTarget = KotlinJvmTarget.JVM_17
}
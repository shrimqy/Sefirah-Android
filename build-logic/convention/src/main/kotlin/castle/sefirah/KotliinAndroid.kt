package castle.sefirah

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = AndroidConfig.COMPILE_SDK

        defaultConfig {
            minSdk = AndroidConfig.MIN_SDK
        }

        compileOptions {
            sourceCompatibility = AndroidConfig.JavaVersion
            targetCompatibility = AndroidConfig.JavaVersion
            isCoreLibraryDesugaringEnabled = true
        }
    }

    configureKotlin<KotlinAndroidProjectExtension>()

    dependencies {
        add("coreLibraryDesugaring", libs.getLibrary("android.desugarJdkLibs"))
    }
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = AndroidConfig.JavaVersion
        targetCompatibility = AndroidConfig.JavaVersion
    }

    configureKotlin<KotlinJvmProjectExtension>()
}

/**
 * Configure base Kotlin options
 */
private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() = configure<T> {
    when (this) {
        is KotlinAndroidProjectExtension -> compilerOptions
        is KotlinJvmProjectExtension -> compilerOptions
        else -> TODO("Unsupported project extension $this ${T::class}")
    }.apply {
        // Use when hilt supports ksp2
//        apiVersion = KotlinVersion.KOTLIN_2_0
        jvmTarget = AndroidConfig.JvmTarget
        freeCompilerArgs = listOf(
            "-java-parameters",
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn",
            // Enable experimental coroutines APIs, including Flow
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-Xcontext-receivers"
        )
    }
}


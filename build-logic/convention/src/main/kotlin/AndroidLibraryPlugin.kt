import castle.sefirah.AndroidConfig
import castle.sefirah.applyHilt
import castle.sefirah.configureKotlinAndroid
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            applyHilt()

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = AndroidConfig.COMPILE_SDK

                buildTypes {
                    release {
                        isMinifyEnabled = false
                    }
                }
            }

        }
    }
}
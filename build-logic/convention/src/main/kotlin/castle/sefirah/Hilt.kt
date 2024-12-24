package castle.sefirah

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal fun Project.applyKSP() {
    with(pluginManager) {
        apply("com.google.devtools.ksp")
    }
}

internal fun Project.applyHilt() {
    applyKSP()

    with(pluginManager) {
        apply("com.google.dagger.hilt.android")
    }
    dependencies {
        implementation(libs.getLibrary("hilt.android"))
        "ksp"(libs.getLibrary("hilt-compiler"))
        "ksp"(libs.getLibrary("hilt-androidx-compiler"))
    }
}
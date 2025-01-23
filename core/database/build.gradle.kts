plugins {
    alias(libs.plugins.sefirah.android.library)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "sefirah.database"
}

dependencies {
    api(projects.domain)

    // Room components
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(project(":core:common"))
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
}
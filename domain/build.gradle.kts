plugins {
    alias(libs.plugins.sefirah.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "sefirah.domain"
}

dependencies {
    implementation(libs.bundles.ktor)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
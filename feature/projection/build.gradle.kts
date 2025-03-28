plugins {
    alias(libs.plugins.sefirah.android.library)
}

android {
    namespace = "sefirah.projection"
}

dependencies {
    api(projects.core.common)
    api(projects.core.presentation)
    api(projects.feature.notification)
    api(projects.domain)

    implementation(libs.core.ktx)
    implementation(libs.androidx.media)
    implementation(libs.androidx.media3.session)
}
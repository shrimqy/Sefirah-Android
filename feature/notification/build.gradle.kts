plugins {
    alias(libs.plugins.sefirah.android.library)
}

android {
    namespace = "sefirah.notification"
}

dependencies {
    api(projects.domain)
    api(projects.core.presentation)

    implementation(libs.core.ktx)
}
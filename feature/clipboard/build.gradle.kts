plugins {
    alias(libs.plugins.sefirah.android.library)
}

android {
    namespace = "sefirah.clipboard"
}

dependencies {
    api(projects.domain)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
}
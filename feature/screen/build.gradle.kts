plugins {
    alias(libs.plugins.sefirah.android.library)
}

android {
    namespace = "sefirah.screen"
}

dependencies {
    implementation(libs.core.ktx)
}
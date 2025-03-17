plugins {
    alias(libs.plugins.sefirah.android.library)
}

android {
    namespace = "sefirah.communication"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.androidSmsmms)
    implementation(libs.core.ktx)
    implementation(libs.commons.io)
    implementation(libs.commons.lang3)
    implementation(libs.commons.collections4)
}
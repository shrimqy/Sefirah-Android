plugins {
    alias(libs.plugins.sefirah.android.library)
}

android {
    namespace = "sefirah.communication"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    implementation(libs.android.smsmms)
    implementation(libs.core.ktx)
    implementation(libs.commons.io)
    implementation(libs.commons.lang3)
    implementation(libs.commons.collections4)
}
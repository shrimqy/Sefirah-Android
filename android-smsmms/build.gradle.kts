plugins {
    alias(libs.plugins.sefirah.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.klinker.android.send_message"
    compileSdk = 35
    useLibrary( "org.apache.http.legacy")

    lint
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.okhttp.urlconnection)
    implementation(libs.klinkerapps.logger)
}
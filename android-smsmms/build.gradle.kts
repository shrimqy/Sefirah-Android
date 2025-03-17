plugins {
    alias(libs.plugins.sefirah.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.klinker.android.send_message"
    compileSdk = 35
    useLibrary( "org.apache.http.legacy")

    lint

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.okhttp.urlconnection)
    implementation(libs.klinkerapps.logger)
}
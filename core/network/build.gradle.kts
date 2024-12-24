plugins {
    alias(libs.plugins.sefirah.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "sefirah.network"

    packaging {
        resources {
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES}"
            excludes += "META-INF/versions/**"
        }
    }
}

dependencies {
    api(projects.core.common)
    api(projects.data)
    api(projects.domain)

    api(projects.feature.notification)
    api(projects.feature.clipboard)
    api(projects.feature.media)
    api(projects.feature.screen)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.runtime)
    implementation(libs.apache.sshd.core)
    implementation(libs.apache.sshd.sftp)
    implementation(libs.apache.sshd.scp)
    implementation(libs.apache.sshd.mina)
    implementation(libs.apache.mina.core)
    implementation(libs.bcpkix.jdk18on)

    implementation(libs.bundles.ktor)
}
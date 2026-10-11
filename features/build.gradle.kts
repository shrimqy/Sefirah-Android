plugins {
    alias(libs.plugins.sefirah.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "sefirah.features"

    buildFeatures {
        compose = true
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES}"
            excludes += "META-INF/versions/**"
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(projects.worker)

    api(projects.core.common)
    api(projects.core.presentation)
    api(projects.domain)
    implementation(projects.core.database)

    implementation(libs.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.foundation)
    implementation(libs.animation)
    implementation(libs.material3.core)
    implementation(libs.android.smsmms)
    implementation(libs.commons.io)
    implementation(libs.commons.lang3)
    implementation(libs.commons.collections4)
    implementation(libs.androidx.media)
    implementation(libs.androidx.media3.session)
    implementation(libs.bundles.ktor)
    implementation(libs.androidx.documentfile)
    implementation(libs.apache.sshd.core)
    implementation(libs.apache.sshd.sftp)
    implementation(libs.apache.sshd.scp)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}

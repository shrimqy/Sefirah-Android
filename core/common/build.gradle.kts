plugins {
    alias(libs.plugins.sefirah.android.library)
}

android {
    namespace = "sefirah.common"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}


dependencies {

    implementation(libs.core.ktx)
    implementation(libs.androidx.appcompat)
}

plugins {
    id("tripinly.kmp.library")
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.navigation3.ui)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

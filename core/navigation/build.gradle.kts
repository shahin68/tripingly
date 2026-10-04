plugins {
    id("tripinly.kmp.library")
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.navigation3.ui)
            api(libs.androidx.lifecycle.viewmodelNavigation3)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

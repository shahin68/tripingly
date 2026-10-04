plugins {
    id("tripinly.kmp.library")
    id("tripinly.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.material.icons.extended)
        }
        androidMain.dependencies {
            implementation(libs.androidx.browser)
        }
    }
}

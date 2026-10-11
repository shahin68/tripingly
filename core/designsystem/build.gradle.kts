plugins {
    id("tripinly.kmp.library")
    id("tripinly.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.material.icons.extended)
            // Photos: the app's image loader (set up in shared/App.kt), used through PhotoImage.
            api(libs.coil.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.browser)
        }
    }
}

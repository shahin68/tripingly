plugins {
    id("tripinly.kmp.library")
    id("tripinly.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.model)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        androidMain.dependencies {
            // The system photo picker (rememberPhotoPicker).
            implementation(libs.androidx.activity.compose)
        }
    }
}

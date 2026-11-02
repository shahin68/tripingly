plugins {
    id("tripinly.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.data)
            // The system photo picker, for the optional profile photo.
            implementation(projects.core.ui)
        }
        androidMain.dependencies {
            implementation(projects.core.network)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
            implementation(libs.googleid)
        }
    }
}

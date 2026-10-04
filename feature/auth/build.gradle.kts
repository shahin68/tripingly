plugins {
    id("tripinly.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.data)
        }
        androidMain.dependencies {
            implementation(projects.core.network)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
            implementation(libs.googleid)
        }
    }
}

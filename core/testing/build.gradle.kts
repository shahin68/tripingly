plugins {
    id("tripinly.kmp.library")
}

// In-memory fakes of the repositories and the realtime client, for tests only: no app build depends on this module.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.data)
            api(projects.core.network)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}

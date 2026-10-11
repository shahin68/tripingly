plugins {
    id("tripinly.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.network)
            implementation(projects.core.storage)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

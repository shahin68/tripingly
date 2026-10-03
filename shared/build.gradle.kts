plugins {
    id("tripinly.kmp.library")
    id("tripinly.kmp.compose")
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.data)
            implementation(projects.core.designsystem)
            implementation(projects.core.navigation)
            implementation(projects.core.network)
            implementation(projects.feature.trips)
            implementation(projects.feature.map)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
    }
}

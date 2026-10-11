import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import java.util.Properties

plugins {
    id("tripinly.kmp.library")
    id("tripinly.kmp.compose")
    alias(libs.plugins.buildkonfig)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
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
            implementation(projects.core.location)
            implementation(projects.core.data)
            implementation(projects.core.designsystem)
            implementation(projects.core.navigation)
            implementation(projects.core.network)
            implementation(projects.core.storage)
            implementation(projects.feature.auth)
            implementation(projects.feature.trips)
            implementation(projects.feature.map)

            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.coil.network.ktor3)
        }
    }
}

buildkonfig {
    packageName = "com.falcon.tripingly"
    objectName = "AppBuildConfig"

    defaultConfigs {
        // The iOS app's Google Maps key, like MAPS_API_KEY for Android (androidApp). Without it the
        // iOS app shows Apple's map.
        buildConfigField(STRING, "MAPS_API_KEY_IOS", localProperties.getProperty("MAPS_API_KEY_IOS") ?: "")
    }
}

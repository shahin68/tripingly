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
            implementation(projects.feature.social)
            implementation(projects.feature.settings)
            implementation(projects.feature.paywall)

            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelNavigation3)
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
        // Firebase (push) for the iOS app, from GoogleService-Info.plist, which stays out of the repo. The
        // project and sender are the Android app's; the app ID and API key are the iOS app's own. Without
        // them the iOS app runs without push.
        buildConfigField(STRING, "FIREBASE_PROJECT_ID", localProperties.getProperty("firebase.projectId") ?: "")
        buildConfigField(STRING, "FIREBASE_SENDER_ID", localProperties.getProperty("firebase.senderId") ?: "")
        buildConfigField(STRING, "FIREBASE_IOS_APP_ID", localProperties.getProperty("firebase.ios.appId") ?: "")
        buildConfigField(STRING, "FIREBASE_IOS_API_KEY", localProperties.getProperty("firebase.ios.apiKey") ?: "")
        // RevenueCat's public iOS key (Test Store key until the App Store app exists). Without it the iOS
        // app has no purchases.
        buildConfigField(STRING, "REVENUECAT_IOS_KEY", localProperties.getProperty("revenuecat.iosKey") ?: "")
    }
}

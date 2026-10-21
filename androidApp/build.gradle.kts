import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val mapsApiKey = localProperties.getProperty("MAPS_API_KEY") ?: ""

// Firebase (push) from the values in google-services.json, which stays out of the repo. Without them
// (CI, a fresh checkout) the app runs without push.
fun firebaseValue(name: String) = "\"" + (localProperties.getProperty("firebase.$name") ?: "") + "\""

// RevenueCat's public Android key (Test Store key until the Play Store app exists). Without it the app
// has no purchases.
val revenueCatKey = "\"" + (localProperties.getProperty("revenuecat.androidKey") ?: "") + "\""

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))
    implementation(project(":core:data"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.revenuecat.purchases)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.falcon.tripingly"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.falcon.tripingly"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = providers.gradleProperty("tripinly.appVersion").get()
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "FIREBASE_PROJECT_ID", firebaseValue("projectId"))
        buildConfigField("String", "FIREBASE_SENDER_ID", firebaseValue("senderId"))
        buildConfigField("String", "FIREBASE_APP_ID", firebaseValue("appId"))
        buildConfigField("String", "FIREBASE_API_KEY", firebaseValue("apiKey"))
        buildConfigField("String", "REVENUECAT_API_KEY", revenueCatKey)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
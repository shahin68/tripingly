plugins {
    id("tripinly.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.data)
            implementation(projects.core.ui)
            implementation(projects.core.location)
            implementation(libs.compose.material.icons.extended)
        }
        androidMain.dependencies {
            implementation(libs.playServices.maps)
            implementation(libs.maps.compose)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.compose)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.compose.uiTest)
            implementation(libs.androidx.compose.uiTestManifest)
        }
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric pokes at JDK internals (e.g. jdk.internal.access.SharedSecrets) that are
    // module-encapsulated by default on JDK 17+.
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.security=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED"
    )
}

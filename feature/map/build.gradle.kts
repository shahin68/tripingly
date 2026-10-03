plugins {
    id("tripinly.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.data)
            // Marker use cases still talk to MarkerDao directly; a MarkerRepository in :core:data replaces this in the trips-on-API stage.
            implementation(projects.core.database)
        }
        androidMain.dependencies {
            implementation(libs.playServices.maps)
            implementation(libs.playServices.location)
            implementation(libs.maps.compose)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
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

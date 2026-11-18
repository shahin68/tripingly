plugins {
    id("tripinly.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.data)
            implementation(projects.core.ui)
            // Back on the members sheet's Add people page returns to the members (as in feature:map).
            implementation(libs.compose.uiBackhandler)
        }
    }
}

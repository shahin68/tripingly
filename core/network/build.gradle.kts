import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.util.Properties

plugins {
    id("tripinly.kmp.library")
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.openapiGenerator)
    // KSP must come before Ktorfit: the Ktorfit plugin wires its processor only when KSP is applied.
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
}

val generatedApiDir = layout.buildDirectory.dir("generated/openapi")

// Local config (secrets, client IDs, switches) lives in the untracked local.properties at the repo root.
// A -P flag or Gradle property with the same name is the fallback, e.g. for CI.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localConfig(name: String, default: String = ""): String =
    localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull ?: default

kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }
        commonMain {
            kotlin.srcDir(generatedApiDir.map { it.dir("src/commonMain/kotlin") })
            dependencies {
                api(projects.core.common)
                implementation(projects.core.storage)
                api(libs.ktor.client.core)
                api(libs.ktorfit.lib.light)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                api(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.koin.core)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // Held at the version in the catalog until the project moves to compileSdk 37.
            implementation("com.squareup.okhttp3:okhttp") { version { strictly(libs.versions.okhttp.get()) } }
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

// API models generated from the backend's OpenAPI document. Refresh openapi.json
// from the backend repo (or https://<api>/v1/openapi.json) when the contract changes.
val generateApiModels = tasks.named<GenerateTask>("openApiGenerate") {
    generatorName.set("kotlin")
    library.set("multiplatform")
    inputSpec.set(layout.projectDirectory.file("openapi.json").asFile.path)
    outputDir.set(generatedApiDir.get().asFile.path)
    packageName.set("com.falcon.tripingly.core.network")
    modelPackage.set("com.falcon.tripingly.core.network.model")
    globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
    configOptions.set(
        mapOf(
            "dateLibrary" to "kotlinx-datetime",
            "enumPropertyNaming" to "UPPERCASE",
            "sourceFolder" to "src/commonMain/kotlin",
        ),
    )
    typeMappings.set(mapOf("DateTime" to "kotlin.time.Instant", "AnyType" to "JsonElement"))
    importMappings.set(mapOf("JsonElement" to "kotlinx.serialization.json.JsonElement"))
    cleanupOutput.set(true)
}

tasks.withType<KotlinCompilationTask<*>>().configureEach { dependsOn(generateApiModels) }
// KSP (Ktorfit) reads the API interfaces, which use the generated models.
tasks.matching { it.name.startsWith("ksp") || it.name.endsWith("SourcesJar", ignoreCase = true) }.configureEach {
    dependsOn(generateApiModels)
}

// Base URL per environment: -Pbuildkonfig.flavor=local|staging|production (default staging).
// Production has no domain yet (docs/knowledge/09-open-questions.md).
buildkonfig {
    packageName = "com.falcon.tripingly.core.network"
    objectName = "NetworkBuildConfig"

    defaultConfigs {
        buildConfigField(STRING, "ENVIRONMENT", "staging")
        buildConfigField(STRING, "BASE_URL", "https://api-staging-4ade.up.railway.app/v1")
        // tripinly.useFakeApi=true: repositories use their fakes (demo before an endpoint exists).
        buildConfigField(BOOLEAN, "USE_FAKE_API", localConfig("tripinly.useFakeApi", "false"))
        buildConfigField(STRING, "APP_VERSION", providers.gradleProperty("tripinly.appVersion").getOrElse("1.0"))
        // POST /auth/dev for testing before the Google/Apple keys exist. Staging needs its secret:
        // put tripinly.devAuthSecret in local.properties, never in a tracked file.
        buildConfigField(BOOLEAN, "DEVELOPER_SIGN_IN", "true")
        buildConfigField(STRING, "DEV_AUTH_SECRET", localConfig("tripinly.devAuthSecret"))
        // The backend's Google Web client ID (Android asks Google for a token with this audience).
        // Not a secret; set tripinly.googleWebClientId in local.properties once it exists.
        buildConfigField(STRING, "GOOGLE_WEB_CLIENT_ID", localConfig("tripinly.googleWebClientId"))
    }
    defaultConfigs("local") {
        buildConfigField(STRING, "ENVIRONMENT", "local")
        // The Android emulator reaches the host as 10.0.2.2; the iOS simulator as localhost.
        buildConfigField(STRING, "BASE_URL", "http://10.0.2.2:3000/v1")
    }
    targetConfigs("local") {
        create("iosArm64") { buildConfigField(STRING, "BASE_URL", "http://localhost:3000/v1") }
        create("iosSimulatorArm64") { buildConfigField(STRING, "BASE_URL", "http://localhost:3000/v1") }
    }
    defaultConfigs("production") {
        buildConfigField(STRING, "ENVIRONMENT", "production")
        buildConfigField(STRING, "BASE_URL", "")
        buildConfigField(BOOLEAN, "USE_FAKE_API", "false")
        buildConfigField(BOOLEAN, "DEVELOPER_SIGN_IN", "false")
        buildConfigField(STRING, "DEV_AUTH_SECRET", "")
    }
}

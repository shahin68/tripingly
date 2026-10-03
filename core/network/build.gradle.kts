import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("tripinly.kmp.library")
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.openapiGenerator)
}

val generatedApiDir = layout.buildDirectory.dir("generated/openapi")

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
                api(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.auth)
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
tasks.matching { it.name.endsWith("SourcesJar", ignoreCase = true) }.configureEach {
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
        buildConfigField(BOOLEAN, "LOG_REQUESTS", "true")
        // -Ptripinly.useFakeApi=true: repositories use their fakes (demo before an endpoint exists).
        buildConfigField(BOOLEAN, "USE_FAKE_API", providers.gradleProperty("tripinly.useFakeApi").getOrElse("false"))
        buildConfigField(STRING, "APP_VERSION", providers.gradleProperty("tripinly.appVersion").getOrElse("1.0"))
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
        buildConfigField(BOOLEAN, "LOG_REQUESTS", "false")
        buildConfigField(BOOLEAN, "USE_FAKE_API", "false")
    }
}

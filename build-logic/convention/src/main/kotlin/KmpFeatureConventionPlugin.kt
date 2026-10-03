import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A feature module: library + Compose, plus what every screen needs (core modules, Koin, lifecycle).
 * Features never depend on other features; shared pieces move down into a core module.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("tripinly.kmp.library")
        pluginManager.apply("tripinly.kmp.compose")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                implementation(project(":core:common"))
                implementation(project(":core:model"))
                implementation(project(":core:designsystem"))

                implementation(libs.library("kotlinx-coroutines-core"))
                implementation(libs.library("kotlinx-datetime"))
                implementation(libs.library("kotlinx-collections-immutable"))
                implementation(libs.library("koin-core"))
                implementation(libs.library("koin-compose"))
                implementation(libs.library("koin-compose-viewmodel"))
                implementation(libs.library("androidx-lifecycle-viewmodelCompose"))
                implementation(libs.library("androidx-lifecycle-runtimeCompose"))
                implementation(libs.library("compose-material-icons-extended"))
            }
        }

        configurations.configureEach {
            dependencies.withType<ProjectDependency>().configureEach {
                check(!path.startsWith(":feature:") || path == project.path) {
                    "${project.path} must not depend on $path: features are independent."
                }
            }
        }
    }
}

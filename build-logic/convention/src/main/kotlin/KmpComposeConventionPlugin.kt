import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Compose Multiplatform on top of `tripinly.kmp.library`: compiler plugin, UI libraries,
 * the shared stability config, and a module-specific package for the generated `Res` class.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<KotlinMultiplatformExtension> {
            // Compose resources are packaged as Android resources/assets, which the KMP library plugin disables by default.
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
                androidResources {
                    enable = true
                }
            }

            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.library("compose-runtime"))
                implementation(libs.library("compose-foundation"))
                implementation(libs.library("compose-material3"))
                implementation(libs.library("compose-ui"))
                implementation(libs.library("compose-components-resources"))
                implementation(libs.library("compose-uiToolingPreview"))
            }
        }

        extensions.configure<ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(
                rootProject.layout.projectDirectory.file("compose-stability.conf"),
            )
        }

        extensions.getByType<ComposeExtension>().extensions.configure<ResourcesExtension> {
            packageOfResClass = "$moduleNamespace.generated.resources"
        }

        configurations.matching { it.name == "androidRuntimeClasspath" }.configureEach {
            dependencies.add(project.dependencies.create(libs.library("compose-uiTooling").get()))
        }
    }
}

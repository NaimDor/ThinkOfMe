import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        val android = extensions.getByName("android") as CommonExtension
        android.buildFeatures.compose = true
        dependencies {
            val bom = libs.lib("compose-bom")
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", libs.lib("compose-ui"))
            add("implementation", libs.lib("compose-ui-graphics"))
            add("implementation", libs.lib("compose-foundation"))
            add("implementation", libs.lib("compose-animation"))
            add("implementation", libs.lib("compose-material3"))
            add("implementation", libs.lib("compose-ui-tooling-preview"))
            add("debugImplementation", libs.lib("compose-ui-tooling"))
        }
    }
}

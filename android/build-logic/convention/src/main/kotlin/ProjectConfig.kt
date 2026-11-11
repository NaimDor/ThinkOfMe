import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal object ProjectConfig {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val TARGET_SDK = 36
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = ProjectConfig.COMPILE_SDK
        defaultConfig.minSdk = ProjectConfig.MIN_SDK
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-Xannotation-default-target=param-property",
            )
        }
    }
}

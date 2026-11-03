package io.github.ehb54.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** An integer from the [versions] table, e.g. the SDK levels. */
internal fun VersionCatalog.intVersion(name: String): Int =
    findVersion(name).get().requiredVersion.toInt()

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

/** A -P or gradle.properties value, or [default] when it is not set. */
internal fun Project.stringProperty(name: String, default: String): String =
    providers.gradleProperty(name).orNull ?: default

internal fun Project.requiredProperty(name: String): String =
    providers.gradleProperty(name).orNull
        ?: error("Gradle property '$name' is required when uploadStoreFile is set (see CLAUDE.md, Signing)")

/** Compose UI, Material 3, previews and JUnit: what every module in this repo uses. */
internal fun Project.addComposeDependencies() {
    dependencies {
        val bom = platform(libs.library("androidx-compose-bom"))
        add("implementation", bom)
        add("implementation", libs.library("androidx-compose-ui"))
        add("implementation", libs.library("androidx-compose-material3"))
        add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
        add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
        add("testImplementation", libs.library("junit"))
    }
}

package io.github.ehb54.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Plain Kotlin on the JVM, for shared/ and desktop/ modules: Java 17 bytecode
 * (what Android apps compile to, so shared code works in both), JUnit, and the
 * unitTests task the build recipe runs.
 */
internal fun Project.configureKotlinJvm() {
    pluginManager.apply("org.jetbrains.kotlin.jvm")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    // shared/quiz, desktop/quiz and apps/quiz are all "quiz": name the jars apart,
    // or a desktop app would bundle two quiz.jar files.
    extensions.configure<BasePluginExtension> {
        archivesName.set("${parent?.name}-$name")
    }
    dependencies {
        add("testImplementation", libs.library("junit"))
    }
    registerUnitTests("test")
}

/** Android lint for plain Kotlin modules that Android apps use (it then checks them as part of the app). */
internal fun Project.configureJvmLint() {
    pluginManager.apply("com.android.lint")
    extensions.configure<Lint> {
        abortOnError = true
        lintConfig = rootDir.resolve("lint.xml")
    }
}

/**
 * `unitTests` runs a module's own unit tests, whatever the module kind calls
 * them: testDebugUnitTest for Android modules, test for JVM ones. It is what
 * tools/ci-build.sh asks for.
 */
internal fun Project.registerUnitTests(testTask: String) {
    tasks.register("unitTests") {
        group = "verification"
        description = "Runs this module's unit tests ($testTask)."
        dependsOn(testTask)
    }
}

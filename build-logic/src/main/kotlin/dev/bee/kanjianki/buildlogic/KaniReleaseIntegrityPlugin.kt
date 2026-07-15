package dev.bee.kanjianki.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class KaniReleaseIntegrityExtension {
    abstract val versionName: Property<String>
    abstract val versionCode: Property<Int>
    abstract val versionSource: Property<String>
    abstract val signingStoreFileConfigured: Property<Boolean>
    abstract val signingStoreFilePath: Property<String>
    abstract val signingStorePasswordConfigured: Property<Boolean>
    abstract val signingKeyAliasConfigured: Property<Boolean>
    abstract val signingKeyPasswordConfigured: Property<Boolean>
}

abstract class PrintKaniVersionTask : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    abstract val versionSource: Property<String>

    @TaskAction
    fun printVersion() {
        println(
            "{\"versionName\":\"${versionName.get()}\",\"versionCode\":${versionCode.get()}," +
                "\"source\":\"${versionSource.get()}\"}",
        )
    }
}

abstract class ValidateKaniReleaseSigningTask : DefaultTask() {
    @get:Input
    abstract val storeFileConfigured: Property<Boolean>

    @get:Internal
    abstract val storeFilePath: Property<String>

    @get:Input
    abstract val storePasswordConfigured: Property<Boolean>

    @get:Input
    abstract val keyAliasConfigured: Property<Boolean>

    @get:Input
    abstract val keyPasswordConfigured: Property<Boolean>

    @TaskAction
    fun validateSigning() {
        val missing = buildList {
            if (!storeFileConfigured.get()) add("KANI_SIGNING_STORE_FILE")
            if (!storePasswordConfigured.get()) add("KANI_SIGNING_STORE_PASSWORD")
            if (!keyAliasConfigured.get()) add("KANI_SIGNING_KEY_ALIAS")
            if (!keyPasswordConfigured.get()) add("KANI_SIGNING_KEY_PASSWORD")
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing is required. Set ${missing.joinToString(", ")} " +
                    "(the legacy KANJI_ANKI_* names remain supported).",
            )
        }

        // The app supplies an absolute path during configuration. Resolve it
        // without Task.project here so this validation remains compatible with
        // Gradle's configuration cache at execution time.
        val storeFile = java.io.File(storeFilePath.get())
        if (!storeFile.isFile) {
            throw GradleException("Release signing keystore does not exist or is not a file: $storeFile")
        }
    }
}

object KaniReleaseTaskPolicy {
    private val artifactTasks = setOf(
        "packageRelease",
        "packageReleaseBundle",
        "packageReleaseUniversalApk",
        "signReleaseBundle",
    )

    fun requiresSigning(taskName: String): Boolean = taskName in artifactTasks
}

class KaniReleaseIntegrityPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "kaniReleaseIntegrity",
            KaniReleaseIntegrityExtension::class.java,
        )

        project.tasks.register("printKaniVersion", PrintKaniVersionTask::class.java) {
            group = "help"
            description = "Prints resolved Kani Android version metadata as one JSON object."
            versionName.convention(extension.versionName)
            versionCode.convention(extension.versionCode)
            versionSource.convention(extension.versionSource)
        }

        val validateSigning = project.tasks.register(
            "validateKaniReleaseSigning",
            ValidateKaniReleaseSigningTask::class.java,
        ) {
            group = "verification"
            description = "Fails before release packaging when complete signing credentials are unavailable."
            storeFileConfigured.convention(extension.signingStoreFileConfigured)
            storeFilePath.convention(extension.signingStoreFilePath)
            storePasswordConfigured.convention(extension.signingStorePasswordConfigured)
            keyAliasConfigured.convention(extension.signingKeyAliasConfigured)
            keyPasswordConfigured.convention(extension.signingKeyPasswordConfigured)
        }

        // Wire the validation into artifact-producing AGP tasks. This follows the
        // selected task graph (including aggregate tasks such as root ciRelease)
        // instead of trying to infer intent from Gradle's command-line task names.
        project.tasks.configureEach {
            if (KaniReleaseTaskPolicy.requiresSigning(name)) {
                dependsOn(validateSigning)
            }
        }
    }
}

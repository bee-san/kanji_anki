package dev.bee.kanjianki.core

import java.io.File

/**
 * Regenerates golden files on request: run
 * `./gradlew :core:test -PupdateSchedulerGoldens` after a deliberate scheduler
 * or memory-model change, then review the diff before committing.
 */
internal object SchedulerGoldens {
    fun updateIfRequested(relativePath: String, actual: String) {
        val root = System.getProperty("kani.updateSchedulerGoldens") ?: return
        val file = File(root, relativePath)
        file.parentFile.mkdirs()
        file.writeText(actual.trimEnd() + "\n")
    }
}

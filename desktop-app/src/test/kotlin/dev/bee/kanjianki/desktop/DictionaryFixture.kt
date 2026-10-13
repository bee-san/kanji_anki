package dev.bee.kanjianki.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Provides a real dictionary database for the asset-reader tests.
 *
 * A copy of the shipped `kanji_dictionary.db` rather than a hand-built table, for two
 * reasons. It is the actual schema, so a reader that works here works against what users
 * install. And `:desktop-app` cannot name `:data-sql` types — that module is an
 * `implementation` dependency of `:data-desktop` and the encapsulation is deliberate — so
 * a fixture that issued `CREATE TABLE` would need the module graph widened for a test.
 *
 * [seed] returns false when the checked-in asset is not present, so a caller can skip
 * rather than fail: the assertion those tests make is about the readers, and a missing
 * repo asset is a different problem that its own generator test already covers.
 */
internal object DictionaryFixture {
    fun seed(databaseFile: Path): Boolean {
        val source = shippedDictionary() ?: return false
        Files.createDirectories(databaseFile.parent)
        Files.copy(source, databaseFile, StandardCopyOption.REPLACE_EXISTING)
        return true
    }

    /** The repo's own dictionary asset, located from the module's working directory. */
    private fun shippedDictionary(): Path? {
        val candidates = listOf(
            Path.of("..", "app", "src", "main", "assets", "dictionaries", "kanji_dictionary.db"),
            Path.of("app", "src", "main", "assets", "dictionaries", "kanji_dictionary.db"),
        )
        return candidates.firstOrNull { Files.isRegularFile(it) }?.toAbsolutePath()
    }
}

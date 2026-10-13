package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.JitenKanjiRanks
import dev.bee.kanjianki.core.ReadingExposureModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.data.desktop.DesktopDictionaryStore
import dev.bee.kanjianki.sync.SyncAssetReaders
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop's [SyncAssetReaders]: the four reference assets a sync reads.
 *
 * This is what the desktop Missing Kanji report was waiting on. The report needs eligible
 * candidates from a [DictionaryLookup], and the desktop container previously supplied
 * none — so the route could only ever show FirstRun or an empty report.
 *
 * Each reader degrades independently, and that separation is the design. A sync uses these
 * for different things: ranks order new cards, the dictionary supplies meanings and
 * candidates, similar-kanji drives the discrimination rung, and reading exposure feeds
 * contextual scheduling. A portable install missing the stroke data should still sync and
 * still study — so a missing asset yields that asset's documented empty value rather than
 * failing the sync that needed only the other three.
 *
 * `loadRanks` and `loadSimilarKanjiIndex` are declared `@Throws(IOException)` by the
 * contract, and this implementation deliberately does not throw: the Android reader can,
 * because on Android a missing bundled asset means a corrupt install, whereas on desktop
 * it means the reference assets were not unpacked — a supported state with defined
 * behaviour. Returning empty is the honest answer, not a swallowed error.
 */
internal class DesktopSyncAssetReaders(
    private val dictionary: DictionaryLookup,
    private val similarKanjiFile: Path?,
    private val readingExposure: () -> ReadingExposureModels.ExposureIndex = {
        ReadingExposureModels.ExposureIndex.EMPTY
    },
) : SyncAssetReaders {
    /**
     * Jiten ranks, read from the dictionary database rather than a separate CSV.
     *
     * The shipped dictionary carries a `jiten_ranks` table, and Android's store reads
     * ranks from there too. Parsing the standalone CSV as well would create a second
     * source of truth for the same numbers, and the two could disagree after an update
     * that refreshed one file and not the other.
     */
    override fun loadRanks(): JitenKanjiRanks = dictionary.jitenRanks()

    override fun loadDictionary(): DictionaryLookup? {
        // Null, not an empty lookup: the contract's return type is nullable precisely so a
        // caller can tell "no dictionary" from "a dictionary with no match", and the
        // Missing Kanji report renders those differently — no assets versus no candidates.
        val absent = (dictionary as? DesktopDictionaryStore)?.absent ?: false
        return if (absent) null else dictionary
    }

    override fun loadSimilarKanjiIndex(): SimilarKanjiIndex {
        val file = similarKanjiFile ?: return SimilarKanjiIndex.empty()
        if (!Files.isRegularFile(file)) return SimilarKanjiIndex.empty()
        return runCatching {
            InputStreamReader(
                Files.newInputStream(file),
                StandardCharsets.UTF_8,
            ).use { reader -> SimilarKanjiIndex.parseTsv(reader) }
        }.getOrDefault(SimilarKanjiIndex.empty())
    }

    /**
     * Reading exposure, which on desktop comes from the Anki collection's own media.
     *
     * Injected rather than read here because it is the one asset that is not shipped
     * content: Android reads it out of the collection's media directory, and desktop's
     * equivalent is a provider concern. Defaulting to `EMPTY` keeps contextual scheduling
     * off rather than wrong until that source is wired.
     */
    override fun loadReadingExposure(): ReadingExposureModels.ExposureIndex =
        runCatching(readingExposure).getOrDefault(ReadingExposureModels.ExposureIndex.EMPTY)

    companion object {
        /**
         * The reference-asset layout inside an installed image or a profile.
         *
         * Both are checked, in that order, because they answer different questions: the
         * install directory holds what the packager shipped, and the profile holds what a
         * user unpacked into a portable install. Preferring the install keeps a stale
         * hand-placed copy from shadowing the packaged one after an update.
         */
        fun forProfile(
            profileDir: Path,
            installReferenceDir: Path? = null,
        ): DesktopSyncAssetReaders {
            val dictionaryFile = firstExisting(
                installReferenceDir?.resolve(DICTIONARY_FILE_NAME),
                profileDir.resolve(REFERENCE_DIR_NAME).resolve(DICTIONARY_FILE_NAME),
            )
            val similarKanji = firstExisting(
                installReferenceDir?.resolve(SIMILAR_KANJI_FILE_NAME),
                profileDir.resolve(REFERENCE_DIR_NAME).resolve(SIMILAR_KANJI_FILE_NAME),
            )
            return DesktopSyncAssetReaders(
                dictionary = dictionaryFile
                    ?.let { DesktopDictionaryStore.open(it) }
                    ?: DesktopDictionaryStore.absent(),
                similarKanjiFile = similarKanji,
            )
        }

        private fun firstExisting(vararg candidates: Path?): Path? =
            candidates.filterNotNull().firstOrNull { Files.isRegularFile(it) }

        internal const val REFERENCE_DIR_NAME = "reference"
        internal const val DICTIONARY_FILE_NAME = "kanji_dictionary.db"
        internal const val SIMILAR_KANJI_FILE_NAME = "similar_kanji.tsv"
    }
}

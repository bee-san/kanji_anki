package dev.bee.kanjianki.assets

/**
 * The cross-platform reference-asset contract. Both packaged hosts (Android and
 * the desktop installed image) load the same verified set of dictionary, rank,
 * stroke-guide, and study-font assets from this manifest.
 *
 * The manifest is code-defined rather than parsed at runtime so it stays
 * dependency-free and cannot drift from a separate descriptor file: every entry
 * carries its expected content hash, format/cache version, extraction target,
 * and a license/attribution record. Placeholder hashes are used until the real
 * licensed binaries are sourced; [bundled] documents each entry's provenance.
 */
data class ReferenceAssetManifest(
    val schemaVersion: Int,
    val assets: List<ReferenceAsset>,
) {
    init {
        require(schemaVersion > 0) { "manifest schema version must be positive" }
        require(assets.isNotEmpty()) { "manifest must declare at least one asset" }
        require(assets.map(ReferenceAsset::id).toSet().size == assets.size) {
            "duplicate reference-asset id"
        }
        require(assets.map(ReferenceAsset::fileName).toSet().size == assets.size) {
            "duplicate reference-asset file name"
        }
    }

    fun byId(id: String): ReferenceAsset? = assets.firstOrNull { it.id == id }

    fun byFileName(fileName: String): ReferenceAsset? = assets.firstOrNull { it.fileName == fileName }

    companion object {
        const val SCHEMA_VERSION: Int = 1

        // 64 zero hex digits marks an entry whose real licensed binary has not
        // been sourced yet. ReferenceAssetVerifier treats a placeholder hash as
        // "accept any non-empty content" so the loader/extraction/cache-upgrade
        // paths are testable before the binaries land, without ever silently
        // accepting a corrupt real asset (a real 64-hex hash is enforced).
        const val PLACEHOLDER_SHA256: String =
            "0000000000000000000000000000000000000000000000000000000000000000"

        /**
         * The bundled manifest. Kinds and license records are final; the SHA-256
         * values are placeholders until the licensed binaries are checked in.
         */
        fun bundled(): ReferenceAssetManifest =
            ReferenceAssetManifest(
                schemaVersion = SCHEMA_VERSION,
                assets = listOf(
                    ReferenceAsset(
                        id = "kanji_dictionary",
                        kind = ReferenceAssetKind.DICTIONARY_DATABASE,
                        fileName = "kanji_dictionary.db",
                        expectedSha256 = PLACEHOLDER_SHA256,
                        formatVersion = 2,
                        extractionTarget = "reference/kanji_dictionary.db",
                        license = ReferenceAssetLicense(
                            name = "KANJIDIC2",
                            spdxOrName = "CC BY-SA 4.0 via EDRDG licence",
                            url = "https://www.edrdg.org/edrdg/licence.html",
                            attribution = "Electronic Dictionary Research and Development Group",
                        ),
                    ),
                    ReferenceAsset(
                        id = "jiten_kanji_rank",
                        kind = ReferenceAssetKind.FREQUENCY_RANKS,
                        fileName = "jiten_kanji_rank.csv",
                        expectedSha256 = PLACEHOLDER_SHA256,
                        formatVersion = 1,
                        extractionTarget = "reference/jiten_kanji_rank.csv",
                        license = ReferenceAssetLicense(
                            name = "Jiten kanji frequency ranks",
                            spdxOrName = "See documentation/dictionary_sources.md",
                            url = null,
                            attribution = "Jiten",
                        ),
                    ),
                    ReferenceAsset(
                        id = "kanjivg_strokes",
                        kind = ReferenceAssetKind.STROKE_GUIDES,
                        fileName = "kanjivg_strokes.db",
                        expectedSha256 = PLACEHOLDER_SHA256,
                        formatVersion = 1,
                        extractionTarget = "reference/kanjivg_strokes.db",
                        license = ReferenceAssetLicense(
                            name = "KanjiVG",
                            spdxOrName = "CC BY-SA 3.0",
                            url = "https://github.com/KanjiVG/kanjivg",
                            attribution = "Ulrich Apel and the KanjiVG project",
                        ),
                    ),
                    ReferenceAsset(
                        id = "study_font",
                        kind = ReferenceAssetKind.STUDY_FONT,
                        fileName = "study_font.otf",
                        expectedSha256 = PLACEHOLDER_SHA256,
                        formatVersion = 1,
                        extractionTarget = "reference/study_font.otf",
                        license = ReferenceAssetLicense(
                            name = "Study font",
                            spdxOrName = "SIL Open Font License 1.1",
                            url = "https://openfontlicense.org",
                            attribution = "Bundled study typeface",
                        ),
                    ),
                ),
            )
    }
}

enum class ReferenceAssetKind {
    DICTIONARY_DATABASE,
    FREQUENCY_RANKS,
    STROKE_GUIDES,
    STUDY_FONT,
}

/**
 * One packaged asset. [extractionTarget] is the host-relative path the verified
 * bytes are installed to; the platform loader resolves it against its install
 * directory. [formatVersion] drives the cache-upgrade decision.
 */
data class ReferenceAsset(
    val id: String,
    val kind: ReferenceAssetKind,
    val fileName: String,
    val expectedSha256: String,
    val formatVersion: Int,
    val extractionTarget: String,
    val license: ReferenceAssetLicense,
) {
    init {
        require(id.isNotBlank()) { "asset id must not be blank" }
        require(fileName.isNotBlank()) { "asset file name must not be blank" }
        require(extractionTarget.isNotBlank()) { "asset extraction target must not be blank" }
        require(formatVersion > 0) { "asset format version must be positive" }
        require(SHA256_HEX.matches(expectedSha256)) {
            "asset expectedSha256 must be 64 lowercase hex digits"
        }
    }

    /** True when the hash is the all-zero placeholder for a not-yet-sourced binary. */
    fun hasPlaceholderHash(): Boolean = expectedSha256 == ReferenceAssetManifest.PLACEHOLDER_SHA256
}

private val SHA256_HEX = Regex("[0-9a-f]{64}")

data class ReferenceAssetLicense(
    val name: String,
    val spdxOrName: String,
    val url: String?,
    val attribution: String,
) {
    init {
        require(name.isNotBlank()) { "license name must not be blank" }
        require(spdxOrName.isNotBlank()) { "license identifier must not be blank" }
        require(attribution.isNotBlank()) { "license attribution must not be blank" }
    }
}

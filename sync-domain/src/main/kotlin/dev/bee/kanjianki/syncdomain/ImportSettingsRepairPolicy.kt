package dev.bee.kanjianki.syncdomain

import java.util.regex.Pattern
import kotlin.math.abs

class ImportSettingsRepairPolicy private constructor() {
    class StoredImportSettings {
        private var importActiveCards: Int? = null
        private var importSuspendedCards: Int? = null
        private var importTaggedCards: Int? = null
        private var importTags: String? = null
        private var importWeakCards: Int? = null
        private var importWeakFsrsDifficulty: Double? = null
        private var importWeakLapses: Int? = null
        private var importMinMatchingCards: Int? = null

        fun importActiveCards(importActiveCards: Int?): StoredImportSettings {
            this.importActiveCards = importActiveCards
            return this
        }

        fun importSuspendedCards(importSuspendedCards: Int?): StoredImportSettings {
            this.importSuspendedCards = importSuspendedCards
            return this
        }

        fun importTaggedCards(importTaggedCards: Int?): StoredImportSettings {
            this.importTaggedCards = importTaggedCards
            return this
        }

        fun importTags(importTags: String?): StoredImportSettings {
            this.importTags = importTags
            return this
        }

        fun importWeakCards(importWeakCards: Int?): StoredImportSettings {
            this.importWeakCards = importWeakCards
            return this
        }

        fun importWeakFsrsDifficulty(importWeakFsrsDifficulty: Double?): StoredImportSettings {
            this.importWeakFsrsDifficulty = importWeakFsrsDifficulty
            return this
        }

        fun importWeakLapses(importWeakLapses: Int?): StoredImportSettings {
            this.importWeakLapses = importWeakLapses
            return this
        }

        fun importMinMatchingCards(importMinMatchingCards: Int?): StoredImportSettings {
            this.importMinMatchingCards = importMinMatchingCards
            return this
        }

        internal fun hasAnyImportSetting(): Boolean {
            return importActiveCards != null ||
                importSuspendedCards != null ||
                importTaggedCards != null ||
                importTags != null ||
                importWeakCards != null ||
                importWeakFsrsDifficulty != null ||
                importWeakLapses != null ||
                importMinMatchingCards != null
        }

        internal fun matchesOldDefaultImportSettings(
            expectedWeakFsrsDifficulty: Double,
            expectedWeakLapses: Int,
            expectedMinMatchingCards: Int,
        ): Boolean {
            return intMatchesOrAbsent(importActiveCards, OLD_DEFAULT_IMPORT_ACTIVE_CARDS) &&
                intMatchesOrAbsent(importSuspendedCards, 1) &&
                intMatchesOrAbsent(importTaggedCards, 0) &&
                importTagsEmptyOrAbsent(importTags) &&
                intMatchesOrAbsent(importWeakCards, 0) &&
                doubleMatchesOrAbsent(importWeakFsrsDifficulty, expectedWeakFsrsDifficulty) &&
                intMatchesOrAbsent(importWeakLapses, expectedWeakLapses) &&
                intMatchesOrAbsent(importMinMatchingCards, expectedMinMatchingCards)
        }
    }

    class RepairDecision private constructor(
        private val shouldRepair: Boolean,
        private val importActiveCards: Int,
        private val importSuspendedCards: Int,
    ) {
        fun shouldRepair(): Boolean = shouldRepair
        fun importActiveCards(): Int = importActiveCards
        fun importSuspendedCards(): Int = importSuspendedCards

        companion object {
            fun none(): RepairDecision = RepairDecision(false, 0, 1)
            fun suspendedOnly(): RepairDecision = RepairDecision(true, 0, 1)
        }
    }

    companion object {
        private val IMPORT_TAG_SEPARATOR = Pattern.compile("[,\\s]+")
        private const val OLD_DEFAULT_IMPORT_ACTIVE_CARDS = 1

        @JvmStatic
        fun oldDefaultRepair(
            settings: StoredImportSettings?,
            expectedWeakFsrsDifficulty: Double,
            expectedWeakLapses: Int,
            expectedMinMatchingCards: Int,
        ): RepairDecision {
            val safeSettings = settings ?: StoredImportSettings()
            if (!safeSettings.hasAnyImportSetting() ||
                !safeSettings.matchesOldDefaultImportSettings(
                    expectedWeakFsrsDifficulty,
                    expectedWeakLapses,
                    expectedMinMatchingCards
                )
            ) {
                return RepairDecision.none()
            }
            return RepairDecision.suspendedOnly()
        }

        private fun intMatchesOrAbsent(value: Int?, expected: Int): Boolean = value == null || value == expected

        private fun doubleMatchesOrAbsent(value: Double?, expected: Double): Boolean {
            return value == null || abs(value - expected) < 0.0001
        }

        private fun importTagsEmptyOrAbsent(value: String?): Boolean {
            return value == null || javaTrim(value).isEmpty() || !hasImportTags(value)
        }

        private fun hasImportTags(value: String): Boolean {
            for (part in IMPORT_TAG_SEPARATOR.split(javaTrim(value))) {
                if (javaTrim(part).isNotEmpty()) {
                    return true
                }
            }
            return false
        }

        private fun javaTrim(value: String?): String {
            return value?.trim { it <= ' ' } ?: ""
        }
    }
}

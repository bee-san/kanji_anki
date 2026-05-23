package dev.bee.kanjianki.core

import java.util.Collections

data class SettingsImportPreset(
    val label: String,
    val activeCards: Boolean,
    val suspendedCards: Boolean,
    val taggedCards: Boolean,
    val tags: String,
    val weakCards: Boolean,
    val weakDifficulty: Double,
    val weakLapses: Int,
    val minMatchingCards: Int,
    val browserQueryCards: Boolean,
    val browserQuery: String,
) {
    fun label(): String = label

    fun activeCards(): Boolean = activeCards

    fun suspendedCards(): Boolean = suspendedCards

    fun taggedCards(): Boolean = taggedCards

    fun tags(): String = tags

    fun weakCards(): Boolean = weakCards

    fun weakDifficulty(): Double = weakDifficulty

    fun weakLapses(): Int = weakLapses

    fun minMatchingCards(): Int = minMatchingCards

    fun browserQueryCards(): Boolean = browserQueryCards

    fun browserQuery(): String = browserQuery

    companion object {
        private val DEFAULTS: List<SettingsImportPreset> = Collections.unmodifiableList(
            listOf(
                SettingsImportPreset("Suspended only", false, true, false, "", false, 7.0, 2, 1, false, ""),
                SettingsImportPreset("Kani tag", false, false, true, "kani", false, 7.0, 2, 1, false, ""),
                SettingsImportPreset("Leech tag", false, false, true, "leech", false, 7.0, 2, 1, false, ""),
                SettingsImportPreset("Mining deck", false, false, false, "", false, 7.0, 2, 1, true, "deck:Mining"),
                SettingsImportPreset("Recent fails", false, false, false, "", false, 7.0, 2, 1, true, "rated:30:1"),
            )
        )

        @JvmStatic
        fun defaults(): List<SettingsImportPreset> = DEFAULTS

        @JvmStatic
        fun boolFlag(value: Boolean): Int = if (value) 1 else 0
    }
}

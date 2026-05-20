package dev.bee.kanjianki

import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsAnkiSourceValidation(
    private val activity: MainActivitySettings,
) {
    fun readImportThresholds(
        difficultyInput: EditText,
        lapses: EditText,
        minMatching: EditText,
    ): MainActivityBase.ImportThresholds? {
        return readImportThresholds(
            difficultyInput.text.toString(),
            lapses.text.toString(),
            minMatching.text.toString(),
        )
    }

    fun readImportThresholds(
        difficultyInput: String,
        lapses: String,
        minMatching: String,
    ): MainActivityBase.ImportThresholds? {
        val difficulty: Double
        val lapseThreshold: Int
        val minCards: Int
        try {
            difficulty = parseDecimalText(difficultyInput)
            lapseThreshold = parseThresholdText(lapses)
            minCards = parseThresholdText(minMatching)
        } catch (_: NumberFormatException) {
            Toast.makeText(activity, SettingsTextCopy.numericImportThresholdsToast(), Toast.LENGTH_SHORT).show()
            return null
        }
        if (!SettingsInputRules.validImportThresholds(difficulty, lapseThreshold, minCards)) {
            Toast.makeText(activity, SettingsTextCopy.importThresholdRangeToast(), Toast.LENGTH_SHORT).show()
            return null
        }
        return MainActivityBase.ImportThresholds(difficulty, lapseThreshold, minCards)
    }

    fun hasSelectedImportSource(
        activeCards: Boolean,
        suspendedCards: Boolean,
        taggedCards: Boolean,
        weakCards: Boolean,
        browserQueryCards: Boolean,
        parsedTags: List<String>?,
        queryText: String?,
    ): Boolean {
        if (activeCards) {
            return SettingsInputRules.hasSelectedImportSource(true, false, false, false, false, null, null)
        }
        if (suspendedCards) {
            return SettingsInputRules.hasSelectedImportSource(false, true, false, false, false, null, null)
        }
        if (weakCards) {
            return SettingsInputRules.hasSelectedImportSource(false, false, false, true, false, null, null)
        }
        if (
            taggedCards &&
            SettingsInputRules.hasSelectedImportSource(false, false, true, false, false, parsedTags, "")
        ) {
            return true
        }
        return SettingsInputRules.hasSelectedImportSource(
            false,
            false,
            false,
            false,
            browserQueryCards,
            emptyList(),
            queryText,
        )
    }

    fun hasSelectedImportSource(
        activeCards: CheckBox?,
        suspendedCards: CheckBox?,
        taggedCards: CheckBox?,
        weakCards: CheckBox?,
        browserQueryCards: CheckBox?,
        parsedTags: List<String>?,
        queryText: String?,
    ): Boolean {
        return hasSelectedImportSource(
            activeCards?.isChecked == true,
            suspendedCards?.isChecked == true,
            taggedCards?.isChecked == true,
            weakCards?.isChecked == true,
            browserQueryCards?.isChecked == true,
            parsedTags,
            queryText,
        )
    }

    fun parseRankText(input: String): Int {
        return input.trim().toInt()
    }

    fun parseDecimalInput(input: EditText): Double {
        return parseDecimalText(input.text.toString())
    }

    private fun parseThresholdText(input: String): Int {
        return input.trim().toInt()
    }

    private fun parseDecimalText(input: String): Double {
        return input.trim().toDouble()
    }
}

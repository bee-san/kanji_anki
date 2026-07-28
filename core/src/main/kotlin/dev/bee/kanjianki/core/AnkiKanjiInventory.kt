package dev.bee.kanjianki.core

import java.util.Collections
import java.util.LinkedHashSet
import kotlin.math.ceil

/**
 * Aggregate-only result of scanning an Anki collection.
 *
 * Note text is consumed by [AnkiKanjiInventoryCollector] and is never retained
 * in this value.
 */
data class AnkiKanjiInventory(
    val literals: Set<String>,
    val notesScanned: Int,
    val fieldsScanned: Int,
    val skippedNotes: Int,
    val modelCount: Int,
    val malformedRowWarning: MalformedRowWarning?,
) {
    val uniqueKanjiCount: Int
        get() = literals.size

    init {
        require(notesScanned >= 0) { "notesScanned must not be negative." }
        require(fieldsScanned >= 0) { "fieldsScanned must not be negative." }
        require(skippedNotes >= 0) { "skippedNotes must not be negative." }
        require(modelCount >= 0) { "modelCount must not be negative." }
    }

    data class MalformedRowWarning(
        val skippedNotes: Int,
        val warningThreshold: Int,
    )
}

data class AnkiKanjiInventoryProgress(
    val notesScanned: Int,
    val uniqueKanjiCount: Int,
    val skippedNotes: Int,
    val totalNotes: Int? = null,
) {
    val isIndeterminate: Boolean
        get() = totalNotes == null
}

/**
 * Stateful, single-scan collector. Callers add one normalized field at a time
 * and discard the field immediately after this method returns.
 */
class AnkiKanjiInventoryCollector {
    private val literals = LinkedHashSet<String>()
    private var fieldsScanned = 0

    fun addNormalizedField(value: String?) {
        fieldsScanned += 1
        literals.addAll(TextUtil.extractKanji(value))
    }

    fun uniqueKanjiCount(): Int = literals.size

    fun finish(
        notesScanned: Int,
        skippedNotes: Int,
        modelCount: Int,
    ): AnkiKanjiInventory {
        val immutableLiterals = Collections.unmodifiableSet(LinkedHashSet(literals))
        return AnkiKanjiInventory(
            literals = immutableLiterals,
            notesScanned = notesScanned,
            fieldsScanned = fieldsScanned,
            skippedNotes = skippedNotes,
            modelCount = modelCount,
            malformedRowWarning = malformedWarning(notesScanned, skippedNotes),
        )
    }

    companion object {
        private const val MAX_WARNING_THRESHOLD = 100
        private const val MALFORMED_WARNING_FRACTION = 0.01

        @JvmStatic
        fun warningThreshold(rowsSeen: Int): Int {
            val boundedRows = rowsSeen.coerceAtLeast(0)
            val onePercent = ceil(boundedRows * MALFORMED_WARNING_FRACTION).toInt()
            return onePercent.coerceIn(1, MAX_WARNING_THRESHOLD)
        }

        private fun malformedWarning(
            notesScanned: Int,
            skippedNotes: Int,
        ): AnkiKanjiInventory.MalformedRowWarning? {
            if (skippedNotes <= 0) {
                return null
            }
            val threshold = warningThreshold(notesScanned + skippedNotes)
            return if (skippedNotes >= threshold) {
                AnkiKanjiInventory.MalformedRowWarning(skippedNotes, threshold)
            } else {
                null
            }
        }
    }
}

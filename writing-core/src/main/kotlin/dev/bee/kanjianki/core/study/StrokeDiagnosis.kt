package dev.bee.kanjianki.core.study

import java.util.ArrayList
import java.util.Collections

class StrokeDiagnosis private constructor(entries: List<Entry>) {
    enum class Label {
        WRONG_ORDER,
        WRONG_DIRECTION,
        MISSING_STROKE,
        EXTRA_STROKE,
        ROUGH_SHAPE,
        FAR_FROM_GUIDE,
        CONFUSED_WITH_SIMILAR_KANJI,
        RECOGNIZED_BUT_MESSY,
    }

    @JvmField val entries: List<Entry> = Collections.unmodifiableList(ArrayList(entries))

    fun isEmpty(): Boolean = entries.isEmpty()

    fun hasLabel(label: Label?): Boolean {
        for (entry in entries) {
            if (entry.label == label) {
                return true
            }
        }
        return false
    }

    fun hasLabel(label: Label?, strokeNumber: Int): Boolean {
        for (entry in entries) {
            if (entry.label == label && entry.strokeNumber == strokeNumber) {
                return true
            }
        }
        return false
    }

    fun plus(label: Label?, strokeNumber: Int): StrokeDiagnosis {
        val builder = builder()
        for (entry in entries) {
            builder.add(entry.label, entry.strokeNumber)
        }
        builder.add(label, strokeNumber)
        return builder.build()
    }

    class Entry private constructor(
        @JvmField val label: Label,
        strokeNumber: Int,
    ) {
        @JvmField val strokeNumber: Int = maxOf(0, strokeNumber)

        companion object {
            fun create(label: Label, strokeNumber: Int): Entry = Entry(label, strokeNumber)
        }
    }

    class Builder {
        private val entries = ArrayList<Entry>()

        fun add(label: Label?, strokeNumber: Int): Builder {
            if (label == null) {
                return this
            }
            val safeStrokeNumber = maxOf(0, strokeNumber)
            for (entry in entries) {
                if (entry.label == label && entry.strokeNumber == safeStrokeNumber) {
                    return this
                }
            }
            entries.add(Entry.create(label, safeStrokeNumber))
            return this
        }

        fun build(): StrokeDiagnosis = if (entries.isEmpty()) EMPTY else StrokeDiagnosis(entries)
    }

    companion object {
        private val EMPTY = StrokeDiagnosis(emptyList())

        @JvmStatic
        fun empty(): StrokeDiagnosis = EMPTY

        @JvmStatic
        fun builder(): Builder = Builder()
    }
}

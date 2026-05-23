package dev.bee.kanjianki.core

import java.util.Objects

class StudyCue(
    meaning: String?,
    reading: String?,
    fromExpression: String?,
    meaningSource: String?,
) {
    @JvmField val meaning: String = normalize(meaning)
    @JvmField val reading: String = normalize(reading)
    @JvmField val fromExpression: String = normalize(fromExpression)
    @JvmField val meaningSource: String = normalize(meaningSource)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is StudyCue) {
            return false
        }
        return meaning == other.meaning &&
            reading == other.reading &&
            fromExpression == other.fromExpression &&
            meaningSource == other.meaningSource
    }

    override fun hashCode(): Int = Objects.hash(meaning, reading, fromExpression, meaningSource)

    override fun toString(): String {
        return "StudyCue{" +
            "meaning='" + meaning + '\'' +
            ", reading='" + reading + '\'' +
            ", fromExpression='" + fromExpression + '\'' +
            ", meaningSource='" + meaningSource + '\'' +
            '}'
    }

    companion object {
        private fun normalize(value: String?): String = (value ?: "").trim()
    }
}

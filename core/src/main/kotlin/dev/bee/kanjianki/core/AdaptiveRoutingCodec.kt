package dev.bee.kanjianki.core

import java.lang.StringBuilder
import java.util.LinkedHashMap

/** Compact, versioned JSON for the `review_log.answer_evidence_json` column. */
object AnswerEvidenceCodec {
    private const val FORMAT_VERSION = 1L

    @JvmStatic
    fun encode(evidence: AnswerEvidence?): String {
        if (evidence == null) {
            return ""
        }
        return CompactJson.encodeObject(toJsonObject(evidence))
    }

    @JvmStatic
    fun decode(encoded: String?): AnswerEvidence? {
        val root = CompactJson.decodeObject(encoded) ?: return null
        return fromJsonObject(root)
    }

    internal fun toJsonObject(evidence: AnswerEvidence): Map<String, Any?> {
        val root = LinkedHashMap<String, Any?>()
        root["v"] = FORMAT_VERSION
        putWire(root, "c", evidence.coreSkill?.wireName())
        putWire(root, "f", evidence.failureKind?.wireName())
        putWire(root, "s", evidence.evidenceSource?.wireName())
        putWire(root, "p", evidence.presentationVariant?.wireName())
        putText(root, "a", evidence.selectedAnswer)
        putText(root, "z", evidence.correctAnswer)
        putText(root, "x", evidence.renderedExpression)
        putText(root, "r", evidence.renderedReading)
        putText(root, "w", evidence.confusedWith)
        return root
    }

    internal fun fromJsonObject(root: Map<String, Any?>): AnswerEvidence? {
        if (root.long("v") != FORMAT_VERSION) {
            return null
        }
        return AnswerEvidence(
            coreSkill = CoreSkill.fromWireName(root.string("c")),
            failureKind = FailureKind.fromWireName(root.string("f")),
            evidenceSource = EvidenceSource.fromWireName(root.string("s")),
            presentationVariant = PresentationVariant.fromWireName(root.string("p")),
            selectedAnswer = root.string("a").orEmpty(),
            correctAnswer = root.string("z").orEmpty(),
            renderedExpression = root.string("x").orEmpty(),
            renderedReading = root.string("r").orEmpty(),
            confusedWith = root.string("w").orEmpty(),
        )
    }

    private fun putWire(target: MutableMap<String, Any?>, key: String, value: String?) {
        if (!value.isNullOrEmpty()) {
            target[key] = value
        }
    }

    private fun putText(target: MutableMap<String, Any?>, key: String, value: String) {
        if (value.isNotEmpty()) {
            target[key] = value
        }
    }
}

/** Compact, versioned JSON for the `study_items.adaptive_route_state_json` column. */
object AdaptiveRouteStateCodec {
    private const val FORMAT_VERSION = 1L

    @JvmStatic
    fun encode(state: AdaptiveRouteState?): String {
        if (state == null) {
            return ""
        }
        val root = LinkedHashMap<String, Any?>()
        root["v"] = FORMAT_VERSION
        root["c"] = state.activeCore.wireName()
        putPositive(root, "rr", state.recognitionReviewCount)
        putPositive(root, "cr", state.contextualReadingReviewCount)
        putList(root, "t", state.activeRepairTasks)
        putPositive(root, "i", state.repairTaskIndex)
        putList(root, "m", state.repairStepMinutes)
        putPositive(root, "d", state.repairDueAtMillis)
        putPositive(root, "o", state.coreDueAtMillis)
        state.recurringFailure?.let { root["f"] = it.wireName() }
        putPositive(root, "n", state.recurringFailureCount)
        putPositive(root, "a", state.repairAttemptCount)
        putPositive(root, "b", state.repairStartedAtMillis)
        if (state.revalidationPending) {
            root["q"] = true
        }
        state.answerEvidence?.let { root["e"] = AnswerEvidenceCodec.toJsonObject(it) }
        return CompactJson.encodeObject(root)
    }

    @JvmStatic
    fun decode(encoded: String?): AdaptiveRouteState? {
        val root = CompactJson.decodeObject(encoded) ?: return null
        if (root.long("v") != FORMAT_VERSION) {
            return null
        }
        val activeCore = CoreSkill.fromWireName(root.string("c")) ?: return null
        val tasks = root.stringList("t").filter { it.isNotBlank() }
        // Unknown task wires cannot be rendered or progressed safely. Treat
        // the whole route as malformed so AdaptiveStudyItemPolicy restores
        // the owning core instead of trapping the item in relearning.
        if (tasks.any { it !in RecordsBase.StudyLadderSettings.REPAIR_TASK_TYPES }) {
            return null
        }
        val rawTaskIndex = root.nonNegativeInt("i")
        val taskIndex = if (tasks.isEmpty()) 0 else rawTaskIndex.coerceAtMost(tasks.lastIndex)
        val recurringFailureWire = root.string("f")
        val recurringFailure = when {
            recurringFailureWire.isNullOrEmpty() -> null
            else -> FailureKind.fromWireName(recurringFailureWire) ?: FailureKind.UNKNOWN
        }
        val evidenceObject = root.objectValue("e")
        return AdaptiveRouteState(
            activeCore = activeCore,
            recognitionReviewCount = root.nonNegativeInt("rr"),
            contextualReadingReviewCount = root.nonNegativeInt("cr"),
            activeRepairTasks = tasks,
            repairTaskIndex = taskIndex,
            repairStepMinutes = root.intList("m").filter { it > 0 },
            repairDueAtMillis = root.nonNegativeLong("d"),
            coreDueAtMillis = root.nonNegativeLong("o"),
            recurringFailure = recurringFailure,
            recurringFailureCount = if (recurringFailure == null) 0 else root.nonNegativeInt("n"),
            repairAttemptCount = root.nonNegativeInt("a"),
            repairStartedAtMillis = root.nonNegativeLong("b"),
            revalidationPending = root.boolean("q"),
            answerEvidence = evidenceObject?.let(AnswerEvidenceCodec::fromJsonObject),
        )
    }

    private fun putPositive(target: MutableMap<String, Any?>, key: String, value: Int) {
        if (value > 0) {
            target[key] = value.toLong()
        }
    }

    private fun putPositive(target: MutableMap<String, Any?>, key: String, value: Long) {
        if (value > 0L) {
            target[key] = value
        }
    }

    private fun putList(target: MutableMap<String, Any?>, key: String, values: List<*>) {
        if (values.isNotEmpty()) {
            target[key] = values
        }
    }
}

/**
 * Dependency-free JSON support for the two tiny persisted payloads above.
 * It deliberately accepts unknown object fields so newer writers remain
 * readable by older app versions, while rejecting malformed/trailing input.
 */
private object CompactJson {
    private const val MAX_INPUT_CHARS = 262_144
    private const val MAX_DEPTH = 8

    fun encodeObject(values: Map<String, Any?>): String = buildString {
        appendObject(this, values)
    }

    fun decodeObject(encoded: String?): Map<String, Any?>? {
        if (encoded.isNullOrBlank() || encoded.length > MAX_INPUT_CHARS) {
            return null
        }
        return try {
            Reader(encoded).readRootObject()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun appendObject(target: StringBuilder, values: Map<String, Any?>) {
        target.append('{')
        var first = true
        for ((key, value) in values) {
            if (!first) {
                target.append(',')
            }
            first = false
            appendString(target, key)
            target.append(':')
            appendValue(target, value)
        }
        target.append('}')
    }

    private fun appendArray(target: StringBuilder, values: List<*>) {
        target.append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) {
                target.append(',')
            }
            appendValue(target, value)
        }
        target.append(']')
    }

    @Suppress("UNCHECKED_CAST")
    private fun appendValue(target: StringBuilder, value: Any?) {
        when (value) {
            null -> target.append("null")
            is String -> appendString(target, value)
            is Boolean -> target.append(value)
            is Int -> target.append(value)
            is Long -> target.append(value)
            is List<*> -> appendArray(target, value)
            is Map<*, *> -> appendObject(target, value as Map<String, Any?>)
            else -> throw IllegalArgumentException("Unsupported compact JSON value")
        }
    }

    private fun appendString(target: StringBuilder, value: String) {
        target.append('"')
        for (character in value) {
            when (character) {
                '"' -> target.append("\\\"")
                '\\' -> target.append("\\\\")
                '\b' -> target.append("\\b")
                '\u000c' -> target.append("\\f")
                '\n' -> target.append("\\n")
                '\r' -> target.append("\\r")
                '\t' -> target.append("\\t")
                else -> if (character.code < 0x20) {
                    target.append("\\u")
                    target.append(character.code.toString(16).padStart(4, '0'))
                } else {
                    target.append(character)
                }
            }
        }
        target.append('"')
    }

    private class Reader(private val source: String) {
        private var index = 0

        fun readRootObject(): Map<String, Any?> {
            skipWhitespace()
            val root = readObject(0)
            skipWhitespace()
            require(index == source.length) { "Trailing JSON input" }
            return root
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            require(depth < MAX_DEPTH) { "JSON nesting is too deep" }
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (consume('}')) {
                return result
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = readValue(depth + 1)
                skipWhitespace()
                if (consume('}')) {
                    return result
                }
                expect(',')
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            require(depth < MAX_DEPTH) { "JSON nesting is too deep" }
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (consume(']')) {
                return result
            }
            while (true) {
                skipWhitespace()
                result.add(readValue(depth + 1))
                skipWhitespace()
                if (consume(']')) {
                    return result
                }
                expect(',')
            }
        }

        private fun readValue(depth: Int): Any? {
            require(index < source.length) { "Missing JSON value" }
            return when (source[index]) {
                '"' -> readString()
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                '-', in '0'..'9' -> readLong()
                else -> throw IllegalArgumentException("Invalid JSON value")
            }
        }

        private fun readString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < source.length) {
                val character = source[index++]
                when (character) {
                    '"' -> return result.toString()
                    '\\' -> result.append(readEscape())
                    else -> {
                        require(character.code >= 0x20) { "Control character in JSON string" }
                        result.append(character)
                    }
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun readEscape(): Char {
            require(index < source.length) { "Unterminated JSON escape" }
            return when (val escaped = source[index++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000c'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> readUnicodeEscape()
                else -> throw IllegalArgumentException("Invalid JSON escape")
            }
        }

        private fun readUnicodeEscape(): Char {
            require(index + 4 <= source.length) { "Truncated unicode escape" }
            val digits = source.substring(index, index + 4)
            index += 4
            return digits.toIntOrNull(16)?.toChar()
                ?: throw IllegalArgumentException("Invalid unicode escape")
        }

        private fun readLong(): Long {
            val start = index
            if (source[index] == '-') {
                index++
            }
            val firstDigit = index
            while (index < source.length && source[index] in '0'..'9') {
                index++
            }
            require(index > firstDigit) { "Invalid JSON number" }
            return source.substring(start, index).toLongOrNull()
                ?: throw IllegalArgumentException("JSON number is out of range")
        }

        private fun readLiteral(expected: String, value: Any?): Any? {
            require(source.regionMatches(index, expected, 0, expected.length)) { "Invalid JSON literal" }
            index += expected.length
            return value
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) {
                index++
            }
        }

        private fun expect(expected: Char) {
            require(index < source.length && source[index] == expected) { "Expected '$expected'" }
            index++
        }

        private fun consume(expected: Char): Boolean {
            if (index < source.length && source[index] == expected) {
                index++
                return true
            }
            return false
        }
    }
}

private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

private fun Map<String, Any?>.long(key: String): Long? = this[key] as? Long

private fun Map<String, Any?>.nonNegativeLong(key: String): Long = long(key)?.coerceAtLeast(0L) ?: 0L

private fun Map<String, Any?>.nonNegativeInt(key: String): Int {
    return long(key)?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0
}

private fun Map<String, Any?>.boolean(key: String): Boolean = this[key] as? Boolean ?: false

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

private fun Map<String, Any?>.stringList(key: String): List<String> {
    return (this[key] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
}

private fun Map<String, Any?>.intList(key: String): List<Int> {
    return (this[key] as? List<*>)
        ?.mapNotNull { (it as? Long)?.takeIf { value -> value in 1L..Int.MAX_VALUE.toLong() }?.toInt() }
        .orEmpty()
}

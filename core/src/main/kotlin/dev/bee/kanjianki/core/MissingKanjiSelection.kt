package dev.bee.kanjianki.core

import java.util.Collections
import java.util.LinkedHashSet

class MissingKanjiSelection private constructor(selected: Collection<String>) {
    val selectedLiterals: Set<String> = Collections.unmodifiableSet(LinkedHashSet(selected))

    val size: Int
        get() = selectedLiterals.size

    fun isSelected(literal: String): Boolean {
        val normalized = MissingKanjiAnalyzer.normalizeLiteral(literal) ?: return false
        return selectedLiterals.contains(normalized)
    }

    fun toggle(literal: String): MissingKanjiSelection {
        val normalized = MissingKanjiAnalyzer.normalizeLiteral(literal) ?: return this
        val next = LinkedHashSet(selectedLiterals)
        if (!next.add(normalized)) {
            next.remove(normalized)
        }
        return from(next)
    }

    fun selectAllVisible(candidates: Iterable<MissingKanjiCandidate>): MissingKanjiSelection {
        val next = LinkedHashSet(selectedLiterals)
        for (candidate in candidates) {
            MissingKanjiAnalyzer.normalizeLiteral(candidate.literal)?.let(next::add)
        }
        return from(next)
    }

    fun clearVisible(candidates: Iterable<MissingKanjiCandidate>): MissingKanjiSelection {
        if (selectedLiterals.isEmpty()) {
            return this
        }
        val next = LinkedHashSet(selectedLiterals)
        for (candidate in candidates) {
            MissingKanjiAnalyzer.normalizeLiteral(candidate.literal)?.let(next::remove)
        }
        return from(next)
    }

    fun clearAll(): MissingKanjiSelection = if (selectedLiterals.isEmpty()) this else EMPTY

    fun reconcile(candidates: Iterable<MissingKanjiCandidate>): MissingKanjiSelection {
        if (selectedLiterals.isEmpty()) {
            return this
        }
        val available = HashSet<String>()
        for (candidate in candidates) {
            MissingKanjiAnalyzer.normalizeLiteral(candidate.literal)?.let(available::add)
        }
        val next = LinkedHashSet<String>()
        for (literal in selectedLiterals) {
            if (available.contains(literal)) {
                next.add(literal)
            }
        }
        return from(next)
    }

    override fun equals(other: Any?): Boolean {
        return this === other ||
            other is MissingKanjiSelection && selectedLiterals == other.selectedLiterals
    }

    override fun hashCode(): Int = selectedLiterals.hashCode()

    override fun toString(): String = "MissingKanjiSelection(selectedLiterals=$selectedLiterals)"

    companion object {
        private val EMPTY = MissingKanjiSelection(emptySet())

        fun empty(): MissingKanjiSelection = EMPTY

        fun from(literals: Iterable<String>): MissingKanjiSelection {
            val normalized = LinkedHashSet<String>()
            for (literal in literals) {
                MissingKanjiAnalyzer.normalizeLiteral(literal)?.let(normalized::add)
            }
            return if (normalized.isEmpty()) EMPTY else MissingKanjiSelection(normalized)
        }
    }
}

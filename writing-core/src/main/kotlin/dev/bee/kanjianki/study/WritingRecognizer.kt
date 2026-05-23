package dev.bee.kanjianki.study

import dev.bee.kanjianki.core.study.RecognitionCandidate
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.CompletableFuture

interface WritingRecognizer : AutoCloseable {
    fun modelStatus(): CompletableFuture<ModelStatus>

    fun downloadModel(): CompletableFuture<ModelStatus>

    fun recognize(writing: CapturedWriting?): CompletableFuture<RecognitionResult>

    override fun close()

    class ModelStatus(
        @JvmField val modelName: String?,
        @JvmField val languageTag: String?,
        @JvmField val downloaded: Boolean,
        @JvmField val message: String?,
    )

    class RecognitionResult(candidates: List<Candidate>) {
        @JvmField
        val candidates: List<Candidate> = Collections.unmodifiableList(ArrayList(candidates))

        fun topText(): String {
            if (candidates.isEmpty()) {
                return ""
            }
            return candidates[0].text
        }

        fun toRecognitionCandidates(): List<RecognitionCandidate> {
            val out = ArrayList<RecognitionCandidate>()
            for (candidate in candidates) {
                out.add(RecognitionCandidate(candidate.text, candidate.score))
            }
            return out
        }
    }

    class Candidate(text: String?, @JvmField val score: Float?) {
        @JvmField
        val text: String = text ?: ""
    }

    companion object {
        @JvmStatic
        fun recognitionCandidates(result: RecognitionResult?): List<RecognitionCandidate> {
            return result?.toRecognitionCandidates() ?: Collections.emptyList()
        }
    }
}

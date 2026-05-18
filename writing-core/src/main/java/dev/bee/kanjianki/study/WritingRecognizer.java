package dev.bee.kanjianki.study;

import dev.bee.kanjianki.core.study.RecognitionCandidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface WritingRecognizer extends AutoCloseable {
    CompletableFuture<ModelStatus> modelStatus();

    CompletableFuture<ModelStatus> downloadModel();

    CompletableFuture<RecognitionResult> recognize(CapturedWriting writing);

    @Override
    void close();

    static List<RecognitionCandidate> recognitionCandidates(RecognitionResult result) {
        return result == null ? Collections.emptyList() : result.toRecognitionCandidates();
    }

    final class ModelStatus {
        public final String modelName;
        public final String languageTag;
        public final boolean downloaded;
        public final String message;

        public ModelStatus(String modelName, String languageTag, boolean downloaded, String message) {
            this.modelName = modelName;
            this.languageTag = languageTag;
            this.downloaded = downloaded;
            this.message = message;
        }
    }

    final class RecognitionResult {
        public final List<Candidate> candidates;

        public RecognitionResult(List<Candidate> candidates) {
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        }

        public String topText() {
            if (candidates.isEmpty()) {
                return "";
            }
            return candidates.get(0).text;
        }

        public List<RecognitionCandidate> toRecognitionCandidates() {
            List<RecognitionCandidate> out = new ArrayList<>();
            for (Candidate candidate : candidates) {
                out.add(new RecognitionCandidate(candidate.text, candidate.score));
            }
            return out;
        }
    }

    final class Candidate {
        public final String text;
        public final Float score;

        public Candidate(String text, Float score) {
            this.text = text == null ? "" : text;
            this.score = score;
        }
    }
}

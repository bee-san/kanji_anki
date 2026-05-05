package dev.bee.kanjianki.study;

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

package dev.bee.kanjianki.core.study;

public final class RecognitionCandidate {
    public final String text;
    public final Float score;

    public RecognitionCandidate(String text, Float score) {
        this.text = text == null ? "" : text;
        this.score = score;
    }
}

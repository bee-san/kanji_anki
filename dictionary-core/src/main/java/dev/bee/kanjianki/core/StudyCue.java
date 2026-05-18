package dev.bee.kanjianki.core;

import java.util.Objects;

public final class StudyCue {
    public final String meaning;
    public final String reading;
    public final String fromExpression;
    public final String meaningSource;

    public StudyCue(String meaning, String reading, String fromExpression, String meaningSource) {
        this.meaning = normalize(meaning);
        this.reading = normalize(reading);
        this.fromExpression = normalize(fromExpression);
        this.meaningSource = normalize(meaningSource);
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StudyCue)) {
            return false;
        }
        StudyCue cue = (StudyCue) other;
        return meaning.equals(cue.meaning)
                && reading.equals(cue.reading)
                && fromExpression.equals(cue.fromExpression)
                && meaningSource.equals(cue.meaningSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meaning, reading, fromExpression, meaningSource);
    }

    @Override
    public String toString() {
        return "StudyCue{"
                + "meaning='" + meaning + '\''
                + ", reading='" + reading + '\''
                + ", fromExpression='" + fromExpression + '\''
                + ", meaningSource='" + meaningSource + '\''
                + '}';
    }
}

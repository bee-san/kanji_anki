package dev.bee.kanjianki.core.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StrokeDiagnosis {
    public enum Label {
        WRONG_ORDER,
        WRONG_DIRECTION,
        MISSING_STROKE,
        ROUGH_SHAPE,
        RECOGNIZED_BUT_MESSY
    }

    private static final StrokeDiagnosis EMPTY = new StrokeDiagnosis(Collections.emptyList());

    public final List<Entry> entries;

    private StrokeDiagnosis(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public static StrokeDiagnosis empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean hasLabel(Label label) {
        for (Entry entry : entries) {
            if (entry.label == label) {
                return true;
            }
        }
        return false;
    }

    public boolean hasLabel(Label label, int strokeNumber) {
        for (Entry entry : entries) {
            if (entry.label == label && entry.strokeNumber == strokeNumber) {
                return true;
            }
        }
        return false;
    }

    public StrokeDiagnosis plus(Label label, int strokeNumber) {
        Builder builder = builder();
        for (Entry entry : entries) {
            builder.add(entry.label, entry.strokeNumber);
        }
        builder.add(label, strokeNumber);
        return builder.build();
    }

    public static final class Entry {
        public final Label label;
        public final int strokeNumber;

        private Entry(Label label, int strokeNumber) {
            this.label = label;
            this.strokeNumber = Math.max(0, strokeNumber);
        }
    }

    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();

        public Builder add(Label label, int strokeNumber) {
            if (label == null) {
                return this;
            }
            int safeStrokeNumber = Math.max(0, strokeNumber);
            for (Entry entry : entries) {
                if (entry.label == label && entry.strokeNumber == safeStrokeNumber) {
                    return this;
                }
            }
            entries.add(new Entry(label, safeStrokeNumber));
            return this;
        }

        public StrokeDiagnosis build() {
            return entries.isEmpty() ? EMPTY : new StrokeDiagnosis(entries);
        }
    }
}

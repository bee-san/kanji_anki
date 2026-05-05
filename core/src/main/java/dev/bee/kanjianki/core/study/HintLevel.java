package dev.bee.kanjianki.core.study;

public enum HintLevel {
    TRACE(0),
    OUTLINE(1),
    MINIMAL(2),
    BLIND(3);

    private final int writingLevel;

    HintLevel(int writingLevel) {
        this.writingLevel = writingLevel;
    }

    public int writingLevel() {
        return writingLevel;
    }

    public HintLevel next() {
        int next = Math.min(values().length - 1, ordinal() + 1);
        return values()[next];
    }

    public HintLevel previous() {
        int previous = Math.max(0, ordinal() - 1);
        return values()[previous];
    }

    public static HintLevel fromWritingLevel(int writingLevel) {
        for (HintLevel level : values()) {
            if (level.writingLevel == writingLevel) {
                return level;
            }
        }
        if (writingLevel < TRACE.writingLevel) {
            return TRACE;
        }
        return BLIND;
    }
}

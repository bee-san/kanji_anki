package dev.bee.kanjianki.core.study;

import java.util.Optional;

public interface StrokeGuideProvider {
    Optional<StrokeGuide> guideFor(String kanji);
}

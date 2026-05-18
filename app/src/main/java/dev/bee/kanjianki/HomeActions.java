package dev.bee.kanjianki;

import dev.bee.kanjianki.core.HomeTextCopy;

final class HomeActions {
    private HomeActions() {
    }

    static String toggleLocalSuspension(
            LocalSuspensionWriter writer,
            String kanji,
            boolean currentlySuspended,
            long changedAtMillis
    ) {
        writer.setKanjiLocallySuspended(kanji, !currentlySuspended, changedAtMillis);
        return HomeTextCopy.localSuspendToast(currentlySuspended);
    }

    interface LocalSuspensionWriter {
        void setKanjiLocallySuspended(String kanji, boolean suspended, long changedAtMillis);
    }
}

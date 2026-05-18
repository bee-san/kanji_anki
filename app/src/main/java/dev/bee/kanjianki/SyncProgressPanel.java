package dev.bee.kanjianki;

import android.content.Context;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import dev.bee.kanjianki.core.SyncProgressCopy;
import dev.bee.kanjianki.sync.SyncProgress;

final class SyncProgressPanel extends LinearLayout {
    private static final int INK = 0xFF2D1635;
    private static final int MUTED = 0xFF6C5674;
    private final TextView stage;
    private final ProgressBar progressBar;
    private final TextView count;
    private final TextView rate;
    private long scanStartedAt;
    private int lastScannedCards = -1;
    private int lastTotalCards = -1;

    SyncProgressPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        stage = text(context, "Finding note type", 22, INK, true);
        addView(stage);
        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setContentDescription("Sync progress");
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(12));
        progressParams.setMargins(0, dp(12), 0, dp(12));
        addView(progressBar, progressParams);
        count = text(context, "Reading collection details.", 17, MUTED, false);
        rate = text(context, "", 15, MUTED, false);
        addView(count);
        addView(rate);
    }

    void render(SyncProgress progress) {
        SyncProgressCopy.Stage currentStage = coreStage(progress.stage);
        stage.setText(SyncProgressCopy.stageTitle(currentStage));
        if (progress.totalKnown()) {
            lastScannedCards = progress.scannedCards;
            lastTotalCards = progress.totalCards;
            if (scanStartedAt <= 0L) {
                scanStartedAt = SystemClock.elapsedRealtime();
            }
        }
        if (lastTotalCards >= 0) {
            renderKnownTotal(progress);
            return;
        }
        progressBar.setIndeterminate(true);
        count.setText(SyncProgressCopy.stageBody(currentStage));
        rate.setText("");
        progressBar.setContentDescription("Sync progress: " + SyncProgressCopy.stageTitle(currentStage));
    }

    private void renderKnownTotal(SyncProgress progress) {
        progressBar.setIndeterminate(false);
        progressBar.setMax(1000);
        progressBar.setProgress(SyncProgressCopy.progressPermille(lastScannedCards, lastTotalCards));
        String cardText = SyncProgressCopy.cardProgressText(lastScannedCards, lastTotalCards);
        count.setText(cardText);
        rate.setText(scanRateText(progress.stage));
        progressBar.setContentDescription("Sync progress: " + cardText);
    }

    private String scanRateText(SyncProgress.Stage stage) {
        return SyncProgressCopy.scanRateText(
                coreStage(stage),
                lastScannedCards,
                lastTotalCards,
                SystemClock.elapsedRealtime() - scanStartedAt
        );
    }

    static SyncProgressCopy.Stage coreStage(SyncProgress.Stage stage) {
        if (stage == null) {
            return null;
        }
        return switch (stage) {
            case FINDING_NOTE_TYPE -> SyncProgressCopy.Stage.FINDING_NOTE_TYPE;
            case READING_NOTES -> SyncProgressCopy.Stage.READING_NOTES;
            case SCANNING_CARDS -> SyncProgressCopy.Stage.SCANNING_CARDS;
            case PROCESSING_IMPORTED_CARDS -> SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS;
            case BUILDING_PRACTICE_QUEUE -> SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE;
            case ARCHIVING_IMPORTED_CARDS -> SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS;
        };
    }

    private TextView text(Context context, String value, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setPadding(0, dp(4), 0, dp(4));
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

package dev.bee.kanjianki;

import android.content.Context;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import dev.bee.kanjianki.sync.SyncProgress;

import java.util.Locale;

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
        stage.setText(syncStageText(progress.stage));
        if (progress.totalKnown()) {
            lastScannedCards = progress.scannedCards;
            lastTotalCards = progress.totalCards;
            if (progress.stage == SyncProgress.Stage.SCANNING_CARDS && scanStartedAt <= 0L) {
                scanStartedAt = SystemClock.elapsedRealtime();
            }
        }
        if (lastTotalCards >= 0) {
            renderKnownTotal(progress);
            return;
        }
        progressBar.setIndeterminate(true);
        count.setText(syncStageBody(progress.stage));
        rate.setText("");
        progressBar.setContentDescription("Sync progress: " + syncStageText(progress.stage));
    }

    private void renderKnownTotal(SyncProgress progress) {
        progressBar.setIndeterminate(false);
        progressBar.setMax(1000);
        int value = lastTotalCards == 0
                ? 1000
                : Math.min(1000, Math.max(0, Math.round((lastScannedCards * 1000f) / lastTotalCards)));
        progressBar.setProgress(value);
        String cardText = lastScannedCards + " / " + lastTotalCards + " cards scanned";
        count.setText(cardText);
        rate.setText(scanRateText(progress.stage));
        progressBar.setContentDescription("Sync progress: " + cardText);
    }

    private String scanRateText(SyncProgress.Stage stage) {
        if (stage != SyncProgress.Stage.SCANNING_CARDS) {
            return lastScannedCards >= lastTotalCards ? "Card scan finished." : "";
        }
        if (lastScannedCards <= 0 || scanStartedAt <= 0L) {
            return "Scanning cards.";
        }
        long elapsedMillis = Math.max(1L, SystemClock.elapsedRealtime() - scanStartedAt);
        double perSecond = lastScannedCards * 1000.0 / elapsedMillis;
        String rateText = String.format(Locale.US, perSecond >= 10.0 ? "%.0f cards/sec" : "%.1f cards/sec", perSecond);
        int remaining = Math.max(0, lastTotalCards - lastScannedCards);
        if (remaining == 0) {
            return rateText + " - finishing up";
        }
        if (lastScannedCards >= 3 && elapsedMillis >= 1000L && perSecond > 0.01) {
            long etaMillis = Math.round((remaining / perSecond) * 1000.0);
            return rateText + " - about " + shortDuration(etaMillis) + " left";
        }
        return rateText + " - estimating time left";
    }

    private String syncStageText(SyncProgress.Stage stage) {
        if (stage == SyncProgress.Stage.FINDING_NOTE_TYPE) {
            return "Finding note type";
        }
        if (stage == SyncProgress.Stage.READING_NOTES) {
            return "Reading notes";
        }
        if (stage == SyncProgress.Stage.SCANNING_CARDS) {
            return "Scanning cards";
        }
        if (stage == SyncProgress.Stage.BUILDING_PRACTICE_QUEUE) {
            return "Building practice queue";
        }
        if (stage == SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS) {
            return "Archiving imported suspended cards";
        }
        return "Syncing cards";
    }

    private String syncStageBody(SyncProgress.Stage stage) {
        if (stage == SyncProgress.Stage.FINDING_NOTE_TYPE) {
            return "Checking collection shape.";
        }
        if (stage == SyncProgress.Stage.READING_NOTES) {
            return "Reading notes before the card total is known.";
        }
        if (stage == SyncProgress.Stage.BUILDING_PRACTICE_QUEUE) {
            return "Saving the practice queue.";
        }
        if (stage == SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS) {
            return "Updating archived suspended cards.";
        }
        return "Preparing card scan.";
    }

    private String shortDuration(long millis) {
        long seconds = Math.max(1L, Math.round(millis / 1000.0));
        if (seconds < 60L) {
            return seconds + " sec";
        }
        long minutes = Math.max(1L, Math.round(seconds / 60.0));
        if (minutes < 60L) {
            return minutes + " min";
        }
        long hours = Math.max(1L, Math.round(minutes / 60.0));
        return hours + " hr";
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

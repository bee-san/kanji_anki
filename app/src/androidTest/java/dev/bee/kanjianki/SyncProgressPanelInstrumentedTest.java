package dev.bee.kanjianki;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.sync.SyncProgress;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class SyncProgressPanelInstrumentedTest {
    @Test
    public void syncProgressPanelHandlesEmptyKnownTotal() {
        SyncProgressPanel panel = panel();

        panel.render(SyncProgress.cardsScanned(0, 0));

        ProgressBar progress = findType(panel, ProgressBar.class);
        assertNotNull(progress);
        assertFalse(progress.isIndeterminate());
        assertEquals(1000, progress.getMax());
        assertEquals(1000, progress.getProgress());
        assertNotNull(findText(panel, "0 / 0 cards scanned"));
        assertTrue(progress.getContentDescription().toString().contains("0 / 0 cards scanned"));
    }

    @Test
    public void syncProgressTitleUsesComposeBridge() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        View title = SyncProgressPanelKt.syncProgressTitleView(context, "Syncing cards");

        assertTrue(title instanceof androidx.compose.ui.platform.ComposeView);
    }

    @Test
    public void syncProgressScreenUsesSingleComposeBridge() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        View screen = SyncProgressPanelKt.syncProgressScreenView(context, "Syncing cards", new SyncProgressPanel(context));

        assertTrue(screen instanceof androidx.compose.ui.platform.ComposeView);
    }

    @Test
    public void syncProgressPanelShowsEtaAndFinishingCopy() {
        SyncProgressPanel panel = panel();

        panel.render(SyncProgress.cardsScanned(0, 50_000));
        SystemClock.sleep(1100L);
        panel.render(SyncProgress.cardsScanned(3, 50_000));

        assertNotNull(findText(panel, "cards/sec - about"));
        assertNotNull(findText(panel, "hr left"));

        panel.render(SyncProgress.cardsScanned(50_000, 50_000));
        assertNotNull(findText(panel, "finishing up"));
    }

    @Test
    public void syncProgressPanelShowsSecondAndMinuteEtaUnits() {
        SyncProgressPanel secondsPanel = panel();
        secondsPanel.render(SyncProgress.cardsScanned(0, 200));
        SystemClock.sleep(1100L);
        secondsPanel.render(SyncProgress.cardsScanned(199, 200));
        assertNotNull(findText(secondsPanel, "sec left"));

        SyncProgressPanel minutesPanel = panel();
        minutesPanel.render(SyncProgress.cardsScanned(0, 600));
        SystemClock.sleep(1100L);
        minutesPanel.render(SyncProgress.cardsScanned(3, 600));
        assertNotNull(findText(minutesPanel, "min left"));
    }

    @Test
    public void syncProgressPanelEstimatesBeforeEnoughTimeHasElapsed() {
        SyncProgressPanel panel = panel();

        panel.render(SyncProgress.cardsScanned(0, 10));
        panel.render(SyncProgress.cardsScanned(3, 10));

        assertNotNull(findText(panel, "cards/sec - estimating time left"));
    }

    @Test
    public void syncProgressPanelEstimatesBeforeEnoughCardsAreScanned() {
        SyncProgressPanel panel = panel();

        panel.render(SyncProgress.cardsScanned(0, 10));
        panel.render(SyncProgress.cardsScanned(2, 10));

        assertNotNull(findText(panel, "cards/sec - estimating time left"));
    }

    @Test
    public void syncProgressPanelKeepsKnownCountAcrossLocalStages() {
        SyncProgressPanel panel = panel();

        panel.render(SyncProgress.cardsScanned(7, 9));
        panel.render(SyncProgress.atStage(SyncProgress.Stage.BUILDING_PRACTICE_QUEUE));
        assertNotNull(findText(panel, "Building practice queue"));
        assertNotNull(findText(panel, "7 / 9 cards scanned"));
        assertNotNull(findText(panel, "Saving the practice queue."));

        panel.render(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS));
        assertNotNull(findText(panel, "Archiving imported suspended cards"));
        assertNotNull(findText(panel, "7 / 9 cards scanned"));
        assertNotNull(findText(panel, "Updating archived suspended cards."));
    }

    @Test
    public void syncProgressPanelShowsDefensiveUnknownStageCopyBeforeTotalIsKnown() {
        SyncProgressPanel panel = panel();

        panel.render(SyncProgress.atStage(null));

        ProgressBar progress = findType(panel, ProgressBar.class);
        assertNotNull(progress);
        assertTrue(progress.isIndeterminate());
        assertNotNull(findText(panel, "Syncing cards"));
        assertNotNull(findText(panel, "Preparing card scan."));
        assertTrue(progress.getContentDescription().toString().contains("Syncing cards"));
    }

    @Test
    public void syncProgressPanelShowsAllPreScanStageCopy() {
        SyncProgressPanel panel = panel();

        panel.render(SyncProgress.atStage(SyncProgress.Stage.FINDING_NOTE_TYPE));
        assertNotNull(findText(panel, "Finding note type"));
        assertNotNull(findText(panel, "Checking collection shape."));

        panel.render(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES));
        assertNotNull(findText(panel, "Reading notes"));
        assertNotNull(findText(panel, "Reading notes before the card total is known."));

        panel.render(SyncProgress.atStage(SyncProgress.Stage.PROCESSING_IMPORTED_CARDS));
        assertNotNull(findText(panel, "Processing imported cards"));
        assertNotNull(findText(panel, "AnkiDroid read finished. Processing imported cards locally."));
    }

    private static SyncProgressPanel panel() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SyncProgressPanel panel = new SyncProgressPanel(context);
        panel.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST)
        );
        panel.layout(0, 0, 1080, panel.getMeasuredHeight());
        return panel;
    }

    private static TextView findText(View root, String text) {
        if (root instanceof TextView) {
            CharSequence value = ((TextView) root).getText();
            if (value != null && value.toString().contains(text)) {
                return (TextView) root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends View> T findType(View root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = findType(group.getChildAt(i), type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

package dev.bee.kanjianki.sync;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class SyncSettingsCoverageTest {
    @Test
    public void fromStoreWithNullUsesKikuDefaultsAndSuspendedOnlyImports() {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        Records.Settings settings = SyncSettings.fromStore(null);

        assertEquals(defaults.modelName, settings.modelName);
        assertEquals(defaults.expressionField, settings.expressionField);
        assertEquals(defaults.readingField, settings.readingField);
        assertEquals(defaults.meaningField, settings.meaningField);
        assertEquals(defaults.sentenceField, settings.sentenceField);
        assertEquals(defaults.frequencyField, settings.frequencyField);
        assertEquals(defaults.frequencySortField, settings.frequencySortField);
        assertEquals(defaults.suspendedRankMin, settings.suspendedRankMin);
        assertEquals(defaults.suspendedRankMax, settings.suspendedRankMax);
        assertEquals(defaults.writingTriggerMissDays, settings.writingTriggerMissDays);
        assertEquals(defaults.recognitionPromotionPasses, settings.recognitionPromotionPasses);
        assertEquals(defaults.realDueReviewsToMove, settings.realDueReviewsToMove);
        assertFalse(settings.importActiveCards);
        assertTrue(settings.importSuspendedCards);
        assertFalse(settings.importTaggedCardsEnabled());
        assertEquals(defaults.importTags, settings.importTags);
        assertEquals(defaults.importTagsText(), settings.importTagsText());
        assertFalse(settings.importWeakCards);
        assertEquals(defaults.importWeakFsrsDifficultyThreshold, settings.importWeakFsrsDifficultyThreshold, 0.001);
        assertEquals(defaults.importWeakLapsesThreshold, settings.importWeakLapsesThreshold);
        assertEquals(defaults.importMinMatchingCardsPerKanji, settings.importMinMatchingCardsPerKanji);
        assertFalse(settings.browserQueryImportEnabled());
        assertEquals("", settings.normalizedBrowserQuery());
        assertTrue(settings.hasImportSourceEnabled());
    }

    @Test
    public void localStoreIsFinalSoJvmTestsCannotSubclassFakeStoreBranches() {
        assertTrue(Modifier.isFinal(LocalStore.class.getModifiers()));
    }

    @Test
    public void syncProgressNormalizesStageAndScanCounts() {
        for (SyncProgress.Stage stage : SyncProgress.Stage.values()) {
            SyncProgress progress = SyncProgress.atStage(stage);

            assertSame(stage, progress.stage);
            assertEquals(0, progress.scannedCards);
            assertEquals(-1, progress.totalCards);
            assertFalse(progress.totalKnown());
            SyncProgress.NONE.onSyncProgress(progress);
        }

        SyncProgress negative = SyncProgress.cardsScanned(-3, -5);
        assertSame(SyncProgress.Stage.SCANNING_CARDS, negative.stage);
        assertEquals(0, negative.scannedCards);
        assertEquals(0, negative.totalCards);
        assertTrue(negative.totalKnown());

        SyncProgress known = SyncProgress.cardsScanned(7, 12);
        assertEquals(7, known.scannedCards);
        assertEquals(12, known.totalCards);
        assertTrue(known.totalKnown());
    }

    @Test
    public void autoSyncRunnerResultsExposeOutcomeFlags() throws Exception {
        AutoSyncRunner.Result success = autoSyncResult("success", "Sync complete.");
        assertTrue(success.ran);
        assertTrue(success.success);
        assertEquals("Sync complete.", success.message);

        AutoSyncRunner.Result failed = autoSyncResult("failed", "Provider missing.");
        assertTrue(failed.ran);
        assertFalse(failed.success);
        assertEquals("Provider missing.", failed.message);

        AutoSyncRunner.Result skipped = autoSyncResult("skipped", "Daily Anki sync is off.");
        assertFalse(skipped.ran);
        assertFalse(skipped.success);
        assertEquals("Daily Anki sync is off.", skipped.message);
    }

    @Test
    public void autoSyncSchedulerNextTriggerUsesTodayTomorrowAndAlreadySyncedBranches() {
        LocalStore.AutoSyncSettings settings = new LocalStore.AutoSyncSettings(
                true,
                true,
                19,
                30,
                0L,
                0L,
                0L
        );
        long morning = millis(2026, Calendar.MAY, 15, 9, 0);
        long evening = millis(2026, Calendar.MAY, 15, 20, 0);

        assertEquals(
                millis(2026, Calendar.MAY, 15, 19, 30),
                AutoSyncScheduler.nextTriggerMillis(settings, morning)
        );
        assertEquals(
                millis(2026, Calendar.MAY, 16, 19, 30),
                AutoSyncScheduler.nextTriggerMillis(settings, evening)
        );
        assertEquals(
                millis(2026, Calendar.MAY, 16, 19, 30),
                AutoSyncScheduler.nextTriggerMillis(settings, morning, true)
        );
    }

    @Test
    public void autoSyncSettingsDisplayTimeKeepsZeroPadding() {
        LocalStore.AutoSyncSettings early = new LocalStore.AutoSyncSettings(
                true,
                true,
                7,
                5,
                0L,
                0L,
                0L
        );
        LocalStore.AutoSyncSettings late = new LocalStore.AutoSyncSettings(
                true,
                true,
                23,
                59,
                0L,
                0L,
                0L
        );

        assertEquals("07:05", early.displayTime());
        assertEquals("23:59", late.displayTime());
    }

    private static AutoSyncRunner.Result autoSyncResult(String methodName, String message) throws Exception {
        Method method = AutoSyncRunner.Result.class.getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        return (AutoSyncRunner.Result) method.invoke(null, message);
    }

    private static long millis(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        return calendar.getTimeInMillis();
    }
}

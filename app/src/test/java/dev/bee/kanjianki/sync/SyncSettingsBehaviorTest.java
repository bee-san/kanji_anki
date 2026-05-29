package dev.bee.kanjianki.sync;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class SyncSettingsBehaviorTest {
    @Test
    public void fromStoreWithNullUsesKikuDefaultsAndSuspendedOnlyImports() {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        RecordsSyncModels.Settings settings = SyncSettings.fromStore(null);

        assertDefaultFieldSettings(defaults, settings);
        assertDefaultLearningSettings(defaults, settings);
        assertDefaultImportSettings(defaults, settings);
    }

    @Test
    public void freshLocalStoreUsesSuspendedOnlyDefaultsWithBrowserQueryOff() {
        LocalStore store = freshStore();
        try {
            RecordsSyncModels.Settings settings = SyncSettings.fromStore(store);

            assertFalse(settings.importActiveCards);
            assertTrue(settings.importSuspendedCards);
            assertFalse(settings.importBrowserQueryCards);
            assertEquals("", settings.importBrowserQuery);
            assertFalse(settings.browserQueryImportEnabled());
            assertTrue(settings.hasImportSourceEnabled());
        } finally {
            store.close();
        }
    }

    @Test
    public void customizedImportFiltersRoundTripThroughLocalStoreIncludingBrowserQuery() {
        LocalStore store = freshStore();
        try {
            store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 1);
            store.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, 0);
            store.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, 1);
            store.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, "kani leech");
            store.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, 1);
            store.putDoubleSetting(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY, 8.5);
            store.putIntSetting(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY, 4);
            store.putIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, 2);
            store.putIntSetting(SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY, 1);
            store.putStringSetting(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY, " rated:30:1 ");

            RecordsSyncModels.Settings settings = SyncSettings.fromStore(store);

            assertTrue(settings.importActiveCards);
            assertFalse(settings.importSuspendedCards);
            assertTrue(settings.importTaggedCardsEnabled());
            assertEquals("kani leech", settings.importTagsText());
            assertTrue(settings.importWeakCards);
            assertEquals(8.5, settings.importWeakFsrsDifficultyThreshold, 0.001);
            assertEquals(4, settings.importWeakLapsesThreshold);
            assertEquals(2, settings.importMinMatchingCardsPerKanji);
            assertTrue(settings.importBrowserQueryCards);
            assertEquals("rated:30:1", settings.normalizedBrowserQuery());
            assertTrue(settings.browserQueryImportEnabled());
        } finally {
            store.close();
        }
    }

    @Test
    public void oldDefaultRepairDoesNotEnableBrowserQueryImport() {
        LocalStore store = freshStore();
        try {
            store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 1);
            store.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, 1);

            RecordsSyncModels.Settings settings = SyncSettings.fromStore(store);

            assertFalse(settings.importActiveCards);
            assertTrue(settings.importSuspendedCards);
            assertFalse(settings.importBrowserQueryCards);
            assertEquals("", settings.importBrowserQuery);
            assertFalse(settings.browserQueryImportEnabled());
            assertTrue(settings.hasImportSourceEnabled());
        } finally {
            store.close();
        }
    }

    private static void assertDefaultFieldSettings(
            RecordsSyncModels.Settings defaults,
            RecordsSyncModels.Settings settings
    ) {
        assertEquals(defaults.modelName, settings.modelName);
        assertEquals(defaults.expressionField, settings.expressionField);
        assertEquals(defaults.readingField, settings.readingField);
        assertEquals(defaults.meaningField, settings.meaningField);
        assertEquals(defaults.sentenceField, settings.sentenceField);
        assertEquals(defaults.frequencyField, settings.frequencyField);
        assertEquals(defaults.frequencySortField, settings.frequencySortField);
    }

    private static void assertDefaultLearningSettings(
            RecordsSyncModels.Settings defaults,
            RecordsSyncModels.Settings settings
    ) {
        assertEquals(defaults.suspendedRankMin, settings.suspendedRankMin);
        assertEquals(defaults.suspendedRankMax, settings.suspendedRankMax);
        assertEquals(defaults.writingTriggerMissDays, settings.writingTriggerMissDays);
        assertEquals(defaults.recognitionPromotionPasses, settings.recognitionPromotionPasses);
        assertEquals(defaults.realDueReviewsToMove, settings.realDueReviewsToMove);
        assertEquals(defaults.ladderPromotionIntervalDays, settings.ladderPromotionIntervalDays);
        assertEquals(defaults.ladderDemotionFailStreak, settings.ladderDemotionFailStreak);
    }

    private static void assertDefaultImportSettings(
            RecordsSyncModels.Settings defaults,
            RecordsSyncModels.Settings settings
    ) {
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
    public void stringFallbackHelpersHandleNullBlankAndPresentValues() throws Exception {
        assertEquals("fallback", invokeStringHelper("nonBlank", null, "fallback"));
        assertEquals("fallback", invokeStringHelper("nonBlank", "   ", "fallback"));
        assertEquals("Kiku", invokeStringHelper("nonBlank", "  Kiku  ", "fallback"));
        assertEquals("", invokeStringHelper("nullToEmpty", null));
        assertEquals("tag:kani", invokeStringHelper("nullToEmpty", "tag:kani"));
        assertEquals(
                "Expression",
                invokePrivateStatic(
                        "fieldSetting",
                        new Class<?>[]{LocalStore.class, String.class, String.class, boolean.class},
                        null,
                        SyncSettings.EXPRESSION_FIELD_SETTING_KEY,
                        "Expression",
                        true
                )
        );
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

    @Test
    public void autoSyncSettingsNormalizeClampsInvalidStoredValues() throws Exception {
        LocalStore.AutoSyncSettings raw = new LocalStore.AutoSyncSettings(
                false,
                true,
                99,
                -4,
                -100L,
                -200L,
                -300L
        );

        LocalStore.AutoSyncSettings normalized = normalize(raw);

        assertFalse(normalized.configured);
        assertFalse(normalized.enabled);
        assertEquals(23, normalized.hour);
        assertEquals(0, normalized.minute);
        assertEquals(0L, normalized.lastAttemptAt);
        assertEquals(0L, normalized.lastSuccessAt);
        assertEquals(0L, normalized.nextRunAt);
    }

    @Test
    public void autoSyncSettingsNormalizePreservesConfiguredEnabledSchedule() throws Exception {
        LocalStore.AutoSyncSettings raw = new LocalStore.AutoSyncSettings(
                true,
                true,
                -2,
                90,
                10L,
                20L,
                30L
        );

        LocalStore.AutoSyncSettings normalized = normalize(raw);

        assertTrue(normalized.configured);
        assertTrue(normalized.enabled);
        assertEquals(0, normalized.hour);
        assertEquals(59, normalized.minute);
        assertEquals(10L, normalized.lastAttemptAt);
        assertEquals(20L, normalized.lastSuccessAt);
        assertEquals(30L, normalized.nextRunAt);
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

    private static LocalStore.AutoSyncSettings normalize(LocalStore.AutoSyncSettings settings) throws Exception {
        java.lang.reflect.Method method = LocalStore.AutoSyncSettings.class.getDeclaredMethod("normalized");
        method.setAccessible(true);
        return (LocalStore.AutoSyncSettings) method.invoke(settings);
    }

    private static LocalStore freshStore() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("kanji_anki_simple.db");
        return new LocalStore(context);
    }

    private static String invokeStringHelper(String name, String value, String fallback) throws Exception {
        return (String) invokePrivateStatic(
                name,
                new Class<?>[]{String.class, String.class},
                value,
                fallback
        );
    }

    private static String invokeStringHelper(String name, String value) throws Exception {
        return (String) invokePrivateStatic(
                name,
                new Class<?>[]{String.class},
                value
        );
    }

    private static Object invokePrivateStatic(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        java.lang.reflect.Method method = SyncSettings.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}

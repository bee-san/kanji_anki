package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.AdaptiveRouteState
import dev.bee.kanjianki.core.AdaptiveRouteStateCodec
import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.AnswerEvidence
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.core.StudyTaskTypes
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudySimilarKanjiCoverageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var store: LocalStore? = null

    @After
    fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        store?.close()
        store = null
    }

    @Test
    fun similarChoiceOrderIsTokenStableAndCanonical() {
        val source = mutableListOf("謎", "拉", "烈", "提")
        val original = source.toList()

        val ordered = tokenOrderedSimilarKanjiChoices(source, "session-token")

        assertEquals(listOf("烈", "拉", "提", "謎"), ordered)
        assertEquals(
            ordered,
            tokenOrderedSimilarKanjiChoices(listOf("提", "烈", "謎", "拉"), "session-token"),
        )
        assertEquals(original, source)
        assertEquals(
            similarKanjiChoiceRecoveryDigest(source),
            similarKanjiChoiceRecoveryDigest(source.reversed()),
        )
        assertEquals(
            listOf("拉"),
            tokenOrderedSimilarKanjiChoices(listOf("拉", " 拉 ", ""), "session-token"),
        )
    }

    @Test
    fun renderSimilarKanjiSessionUsesRouteChoiceAndSessionClue() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val index = SimilarKanjiIndex.parseTsv(StringReader("拉\t提\tfixture\n拉\t謎\tfixture\n"))
        val rows = listOf(dashboardRow("拉"), dashboardRow("提"), dashboardRow("謎"))
        val savedStore = LocalStore(context)
        savedStore.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList<RecordsImportModels.SuspendedImport>(),
            rows,
            RecordsSyncModels.Settings.kikuDefaults(),
            LocalStoreBase.SyncTiming(1_000L, 2_000L),
            null,
            index,
        )
        store = savedStore

        var controller: org.robolectric.android.controller.ActivityController<TestMainActivity>? = null
        try {
            controller = Robolectric.buildActivity(TestMainActivity::class.java, Intent(context, TestMainActivity::class.java))
            val activity = controller.get()
            activity.store = savedStore
            activity.dictionaryLookup = DictionaryLookup.empty()
            controller.create().start().resume()
            activity.cancelPendingHomeRouteLoads()
            shadowOf(Looper.getMainLooper()).idle()

            val session = similarSession("拉", "謎")
            val card = activity.similarChoiceCardForSession(session)
            assertEquals("拉", card.targetKanji)
            assertTrue(card.choices.size >= 2)
            assertEquals(listOf("拉", "謎"), card.choices.take(2))

            val prepared = activity.prepareSessionRender(session)
            assertEquals(
                similarKanjiChoiceRecoveryDigest(card.choices),
                prepared.similarChoiceSignatureDigest,
            )
            prepared()
            shadowOf(Looper.getMainLooper()).idle()
            assertNotNull(activity.backAction)
        } finally {
            controller?.pause()?.stop()?.destroy()
            store?.close()
            store = null
        }
    }

    @Test
    fun onTheFlyFallbackChoiceIsNotPublishedAsRecoverable() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val index = SimilarKanjiIndex.parseTsv(StringReader("拉\t提\tfixture\n拉\t謎\tfixture\n"))
        val rows = listOf(dashboardRow("拉"), dashboardRow("提"), dashboardRow("謎"))
        val savedStore = LocalStore(context)
        savedStore.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList<RecordsImportModels.SuspendedImport>(),
            rows,
            RecordsSyncModels.Settings.kikuDefaults(),
            LocalStoreBase.SyncTiming(1_000L, 2_000L),
            null,
            index,
        )
        savedStore.writableDatabase.delete(
            LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE,
            "target_kanji=?",
            arrayOf("拉"),
        )
        store = savedStore
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        var controller: org.robolectric.android.controller.ActivityController<TestMainActivity>? = null
        try {
            controller = Robolectric.buildActivity(TestMainActivity::class.java, Intent(context, TestMainActivity::class.java))
            val activity = controller.get()
            activity.store = savedStore
            activity.dictionaryLookup = DictionaryLookup.empty()
            controller.create().start().resume()
            activity.cancelPendingHomeRouteLoads()
            shadowOf(Looper.getMainLooper()).idle()
            val session = similarSession("拉", "謎")

            val prepared = activity.prepareSessionRender(session)
            activity.acceptNewActiveStudySession(
                session,
                StudyPromptSource.REASON_TEXT,
                savedStore.latestSuccessfulSyncFinishedAt() ?: 0L,
                similarChoiceSignatureDigest = prepared.similarChoiceSignatureDigest,
            )

            assertNull(prepared.similarChoiceSignatureDigest)
            assertNull(activity.activeStudyRecovery())
            assertFalse(preferences.contains("snapshot"))
        } finally {
            preferences.edit().clear().commit()
            controller?.pause()?.stop()?.destroy()
            store?.close()
            store = null
        }
    }

    private fun similarSession(kanji: String, confusedWith: String? = null): RecordsSchedulerModels.StudySession {
        val item = RecordsStudyModels.StudyItem(
            kanji,
            "review",
            1_000L,
            1.0,
            2.0,
            1,
            0,
            0,
            0,
            "",
            1_000L,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji")
            .let { builder ->
                if (confusedWith == null) {
                    builder
                } else {
                    builder
                        .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
                        .adaptiveRouteStateJson(
                            AdaptiveRouteStateCodec.encode(
                                AdaptiveRouteState(
                                    activeCore = CoreSkill.RECOGNITION,
                                    activeRepairTasks = listOf(StudyTaskTypes.SIMILAR_KANJI),
                                    answerEvidence = AnswerEvidence(
                                        coreSkill = CoreSkill.RECOGNITION,
                                        failureKind = FailureKind.VISUAL_CONFUSION,
                                        confusedWith = confusedWith,
                                    ),
                                ),
                            ),
                        )
                }
            }
            .build()
        val row = dashboardRow(kanji)
        return RecordsSchedulerModels.StudySession(
            item,
            row,
            "session-token",
            StudyTaskTypes.SIMILAR_KANJI,
            false,
            "prompt text",
        )
    }

    private fun dashboardRow(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            null,
            "meaning-$kanji",
            "reading-$kanji",
            kanji,
            1,
            "reason",
            "Needs practice",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(
            Context::class.java,
            List::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(context, emptyList<Any>()) as AnkiDroidGateway
    }

    private class TestMainActivity : MainActivity() {
        override fun renderStudyForKanji(kanji: String?) {
            // No-op; this test only needs the study route to compose successfully.
        }
    }
}

package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.StudyWritingCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyWritingUiTest {
    @Test
    fun similarWritingRepairShowsContinueAnywayBeforeChecking() {
        val activity = createActivity()
        try {
            activity.writingPrimaryActionsView = WritingPrimaryActionsView(activity)
            activity.writingFallbackActionsView = WritingFallbackActionsView(activity)
            activity.activeSimilarWritingRepair = repair()
            activity.checkingWriting = true

            activity.updateResultActions()

            val primary = requireNotNull(activity.writingPrimaryActionsView).currentModel()
            val fallback = requireNotNull(activity.writingFallbackActionsView).currentModel()
            assertTrue(primary.nextVisible)
            assertEquals(StudyWritingCopy.continueAnywayLabel(), primary.nextText)
            assertFalse(primary.nextEnabled)
            assertFalse(fallback.manualOverrideVisible)
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun createActivity(): MainActivity {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        return try {
            Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(
            ApplicationProvider.getApplicationContext<Context>(),
            emptyList<Any>(),
        ) as AnkiDroidGateway
    }

    private fun repair(): RecordsImportModels.SimilarKanjiWritingRepair {
        return RecordsImportModels.SimilarKanjiWritingRepair(
            42L,
            "末",
            "未",
            "末|未",
            "末",
            "not yet",
            "pending",
            1000L,
            "active-token",
            0,
            900L,
            901L,
            0L,
        )
    }
}

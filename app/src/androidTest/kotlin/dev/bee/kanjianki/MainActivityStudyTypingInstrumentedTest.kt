package dev.bee.kanjianki

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MainActivityStudyTypingInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("kanji_anki_simple.db")
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.typing_no_anki")
        )
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(null)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
    }

    @After
    fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(null)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
        context.deleteDatabase("kanji_anki_simple.db")
    }

    @Test
    fun routedTypingMeaningInputAutoPassesAndPreservesGestureExclusion() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val row = row("裂", "split", "レツ")
                val correct = sessionWithToken("裂", row, "typing-correct")
                activity.activeStudyPlan = RecordsSchedulerModels.AdaptiveLoadPlan(
                    20,
                    1,
                    1,
                    listOf("裂"),
                    0,
                    false,
                    "One left"
                )
                activity.activeSession = correct
                activity.startActiveStudyTask(
                    activity.sessionTaskKey(correct),
                    "裂",
                    correct.taskType,
                    System.currentTimeMillis()
                )
                activity.renderSession(correct)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            typeAnswer("split")
            scenario.onActivity { activity ->
                val typingAnswerState = requireNotNull(activity.typingAnswerState)
                assertEquals("split", typingAnswerState.text.toString())
                performClickableWithText(requireNotNull(activity.findViewById(android.R.id.content)), "Reveal")
                val stats = activity.store.reviewStatsSince(0L)
                assertEquals(1, stats.total)
                assertEquals(1, stats.good)

                val row = row("裂", "split", "レツ")
                val wrong = sessionWithToken("裂", row, "typing-wrong")
                activity.activeSession = wrong
                activity.startActiveStudyTask(
                    activity.sessionTaskKey(wrong),
                    "裂",
                    wrong.taskType,
                    System.currentTimeMillis()
                )
                activity.renderSession(wrong)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val wrongInput = typeAnswer("wrong")
            val inputBounds = wrongInput.visibleBounds
            scenario.onActivity { activity ->
                val typingAnswerState = requireNotNull(activity.typingAnswerState)
                assertEquals("wrong", typingAnswerState.text.toString())
                val inputCenterX = inputBounds.exactCenterX()
                val inputCenterY = inputBounds.exactCenterY()
                assertTrue(typingAnswerState.containsWindowPoint(inputCenterX, inputCenterY))
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_DOWN, inputCenterX, inputCenterY)))
                assertFalse(activity.flashcardTouchTracking)
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_UP, inputCenterX, inputCenterY)))
                assertFalse(activity.flashcardTouchTracking)

                val root = requireNotNull(activity.findViewById<View>(android.R.id.content))
                performClickableWithText(root, "Reveal")
                assertTrue(activity.flashcardAnswerRevealed)
                assertTrue(containsText(root, "split"))
                assertTrue(containsText(root, "Fail"))
                assertTrue(containsText(root, MainActivityBase.LABEL_PASS))
                val stats = activity.store.reviewStatsSince(0L)
                assertEquals(1, stats.total)
                assertEquals(1, stats.good)
            }
        }
    }

    private fun typeAnswer(text: String): UiObject2 {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val input = requireNotNull(device.wait(Until.findObject(By.clazz(EditText::class.java.name)), 3_000L))
        input.setText(text)
        device.waitForIdle(2_000L)
        return input
    }

    private fun sessionWithToken(
        kanji: String,
        row: RecordsImportModels.DashboardRow,
        token: String,
    ): RecordsSchedulerModels.StudySession {
        val item = RecordsStudyModels.StudyItem(
            kanji,
            "review",
            0L,
            1.0,
            5.0,
            1,
            0,
            0,
            1,
            0,
            0,
            0,
            0L,
            false,
            "",
            0L,
            0,
            "sig",
            token,
            0L
        )
        return RecordsSchedulerModels.StudySession(item, row, token, BridgeScheduler.TASK_TYPE_MEANING, false, row.primaryMeaning)
    }

    private fun row(kanji: String, meaning: String, reading: String): RecordsImportModels.DashboardRow =
        RecordsImportModels.DashboardRow(
            kanji,
            1000,
            meaning,
            reading,
            kanji,
            10,
            "reason",
            "reason text",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>()
        )

    private fun motion(action: Int, x: Float, y: Float): MotionEvent = MotionEvent.obtain(0L, 0L, action, x, y, 0)

    private fun performClickableWithText(root: View, label: String) {
        val clickableView = findClickableWithText(root, label)
        if (clickableView != null) {
            clickableView.performClick()
        } else {
            val clickableObject = requireNotNull(findDeviceClickableTextNow(label)) {
                "Missing clickable text: $label"
            }
            clickableObject.click()
        }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2_000L)
    }

    private fun findClickableWithText(view: View, label: String): View? {
        if (view.isClickable && containsText(view, label)) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findClickableWithText(view.getChildAt(i), label)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun containsText(view: View, expected: String): Boolean {
        if (view is TextView) {
            if (expected.contentEquals(view.text)) {
                return true
            }
        }
        if (view is androidx.compose.ui.platform.ComposeView && containsAccessibilityText(view.createAccessibilityNodeInfo(), expected)) {
            return true
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsText(view.getChildAt(i), expected)) {
                    return true
                }
            }
        }
        return false
    }

    private fun findDeviceClickableTextNow(label: String): UiObject2? {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        var object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).text(label)))
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).textContains(label)))
        }
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).text(label.uppercase(Locale.ROOT))))
        }
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).textContains(label.uppercase(Locale.ROOT))))
        }
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).desc(label)))
        }
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).descContains(label)))
        }
        if (object2 != null && !object2.isClickable) {
            var parent = object2.parent
            while (parent != null && parent != object2 && !parent.isClickable) {
                object2 = parent
                parent = object2.parent
            }
            if (parent?.isClickable == true) {
                object2 = parent
            }
        }
        return object2?.takeIf { it.isClickable }
    }

    private fun firstMatch(objects: List<UiObject2>): UiObject2? = objects.firstOrNull()

    private fun containsAccessibilityText(node: AccessibilityNodeInfo?, expected: String): Boolean {
        if (node == null) {
            return false
        }
        val text = node.text?.toString()
        if (text != null && expected.contentEquals(text)) {
            return true
        }
        val description = node.contentDescription?.toString()
        if (description != null && expected.contentEquals(description)) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && containsAccessibilityText(child, expected)) {
                return true
            }
        }
        return false
    }
}

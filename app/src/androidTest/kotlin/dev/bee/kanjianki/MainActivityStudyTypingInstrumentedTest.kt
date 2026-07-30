package dev.bee.kanjianki

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.NavigationCopy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStudyTypingInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        KaniTestDatabase.delete(context)
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
        KaniTestDatabase.delete(context)
    }

    @Test
    fun routedTypingMeaningInputAutoPassesAndPreservesGestureExclusion() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startTypingSession(scenario, "typing-correct")
            typeAnswer("split")
            scenario.onActivity { activity ->
                val typingAnswerState = requireNotNull(activity.typingAnswerState)
                assertEquals("split", typingAnswerState.text)
            }
            clickDeviceText("Reveal")
            // The typed answer matched, so revealing auto-passes; the review write
            // happens on the background executor, so poll instead of asserting
            // immediately on the main thread.
            pollReviewStats(scenario, expectedTotal = 1, expectedGood = 1)
            settleMainThread(scenario)

            startTypingSession(scenario, "typing-wrong")
            val wrongInput = typeAnswer("wrong")
            val inputBounds = wrongInput.visibleBounds
            scenario.onActivity { activity ->
                val typingAnswerState = requireNotNull(activity.typingAnswerState)
                assertEquals("wrong", typingAnswerState.text)
                val inputCenterX = inputBounds.exactCenterX()
                val inputCenterY = inputBounds.exactCenterY()
                assertTrue(typingAnswerState.containsWindowPoint(inputCenterX, inputCenterY))
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_DOWN, inputCenterX, inputCenterY)))
                assertFalse(activity.flashcardTouchTracking)
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_UP, inputCenterX, inputCenterY)))
                assertFalse(activity.flashcardTouchTracking)
            }
            clickDeviceText("Reveal")
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            // The revealed answer content itself is covered by the FlashcardCard
            // compose tests; here it is enough that manual grading is offered (the
            // wrong typed answer must not auto-pass).
            assertNotNull("expected the Fail rating button", device.wait(Until.findObject(By.text("Fail")), 5_000L))
            assertNotNull("expected the Pass rating button", device.findObject(By.text(MainActivityBase.LABEL_PASS)))
            scenario.onActivity { activity ->
                assertTrue(activity.flashcardAnswerRevealed)
                // A wrong typed answer never auto-passes: still just the one review.
                val stats = activity.store.reviewStatsSince(0L)
                assertEquals(1, stats.total)
                assertEquals(1, stats.good)
            }
        }
    }

    @Test
    fun typingCardKeepsKanjiVisibleAndHidesNavWhileKeyboardIsOpen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startTypingSession(scenario, "typing-keyboard")
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            // The answer field autofocuses, which is what opens the keyboard. Give the
            // accessibility tree time to settle when this test runs right after
            // another activity launch.
            device.waitForIdle(2_000L)
            requireNotNull(device.wait(Until.findObject(By.clazz(EditText::class.java.name)), 10_000L)) {
                "typing answer field never appeared"
            }
            device.waitForIdle(2_000L)

            var imeVisible = false
            var keyboardTop = 0
            scenario.onActivity { activity ->
                val rootView = requireNotNull(activity.findViewById<View>(android.R.id.content))
                val insets = rootView.rootWindowInsets
                imeVisible = insets != null &&
                    insets.isVisible(android.view.WindowInsets.Type.ime()) &&
                    insets.getInsets(android.view.WindowInsets.Type.ime()).bottom > 0
                // adjustResize shrinks the content window to end where the keyboard
                // starts, so the resized window bottom is the keyboard top.
                val location = IntArray(2)
                rootView.getLocationOnScreen(location)
                keyboardTop = location[1] + rootView.height
            }
            // Emulators with a hardware keyboard never show the IME; without it this
            // regression cannot manifest, so there is nothing to verify.
            assumeTrue("soft keyboard did not open", imeVisible)

            val glyph = requireNotNull(device.wait(Until.findObject(By.text("裂")), 3_000L)) {
                "kanji glyph pushed off-screen while the keyboard is open"
            }
            val glyphBounds = glyph.visibleBounds
            assertTrue("kanji glyph is covered by the keyboard", glyphBounds.bottom <= keyboardTop)
            assertTrue("kanji glyph is clipped at the top", glyphBounds.top >= 0)
            assertTrue("kanji glyph has no visible height", glyphBounds.height() > 0)
            // The bottom nav must yield its space to the content while typing.
            val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
            assertTrue(
                "bottom nav should hide while the keyboard is open",
                device.findObjects(By.pkg(pkg).text(NavigationCopy.homeLabel())).isEmpty()
            )
        }
    }

    private fun startTypingSession(scenario: ActivityScenario<MainActivity>, token: String) {
        scenario.onActivity { activity ->
            val row = row("裂", "split", "レツ")
            val session = sessionWithToken("裂", row, token)
            activity.activeStudyPlan = RecordsSchedulerModels.AdaptiveLoadPlan(
                20,
                1,
                1,
                listOf("裂"),
                0,
                false,
                "One left"
            )
            activity.activeSession = session
            activity.startActiveStudyTask(
                activity.sessionTaskKey(session),
                "裂",
                session.taskType,
                System.currentTimeMillis()
            )
            activity.renderSession(session)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    /**
     * Clicks a visible node with [label] from the instrumentation thread. UiAutomator
     * must never run on the app main thread (inside onActivity) - accessibility
     * queries need the app main looper free to respond.
     */
    private fun clickDeviceText(label: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val target = requireNotNull(device.wait(Until.findObject(By.text(label)), 5_000L)) {
            "Missing clickable text: $label"
        }
        target.click()
        device.waitForIdle(2_000L)
    }

    private fun pollReviewStats(scenario: ActivityScenario<MainActivity>, expectedTotal: Int, expectedGood: Int) {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        var total = -1
        var good = -1
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val stats = activity.store.reviewStatsSince(0L)
                total = stats.total
                good = stats.good
            }
            if (total == expectedTotal && good == expectedGood) {
                return
            }
            SystemClock.sleep(150L)
        }
        assertEquals(expectedTotal, total)
        assertEquals(expectedGood, good)
    }

    /**
     * Lets the post-review study-route reload land before the test replaces the
     * active session, so the reload cannot clobber the newly rendered card.
     */
    private fun settleMainThread(scenario: ActivityScenario<MainActivity>) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(500L)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun typeAnswer(text: String): UiObject2 {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val input = requireNotNull(device.wait(Until.findObject(By.clazz(EditText::class.java.name)), 10_000L)) {
            "typing answer field never appeared"
        }
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
}

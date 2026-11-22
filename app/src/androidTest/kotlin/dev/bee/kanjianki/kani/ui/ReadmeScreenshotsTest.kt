package dev.bee.kanjianki.kani.ui

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.kani.Pair2
import dev.bee.kanjianki.kani.Reveal
import dev.bee.kanjianki.kani.ScanSummary
import dev.bee.kanjianki.kani.SessionSummary
import dev.bee.kanjianki.kani.TodayData
import dev.bee.kanjianki.kani.core.Check
import dev.bee.kanjianki.kani.core.KanjiState
import dev.bee.kanjianki.kani.core.ScanAnalyzer
import dev.bee.kanjianki.kani.core.SessionItem
import dev.bee.kanjianki.kani.core.SprintTrial
import dev.bee.kanjianki.kani.core.Stage
import dev.bee.kanjianki.kani.core.Status
import dev.bee.kanjianki.kani.core.StrokeDiff
import dev.bee.kanjianki.kani.data.KaniStore
import dev.bee.kanjianki.kani.data.Reference
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders the real screens with sample data and saves a PNG of each, for the README.
 * Skipped unless run with `-e kaniScreenshots true`; see `tools/readme_screenshots.sh`.
 */
@RunWith(AndroidJUnit4::class)
class ReadmeScreenshotsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ref = Reference(context)
    private val out = File(context.getExternalFilesDir(null), "readme-screens")

    private var theme by mutableStateOf(ThemeMode.LIGHT)
    private var name by mutableStateOf("")
    private var screen by mutableStateOf<@Composable () -> Unit>({})

    @Before
    fun setUp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("kaniScreenshots") == "true")
        out.deleteRecursively()
        out.mkdirs()
        ref.warm()
        rule.runOnUiThread {
            val activity = rule.activity
            activity.enableEdgeToEdge()
            // No soft keyboard and no system bars: the shot is the screen alone.
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
        rule.setContent { KaniTheme(theme) { key(name, theme) { screen() } } }
    }

    private fun show(shot: String, mode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
        theme = mode
        name = shot
        screen = content
        rule.waitForIdle()
    }

    private fun save(shot: String = name) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$shot.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val day = 20_000

    private fun state(kanji: String, goodDays: Int, status: Status = Status.TRAINING) = KanjiState(
        kanji, status, ScanAnalyzer.Reason.LEECH, emptySet(), List(goodDays) { day - it }, null, day, 0, null, false,
    )

    private val pairs = listOf(
        Pair2("待", "持", 4, Stage.TRAINING),
        Pair2("末", "未", 3, Stage.TRAINING),
        Pair2("緑", "録", 2, Stage.WATCHING),
        Pair2("微", "徴", 2, Stage.TRAINING),
        Pair2("清", "晴", 1, Stage.WATCHING),
        Pair2("輸", "輪", 1, Stage.SOLID),
        Pair2("鳥", "島", 0, Stage.TRAINING),
        Pair2("右", "石", 0, Stage.SOLID),
    )

    @Test
    fun captureEveryScreen() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.AMOLED)) {
            val suffix = if (mode == ThemeMode.AMOLED) "-dark" else ""
            setup(mode, suffix)
            session(mode, suffix)
            sprint(mode, suffix)
            write(mode, suffix)
            home(mode, suffix)
        }
    }

    private fun setup(mode: ThemeMode, suffix: String) {
        show("welcome$suffix", mode) { WelcomeScreen {} }
        save()
        show("connect$suffix", mode) { ConnectScreen(installed = true, {}, {}, {}) }
        save()
        show("scan$suffix", mode) {
            ScanningScreen(
                scanned = 7_412,
                summary = ScanSummary(
                    wordsScanned = 7_412,
                    distinctKanji = 1_846,
                    flagged = 47,
                    reasons = mapOf(
                        ScanAnalyzer.Reason.LEECH to 21,
                        ScanAnalyzer.Reason.FAILED to 17,
                        ScanAnalyzer.Reason.SUSPENDED to 9,
                    ),
                    lookAlikes = listOf("待" to "持", "末" to "未", "緑" to "録", "微" to "徴", "清" to "晴", "輸" to "輪"),
                ),
                inSetup = true,
                onContinue = {},
                onSeeWords = {},
            )
        }
        save()
        show("words$suffix", mode) {
            WordsScreen(
                listOf(
                    WordRow("待ち合わせ", listOf("待" to Status.TRAINING, "合" to Status.CLEARED)),
                    WordRow("週末", listOf("週" to Status.CLEARED, "末" to Status.TRAINING)),
                    WordRow("緑茶", listOf("緑" to Status.TRAINING, "茶" to Status.CLEARED)),
                    WordRow("特徴", listOf("特" to Status.CLEARED, "徴" to Status.TRAINING)),
                    WordRow("清潔", listOf("清" to Status.TRAINING, "潔" to Status.TRAINING)),
                    WordRow("輸入", listOf("輸" to Status.SOLID, "入" to Status.CLEARED)),
                ),
                onBack = {},
                onCheckAnyway = {},
            )
        }
        save()
        show("daily$suffix", mode) { DailyScreen(5, {}) { _, _, _, _ -> } }
        save()
    }

    private fun session(mode: ThemeMode, suffix: String) {
        val matsu = SessionItem("末", Check.MEANING)
        show("check$suffix", mode) {
            CheckScreen(matsu, 2, 7, null, null, speakOnReveal = false, onSpeak = {}, onSubmit = { _, _, _ -> }, onNext = {}, onClose = {})
        }
        rule.onNode(hasSetTextAction()).performTextInput("not yet")
        save()

        val reading = SessionItem("待", Check.READING)
        val kitai = KaniStore.Word(1, "期待", "きたい", "expectation, anticipation, hope")
        show("check-reading$suffix", mode) {
            CheckScreen(reading, 4, 7, kitai, null, speakOnReveal = false, onSpeak = {}, onSubmit = { _, _, _ -> }, onNext = {}, onClose = {})
        }
        rule.onNode(hasSetTextAction()).performTextInput("kita")
        save()

        show("reveal$suffix", mode) {
            CheckScreen(
                reading, 4, 7, kitai, Reveal(true, "きたい", kitai, null),
                speakOnReveal = false, onSpeak = {}, onSubmit = { _, _, _ -> }, onNext = {}, onClose = {},
            )
        }
        save()

        show("contrast$suffix", mode) { ContrastScreen("末", "未", ref::meaning, ref::strokes, onTrain = {}, onSkip = {}) }
        rule.onNodeWithText("Sure").performClick()
        save()

        val seiketsu = KaniStore.Word(2, "清潔", "せいけつ", "clean, hygienic")
        show("family$suffix", mode) {
            ReadingFamilyScreen(
                "清", seiketsu, "セイ",
                listOf(FamilyMember("清", "this card"), FamilyMember("晴", "you know"), FamilyMember("精", "you know"), FamilyMember("静", "new")),
                listOf("情" to "ジョウ"),
                check = { true },
                onDone = {},
            )
        }
        rule.onNode(hasSetTextAction()).performTextInput("seike")
        save()

        show("hook$suffix", mode) {
            HookScreen(
                "微", ref.meaning("微"),
                listOf(KaniStore.Word(3, "微妙", "びみょう", "subtle, delicate"), KaniStore.Word(4, "微笑む", "ほほえむ", "to smile")),
                onSave = {}, onSkip = {},
            )
        }
        rule.onNode(hasSetTextAction()).performTextInput("A tiny mountain hides in the middle")
        save()

        show("early$suffix", mode) { EarlyScreen("侍", "待", ref::meaning, ref::strokes, onQuickCheck = {}, onGotIt = {}) }
        save()

        show("done$suffix", mode) {
            DoneScreen(
                SessionSummary(
                    seconds = 252,
                    solidNow = 2,
                    rows = listOf(
                        state("緑", 3, Status.SOLID), state("輸", 3, Status.SOLID), state("待", 2), state("清", 2),
                        state("末", 1), state("微", 1), state("徴", 0),
                    ),
                    medianMs = 1_800,
                    sprintRight = 5,
                    sprintTotal = 6,
                    tagged = 3,
                ),
                ref::meaning,
                onDone = {},
            )
        }
        save()
    }

    private fun sprint(mode: ThemeMode, suffix: String) {
        val trials = listOf(SprintTrial("待", listOf("持", "待", "侍", "特"))) + List(5) { SprintTrial("持", listOf("待", "持", "侍", "寺")) }
        show("sprint-intro$suffix", mode) { SprintScreen(trials, onAnswer = { _, _ -> }, onDone = {}, onClose = {}) }
        save()
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithText("Start · 6 rounds").performClick()
            rule.mainClock.advanceTimeBy(100)
            save("sprint-flash$suffix")
            rule.mainClock.advanceTimeBy(700)
            save("sprint-mask$suffix")
            rule.mainClock.advanceTimeBy(300)
            save("sprint-pick$suffix")
            rule.onNodeWithText("持").performClick()
            rule.mainClock.advanceTimeBy(100)
            save("sprint-feedback$suffix")
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    private fun write(mode: ThemeMode, suffix: String) {
        val target = ref.strokes("待").orEmpty()
        val other = ref.strokes("持").orEmpty()
        show("write-empty$suffix", mode) { WriteScreen("待", "持", target, other, onDone = {}) }
        save()

        // The canvas is the full-width square under the instruction line.
        val root = rule.onRoot().fetchSemanticsNode()
        val hint = rule.onNodeWithText("Draw the missing strokes, then tap Check.").fetchSemanticsNode()
        val density = root.layoutInfo.density.density
        val left = 24.dp.value * density
        val side = root.size.width - 2 * left
        val top = hint.boundsInRoot.bottom + 16.dp.value * density
        val deciding = StrokeDiff.decidingStrokes(target, other).sorted().map { target[it] }
        for (stroke in deciding) {
            rule.onRoot().performTouchInput {
                val points = stroke.map { Offset(left + it.x * side, top + it.y * side) }
                down(points.first())
                // Enough small moves to pass touch slop near the start of the stroke.
                val first = points.first()
                val second = points[1]
                for (i in 1..4) moveTo(Offset(first.x + (second.x - first.x) * i / 4f, first.y + (second.y - first.y) * i / 4f))
                points.drop(2).forEach { moveTo(it) }
                up()
            }
        }
        save("write-drawn$suffix")
        rule.onNodeWithText("Check").performClick()
        save("write$suffix")
    }

    private fun home(mode: ThemeMode, suffix: String) {
        show("today$suffix", mode) { TodayScreen(TodayData(7, pairs.take(3), 38, 47), {}, {}, {}, {}) }
        save()
        show("blind-spots$suffix", mode) { BlindSpotsScreen(pairs, onBack = {}, onSprint = {}) }
        save()
        show("settings$suffix", mode) {
            SettingsScreen(
                SettingsState(mode, 5, reminder = true, handwriting = true, tag = true, speak = true, version = "1.0", memory = "RWKV SM92 · 214 answers"),
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        save()
    }
}

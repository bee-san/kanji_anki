package dev.bee.kanjianki.kani

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.bee.kanjianki.kani.anki.AnkiSource
import dev.bee.kanjianki.kani.ui.BlindSpotsScreen
import dev.bee.kanjianki.kani.ui.CardsScreen
import dev.bee.kanjianki.kani.ui.CheckScreen
import dev.bee.kanjianki.kani.ui.ConnectScreen
import dev.bee.kanjianki.kani.ui.ContrastScreen
import dev.bee.kanjianki.kani.ui.DailyScreen
import dev.bee.kanjianki.kani.ui.DoneScreen
import dev.bee.kanjianki.kani.ui.EarlyScreen
import dev.bee.kanjianki.kani.ui.HookScreen
import dev.bee.kanjianki.kani.ui.KaSeal
import dev.bee.kanjianki.kani.ui.Kani
import dev.bee.kanjianki.kani.ui.KaniTheme
import dev.bee.kanjianki.kani.ui.ReadingFamilyScreen
import dev.bee.kanjianki.kani.ui.ScanningScreen
import dev.bee.kanjianki.kani.ui.SettingsScreen
import dev.bee.kanjianki.kani.ui.SprintScreen
import dev.bee.kanjianki.kani.ui.TodayScreen
import dev.bee.kanjianki.kani.ui.WelcomeScreen
import dev.bee.kanjianki.kani.ui.WordsScreen
import dev.bee.kanjianki.kani.ui.WriteScreen
import java.util.Locale

class KaniActivity : ComponentActivity() {
    private val vm: KaniViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val ankiPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onPermissionResult()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, theme change) still holds the launch intent; only a
        // fresh launch should act on it.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            KaniTheme(vm.themeMode) {
                val c = Kani.colors
                LaunchedEffect(c.isDark) {
                    val bar = c.bg.toArgb()
                    enableEdgeToEdge(
                        statusBarStyle = if (c.isDark) SystemBarStyle.dark(bar) else SystemBarStyle.light(bar, bar),
                        navigationBarStyle = if (c.isDark) SystemBarStyle.dark(bar) else SystemBarStyle.light(bar, bar),
                    )
                }
                BackHandler(enabled = vm.screen !is Screen.Today && vm.screen !is Screen.Welcome) { vm.back() }
                Box(Modifier.fillMaxSize().background(c.bg)) {
                    AnimatedContent(
                        targetState = vm.screen,
                        contentKey = { it::class },
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                        label = "screen",
                    ) { screen -> Route(screen) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SPRINT, false) == true) vm.requestSprint()
    }

    override fun onStart() {
        super.onStart()
        vm.onReturn()
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onStop() {
        super.onStop()
        // Turning the screen is not leaving.
        if (!isChangingConfigurations) vm.onLeave()
    }

    private fun installUpdate() {
        if (!vm.installUpdate()) {
            try {
                startActivity(vm.allowInstallsIntent())
            } catch (_: ActivityNotFoundException) {
                // No settings screen to send the user to; the banner stays for another try.
            }
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun speak(text: String) {
        val engine = tts
        if (engine == null) {
            tts = TextToSpeech(applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.JAPANESE
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kani")
                }
            }
            return
        }
        if (ttsReady) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kani")
    }

    private fun requestAnki() {
        val permission = vm.anki.permission() ?: return
        ankiPermission.launch(permission)
    }

    private fun openAnkiDroidStore() {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=com.ichi2.anki".toUri())
        try {
            startActivity(market)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/ankidroid/Anki-Android/releases".toUri()))
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Composable
    private fun Route(screen: Screen) {
        val ref = vm.ref
        when (screen) {
            Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KaSeal(72.dp) }
            Screen.Welcome -> WelcomeScreen(onStart = vm::startSetup)
            Screen.Connect -> ConnectScreen(
                installed = vm.anki.state() != AnkiSource.State.NOT_INSTALLED,
                onAllow = ::requestAnki,
                onInstall = ::openAnkiDroidStore,
                onBack = { vm.back() },
            )
            is Screen.Cards -> CardsScreen(
                screen.detected,
                screen.error,
                onConfirm = vm::confirmCards,
                onRetry = vm::changeCards,
                onBack = { vm.back() },
            )
            is Screen.Scanning -> ScanningScreen(screen.scanned, screen.result, !vm.prefs.setupDone, onContinue = vm::afterScan, onSeeWords = { vm.openWords(screen) })
            Screen.Daily -> DailyScreen(vm.prefs.minutes, onBack = { vm.back() }) { minutes, reminder, handwriting, tag ->
                if (reminder) askForNotifications()
                vm.finishSetup(minutes, reminder, handwriting, tag, startNow = true)
            }
            Screen.Today -> TodayScreen(
                vm.today,
                update = vm.update,
                onUpdate = ::installUpdate,
                onStart = vm::startSession,
                onSprint = vm::sprintBlindSpots,
                onBlindSpots = { vm.open(Screen.BlindSpots) },
                onSettings = { vm.open(Screen.Settings) },
            )
            is Screen.CheckScreen -> {
                val (index, total) = vm.position()
                CheckScreen(
                    screen.item, index, total, screen.word, screen.reveal,
                    speakOnReveal = vm.prefs.speak,
                    onSpeak = ::speak,
                    onSubmit = vm::submit,
                    onNext = vm::afterReveal,
                    onClose = vm::endSession,
                )
            }
            is Screen.Contrast -> ContrastScreen(
                screen.target, screen.picked, ref::meaning, ref::strokes,
                onTrain = { sure -> vm.contrastTrain(screen.target, screen.picked, sure) },
                onSkip = vm::contrastSkip,
            )
            is Screen.Sprint -> SprintScreen(screen.trials, onAnswer = vm::sprintAnswer, onDone = vm::sprintDone, onClose = vm::sprintClose)
            is Screen.Write -> WriteScreen(
                screen.target, screen.other,
                ref.strokes(screen.target).orEmpty(), ref.strokes(screen.other).orEmpty(),
                onDone = vm::writeDone,
            )
            is Screen.ReadingFamily -> {
                val family = remember(screen) { vm.family(screen.kanji, screen.word) }
                ReadingFamilyScreen(
                    screen.kanji, screen.word, family.sound, family.members, family.watchFor,
                    check = { vm.readingRetry(screen.word, it) },
                    onDone = vm::readingDone,
                )
            }
            is Screen.Hook -> HookScreen(
                screen.kanji, ref.meaning(screen.kanji), remember(screen) { vm.store.wordsWith(screen.kanji, 2) },
                onSave = { vm.saveHook(screen.kanji, it) },
                onSkip = { vm.saveHook(screen.kanji, null) },
            )
            is Screen.Early -> EarlyScreen(
                screen.newKanji, screen.known, ref::meaning, ref::strokes,
                onQuickCheck = { vm.earlyDone(screen.newKanji, screen.known, quickCheck = true) },
                onGotIt = { vm.earlyDone(screen.newKanji, screen.known, quickCheck = false) },
            )
            is Screen.Done -> DoneScreen(screen.summary, ref::meaning, onDone = vm::goToday)
            Screen.BlindSpots -> BlindSpotsScreen(remember { vm.pairs() }, onBack = vm::goToday, onSprint = vm::sprintBlindSpots)
            is Screen.Words -> WordsScreen(vm.wordRows, onBack = { vm.back() }, onCheckAnyway = vm::checkAnyway)
            Screen.Settings -> SettingsScreen(
                vm.settings.copy(memory = vm.memoryLine()),
                onBack = vm::goToday,
                onTheme = { mode -> vm.updateSettings { theme = mode } },
                onMinutes = { m -> vm.updateSettings { minutes = m } },
                onReminder = {
                    if (!vm.settings.reminder) askForNotifications()
                    vm.updateSettings { reminder = !reminder }
                },
                onHandwriting = { vm.updateSettings { handwriting = !handwriting } },
                onTag = { vm.updateSettings { tagInAnki = !tagInAnki } },
                onSpeak = { vm.updateSettings { speak = !speak } },
                onRescan = { vm.rescan() },
                onChangeCards = vm::changeCards,
                onWords = { vm.openWords(Screen.Settings) },
            )
        }
    }

    companion object {
        const val EXTRA_SPRINT = "dev.bee.kanjianki.kani.SPRINT"
    }
}

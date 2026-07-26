package dev.bee.kanjianki.baseline

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.MainActivity
import dev.bee.kanjianki.MainActivityBase
import dev.bee.kanjianki.MainActivityRuntimeOverrides
import dev.bee.kanjianki.TestRecords
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.testing.DeviceRisk
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Goal165AndroidRouteBaselineInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var originalLocale: Locale
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        originalLocale = Locale.getDefault()
        originalTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.forLanguageTag(LOCALE_TAG))
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        resetRuntimeState()
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
        if (::context.isInitialized) {
            context.deleteDatabase(DATABASE_NAME)
        }
        if (::originalLocale.isInitialized) Locale.setDefault(originalLocale)
        if (::originalTimeZone.isInitialized) TimeZone.setDefault(originalTimeZone)
    }

    /**
     * Executed only by ci/scripts/run_goal165_ui_baselines.sh, which provisions
     * the device and caps every instrumentation process at twelve Activity
     * launches. Ordinary connected suites skip this method.
     *
     * Record additionally passes:
     *   -e goal165RecordBaselines true
     */
    @Test
    fun capturesOrComparesCatalogShard() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Goal 165 UI baselines run only through the sharded host gate",
            arguments.getString(RUN_ARGUMENT)?.toBooleanStrictOrNull() == true,
        )
        val record = arguments.getString(RECORD_ARGUMENT)?.equals("true", ignoreCase = true) == true
        val fontScale = requireNotNull(arguments.getString(FONT_SCALE_ARGUMENT)?.toFloatOrNull()) {
            "Missing -e $FONT_SCALE_ARGUMENT"
        }
        require(fontScale == 1f || fontScale == 2f) {
            "$FONT_SCALE_ARGUMENT must be 1.0 or 2.0: $fontScale"
        }
        val shardIndex = requireNotNull(arguments.getString(SHARD_INDEX_ARGUMENT)?.toIntOrNull()) {
            "Missing -e $SHARD_INDEX_ARGUMENT"
        }
        val shardCount = requireNotNull(arguments.getString(SHARD_COUNT_ARGUMENT)?.toIntOrNull()) {
            "Missing -e $SHARD_COUNT_ARGUMENT"
        }
        require(shardCount > 0) { "$SHARD_COUNT_ARGUMENT must be positive" }
        require(shardIndex in 0 until shardCount) {
            "$SHARD_INDEX_ARGUMENT must be in 0 until $shardCount: $shardIndex"
        }
        val scaleCases = AndroidRouteBaselineCatalog.captureCases.filter {
            it.fontScale == fontScale
        }
        val shardCases = scaleCases.filterIndexed { index, _ ->
            index % shardCount == shardIndex
        }
        require(shardCases.isNotEmpty()) {
            "Shard $shardIndex/$shardCount selected no $fontScale cases"
        }
        require(shardCases.size <= MAX_CASES_PER_SHARD) {
            "Shard $shardIndex/$shardCount selected ${shardCases.size} cases; " +
                "maximum is $MAX_CASES_PER_SHARD"
        }
        val executed = mutableListOf<String>()

        shardCases.forEach { captureCase ->
            prepareDatabase(captureCase)
            ActivityScenario.launch<MainActivity>(launchIntent(captureCase)).use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(
                        "${captureCase.id} display width",
                        VIEWPORT_WIDTH,
                        activity.resources.displayMetrics.widthPixels,
                    )
                    assertEquals(
                        "${captureCase.id} display height",
                        VIEWPORT_HEIGHT,
                        activity.resources.displayMetrics.heightPixels,
                    )
                    assertEquals(
                        "${captureCase.id} density",
                        VIEWPORT_DENSITY_DPI,
                        activity.resources.displayMetrics.densityDpi,
                    )
                    assertEquals(
                        "${captureCase.id} font scale",
                        captureCase.fontScale,
                        activity.resources.configuration.fontScale,
                        0.01f,
                    )
                    captureCase.renderer.render(activity, captureCase)
                }
                waitForReadiness(captureCase)
                composeRule.waitForIdle()
                val root = composeRule.onRoot(useUnmergedTree = true)
                val semantics = AndroidGoldenAssertions.normalizedSemantics(root)
                AndroidGoldenAssertions.assertSemanticAnchors(captureCase, semantics)
                val bitmap = AndroidGoldenAssertions.captureBitmap(root)
                AndroidGoldenAssertions.assertViewport(
                    captureCase,
                    bitmap,
                    VIEWPORT_WIDTH,
                    VIEWPORT_HEIGHT,
                )
                if (record) {
                    val output = AndroidGoldenAssertions.recordActual(
                        context,
                        captureCase,
                        bitmap,
                        semantics,
                    )
                    Log.i(TAG, "Recorded ${captureCase.id} under ${output.absolutePath}")
                } else {
                    AndroidGoldenAssertions.assertSemanticsGolden(
                        InstrumentationRegistry.getInstrumentation().context,
                        captureCase,
                        semantics,
                    )
                    AndroidGoldenAssertions.assertImageGolden(
                        context,
                        InstrumentationRegistry.getInstrumentation().context,
                        captureCase,
                        bitmap,
                    )
                }
                executed += captureCase.id
            }
            context.deleteDatabase(DATABASE_NAME)
        }

        assertEquals(shardCases.map { it.id }, executed)
    }

    @Test
    @DeviceRisk
    fun routeAndStateCatalogMatchesCheckedInContract() {
        val expected = InstrumentationRegistry.getInstrumentation().context.assets
            .open("goal165/ui/route-state-catalog.snapshot.txt")
            .use { input -> String(input.readBytes(), StandardCharsets.UTF_8).trimEnd() }

        assertEquals(expected, AndroidRouteBaselineCatalog.renderContract())
    }

    @Test
    @DeviceRisk
    fun catalogCoversEveryDurableRouteAtNormalAndAccessibilityFontScale() {
        assertEquals(19, AndroidRouteBaselineCatalog.durableRoutes.size)
        AndroidRouteBaselineCatalog.durableRoutes.forEach { route ->
            val cases = AndroidRouteBaselineCatalog.captureCases.filter {
                it.routeKey == route.key && it.state == "data"
            }
            assertEquals("${route.key} font-scale matrix", listOf(1f, 2f), cases.map { it.fontScale })
            assertTrue(cases.all { it.imageGolden.endsWith(".png") })
            assertTrue(cases.all { it.semanticsGolden.endsWith(".txt") })
        }
    }

    @Test
    @DeviceRisk
    fun semanticsNormalizerRemovesBoundsAndGeneratedNodeIds() {
        val raw = """
            Node #42 at (l=0.0, t=12.0, r=360.0, b=640.0)px
              Role = 'Button'
              nodeId=9182
              Shape = 'androidx.compose.foundation.VerticalScrollableClipShape@9b1d45b'
              Text = '[Study]'
        """.trimIndent()

        assertEquals(
            """
                Node
                  Role = 'Button'
                  nodeId=<id>
                  Shape = 'androidx.compose.foundation.VerticalScrollableClipShape@<id>'
                  Text = '[Study]'
            """.trimIndent(),
            AndroidGoldenAssertions.normalizeSemantics(raw),
        )
    }

    @Test
    @DeviceRisk
    fun pixelHarnessUsesPerChannelToleranceAndReportsDeterministicDiff() {
        val expected = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(10, 20, 30))
            setPixel(1, 0, Color.rgb(40, 50, 60))
        }
        val actual = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(11, 21, 31))
            setPixel(1, 0, Color.rgb(40, 50, 70))
        }

        val diff = AndroidGoldenAssertions.compare(actual, expected, perChannelTolerance = 2)

        assertEquals(1, diff.mismatchedPixels)
        assertEquals(10, diff.maximumChannelDelta)
        assertEquals(0.5, diff.mismatchFraction, 0.0)
    }

    @Test
    @DeviceRisk
    fun checkedInGoldensHaveOnlyDocumentedAliases() {
        assertEquals(63, AndroidRouteBaselineCatalog.captureCases.size)
        assertEquals(
            setOf(setOf("study-active-fs100", "study-data-fs100")),
            duplicateAssetGroups { it.imageGolden },
        )
        assertEquals(
            setOf(
                setOf("study-active-fs100", "study-data-fs100"),
                // Bounds and generated ids are normalized, so this route's text
                // semantics are scale-independent even though its pixels differ.
                setOf("recent-mistakes-data-fs100", "recent-mistakes-data-fs200"),
            ),
            duplicateAssetGroups { it.semanticsGolden },
        )
        AndroidRouteBaselineCatalog.durableRoutes.forEach { route ->
            val normal = AndroidRouteBaselineCatalog.captureCases.single {
                it.routeKey == route.key && it.state == "data" && it.fontScale == 1f
            }
            val accessible = AndroidRouteBaselineCatalog.captureCases.single {
                it.routeKey == route.key && it.state == "data" && it.fontScale == 2f
            }
            assertTrue(
                "${route.key} 1.0x and 2.0x image goldens must differ",
                assetDigest(normal.imageGolden) != assetDigest(accessible.imageGolden),
            )
        }
    }

    private fun duplicateAssetGroups(
        path: (AndroidRouteBaselineCatalog.CaptureCase) -> String,
    ): Set<Set<String>> =
        AndroidRouteBaselineCatalog.captureCases
            .groupBy { captureCase -> assetDigest(path(captureCase)) }
            .values
            .map { captures -> captures.map { it.id }.toSet() }
            .filter { it.size > 1 }
            .toSet()

    private fun assetDigest(path: String): String {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open(path)
            .use { it.readBytes() }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun prepareDatabase(captureCase: AndroidRouteBaselineCatalog.CaptureCase) {
        context.deleteDatabase(DATABASE_NAME)
        LocalStore(context).use { store ->
            store.saveAppThemeChoice(KaniThemeChoice.LIGHT)
            if (captureCase.state == "data") {
                seedRows(store)
            }
        }
    }

    private fun seedRows(store: LocalStore) {
        val rows = listOf(
            row("裂", "split", "レツ"),
            row("列", "row", "レツ"),
            row("語", "language", "ゴ"),
        )
        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(
                listOf(
                    TestRecords.kikuNote(1L, "裂語", "レツ", "split", "裂を見た。"),
                    TestRecords.kikuNote(2L, "列語", "レツ", "row", "列を見た。"),
                    TestRecords.kikuNote(3L, "語学", "ゴ", "language", "語を見た。"),
                ),
                listOf(
                    TestRecords.kikuCard(10L, 1L).build(),
                    TestRecords.kikuCard(20L, 2L).build(),
                    TestRecords.kikuCard(30L, 3L).build(),
                ),
            ),
            emptyList(),
            rows,
            RecordsSyncModels.Settings.kikuDefaults(),
            LocalStoreBase.SyncTiming(BASELINE_TIME_MILLIS, BASELINE_TIME_MILLIS),
            null,
            null,
        )
        store.saveStudyItem(
            RecordsStudyModels.StudyItem(
                "裂",
                "new",
                0L,
                0.4,
                5.0,
                0,
                0,
                0,
                0,
                null,
                0L,
            ),
        )
        store.saveReview(
            RecordsSchedulerModels.ReviewRequest(
                "裂",
                "goal165-recent-mistake",
                "again",
                false,
                false,
                false,
                0,
            ),
            "again",
            BASELINE_TIME_MILLIS,
        )
    }

    private fun row(kanji: String, meaning: String, reading: String): RecordsImportModels.DashboardRow =
        RecordsImportModels.DashboardRow(
            kanji,
            1_000,
            meaning,
            reading,
            kanji,
            10,
            "baseline",
            "baseline fixture",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )

    private fun launchIntent(captureCase: AndroidRouteBaselineCatalog.CaptureCase): Intent {
        val intent = Intent(context, MainActivity::class.java)
        val route =
            if (captureCase.renderer == AndroidRouteBaselineCatalog.Renderer.SCREENSHOT_INTENT) {
                AndroidRouteBaselineCatalog.durableRoutes
                .single { it.key == captureCase.routeKey }
                .route
            } else {
                MainActivityBase.NAV_HOME_ROUTE
            }
        intent.putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, route)
        intent.putExtra(MainActivityBase.EXTRA_SCREENSHOT_THEME, "light")
        intent.putExtra(MainActivityBase.EXTRA_SCREENSHOT_LOCALE, LOCALE_TAG)
        return intent
    }

    private fun waitForReadiness(captureCase: AndroidRouteBaselineCatalog.CaptureCase) {
        captureCase.readinessTags.forEach { tag ->
            try {
                composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
                    composeRule.onAllNodes(
                        hasTestTag(tag),
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                }
            } catch (error: ComposeTimeoutException) {
                throw AssertionError(
                    "Timed out waiting for ${captureCase.id} readiness tag: $tag",
                    error,
                )
            }
        }
        if (captureCase.readinessTags.isNotEmpty()) {
            // Tag selectors are the authoritative async-settle boundary. Text
            // anchors are asserted immediately after capture with the full
            // normalized tree in the failure message.
            return
        }
        captureCase.semanticAnchors.forEach { anchor ->
            try {
                composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
                    composeRule.onAllNodes(
                        hasText(anchor, substring = true),
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                }
            } catch (error: ComposeTimeoutException) {
                throw AssertionError(
                    "Timed out waiting for ${captureCase.id} semantic anchor: $anchor",
                    error,
                )
            }
        }
    }

    private fun resetRuntimeState() {
        context.deleteDatabase(DATABASE_NAME)
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.goal165_no_anki"),
        )
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(false)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
    }

    private companion object {
        private const val TAG = "Goal165UiBaseline"
        private const val DATABASE_NAME = "kanji_anki_simple.db"
        private const val RUN_ARGUMENT = "goal165RunBaselines"
        private const val RECORD_ARGUMENT = "goal165RecordBaselines"
        private const val FONT_SCALE_ARGUMENT = "goal165FontScale"
        private const val SHARD_INDEX_ARGUMENT = "goal165ShardIndex"
        private const val SHARD_COUNT_ARGUMENT = "goal165ShardCount"
        private const val LOCALE_TAG = "en-GB"
        private const val VIEWPORT_WIDTH = 360
        private const val VIEWPORT_HEIGHT = 640
        private const val VIEWPORT_DENSITY_DPI = 160
        private const val UI_TIMEOUT_MILLIS = 30_000L
        private const val MAX_CASES_PER_SHARD = 12
        private const val BASELINE_TIME_MILLIS = 1_700_000_000_000L
    }
}

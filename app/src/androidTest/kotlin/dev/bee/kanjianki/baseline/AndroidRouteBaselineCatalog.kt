package dev.bee.kanjianki.baseline

import dev.bee.kanjianki.MainActivityBase
import dev.bee.kanjianki.browseKanjiRowTestTag
import dev.bee.kanjianki.homeFocusQueueCardTestTag
import dev.bee.kanjianki.homeFullWidthHomeButtonTestTag
import dev.bee.kanjianki.homeRecentMistakesCardTestTag
import dev.bee.kanjianki.homeSectionActionButtonTestTag
import dev.bee.kanjianki.core.HomeTextCopy

/**
 * Deterministic inventory for Goal 165 UI captures.
 *
 * A durable destination gets a data-state image and semantics capture at both
 * normal and accessibility font scale. State-only cases are captured once at
 * normal scale; they do not create fake navigation destinations.
 */
internal object AndroidRouteBaselineCatalog {
    enum class Renderer(val contractKey: String) {
        SCREENSHOT_INTENT("screenshot-intent"),
        HOME("home"),
        HOME_FOCUS_QUEUE("home-focus-queue"),
        HOME_RECENT_MISTAKES("home-recent-mistakes"),
        HOME_BROWSE("home-browse"),
        HOME_DETAIL("home-detail"),
        HOME_READ_ONLY_DETAIL("home-read-only-detail"),
        STATS("stats"),
        STUDY("study"),
        SHARED_LOADING("shared-loading"),
        SHARED_ERROR("shared-error"),
        SETTINGS_LOADING("settings-loading"),
        SETTINGS_ERROR("settings-error"),
        GAMES("games"),
        MISSING_KANJI("missing-kanji"),
        HOME_SYNC("home-sync");

        fun render(activity: dev.bee.kanjianki.MainActivity, captureCase: CaptureCase) {
            AndroidRouteBaselineFixtureRenderer.render(this, activity, captureCase)
        }
    }

    data class DurableRoute(
        val key: String,
        val route: String,
        val renderer: Renderer,
        val semanticAnchors: List<String>,
        val readinessTags: List<String> = emptyList(),
    )

    data class StateCase(
        val key: String,
        val routeKey: String,
        val state: String,
        val renderer: Renderer,
        val semanticAnchors: List<String>,
        val readinessTags: List<String> = emptyList(),
    )

    data class CaptureCase(
        val id: String,
        val routeKey: String,
        val state: String,
        val renderer: Renderer,
        val fontScale: Float,
        val semanticAnchors: List<String>,
        val readinessTags: List<String>,
        val imageGolden: String,
        val semanticsGolden: String,
    )

    val durableRoutes: List<DurableRoute> = listOf(
        DurableRoute(
            "home",
            MainActivityBase.NAV_HOME_ROUTE,
            Renderer.HOME,
            listOf("裂"),
            listOf(HOME_DATA_READY_TAG, homeFocusQueueCardTestTag("裂")),
        ),
        DurableRoute(
            "focus-queue",
            "focus-queue",
            Renderer.HOME_FOCUS_QUEUE,
            listOf("Focus queue", "裂"),
            listOf(
                homeSectionActionButtonTestTag(HomeTextCopy.homeLabel()),
                homeFocusQueueCardTestTag("裂"),
            ),
        ),
        DurableRoute(
            "recent-mistakes",
            "recent-mistakes",
            Renderer.HOME_RECENT_MISTAKES,
            listOf("Recent mistakes", "Again · 14 Nov 2023"),
            listOf(
                homeSectionActionButtonTestTag(HomeTextCopy.homeLabel()),
                homeRecentMistakesCardTestTag("裂"),
            ),
        ),
        DurableRoute(
            "browse",
            "browse",
            Renderer.HOME_BROWSE,
            listOf("Browse Kanji", "1 kanji"),
            listOf(
                homeFullWidthHomeButtonTestTag(HomeTextCopy.homeLabel()),
                browseKanjiRowTestTag("裂"),
            ),
        ),
        DurableRoute(
            "detail",
            "detail",
            Renderer.HOME_DETAIL,
            listOf("裂", "Back to Browse", "Last seen 14 Nov 2023"),
        ),
        DurableRoute(
            "read-only-detail",
            "read-only-detail",
            Renderer.HOME_READ_ONLY_DETAIL,
            listOf("裂", "Back to Browse", "This kanji is not in your deck."),
        ),
        DurableRoute("games", "games", Renderer.SCREENSHOT_INTENT, listOf("Games")),
        DurableRoute(
            "missing-kanji",
            MainActivityBase.SCREENSHOT_MISSING_KANJI_ROUTE,
            Renderer.SCREENSHOT_INTENT,
            listOf("Missing Kanji"),
        ),
        DurableRoute("study", MainActivityBase.NAV_STUDY, Renderer.STUDY, listOf("Pass", "Fail")),
        DurableRoute("stats", MainActivityBase.NAV_STATS_ROUTE, Renderer.SCREENSHOT_INTENT, listOf("Stats overview")),
        DurableRoute("settings", MainActivityBase.NAV_SETTINGS_ROUTE, Renderer.SCREENSHOT_INTENT, listOf("Settings")),
        DurableRoute(
            "settings-import-sync",
            MainActivityBase.NAV_SETTINGS_IMPORT_SYNC_ROUTE,
            Renderer.SCREENSHOT_INTENT,
            listOf("Import & sync"),
        ),
        DurableRoute(
            "settings-study-behavior",
            MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE,
            Renderer.SCREENSHOT_INTENT,
            listOf("Study settings"),
        ),
        DurableRoute(
            "settings-automation",
            MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE,
            Renderer.SCREENSHOT_INTENT,
            listOf("Automation"),
        ),
        DurableRoute(
            "settings-appearance",
            MainActivityBase.NAV_SETTINGS_APPEARANCE_ROUTE,
            Renderer.SCREENSHOT_INTENT,
            listOf("Appearance"),
        ),
        DurableRoute(
            "settings-display-data",
            MainActivityBase.NAV_SETTINGS_DISPLAY_DATA_ROUTE,
            Renderer.SCREENSHOT_INTENT,
            listOf("Display & data"),
        ),
        DurableRoute(
            "settings-update",
            MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE,
            Renderer.SCREENSHOT_INTENT,
            listOf("App updates"),
        ),
        DurableRoute(
            "settings-licenses",
            MainActivityBase.NAV_SETTINGS_LICENSES_ROUTE,
            Renderer.SCREENSHOT_INTENT,
            listOf("Data licenses"),
        ),
        DurableRoute(
            "settings-how-kani-works",
            MainActivityBase.NAV_SETTINGS_HOW_IT_WORKS_ROUTE,
            Renderer.SCREENSHOT_INTENT,
            listOf("How Kani works"),
        ),
    )

    val stateCases: List<StateCase> = listOf(
        StateCase("shared-loading", "shared", "loading", Renderer.SHARED_LOADING, listOf("Loading")),
        StateCase("shared-error", "shared", "error", Renderer.SHARED_ERROR, listOf("Try again")),
        StateCase(
            "home-empty",
            "home",
            "empty",
            Renderer.HOME,
            listOf("Sync AnkiDroid"),
            listOf(HOME_EMPTY_READY_TAG),
        ),
        StateCase(
            "focus-empty",
            "focus-queue",
            "empty",
            Renderer.HOME_FOCUS_QUEUE,
            listOf("Focus queue", "Adaptive focus is waiting for sync"),
            listOf(homeSectionActionButtonTestTag(HomeTextCopy.homeLabel())),
        ),
        StateCase(
            "recent-empty",
            "recent-mistakes",
            "empty",
            Renderer.HOME_RECENT_MISTAKES,
            listOf("Recent mistakes", "No mistakes yet"),
            listOf(homeSectionActionButtonTestTag(HomeTextCopy.homeLabel())),
        ),
        StateCase(
            "browse-empty",
            "browse",
            "empty",
            Renderer.HOME_BROWSE,
            listOf("Browse Kanji", "No local kanji found"),
            listOf(homeFullWidthHomeButtonTestTag(HomeTextCopy.homeLabel())),
        ),
        StateCase("detail-missing", "detail", "missing", Renderer.HOME_DETAIL, listOf("Kanji not found")),
        StateCase(
            "read-only-detail-missing",
            "read-only-detail",
            "missing",
            Renderer.HOME_READ_ONLY_DETAIL,
            listOf("Kanji not found"),
        ),
        StateCase(
            "stats-empty",
            "stats",
            "empty",
            Renderer.STATS,
            listOf("Stats overview", "Your story starts here"),
        ),
        StateCase("study-loading", "study", "loading", Renderer.STUDY, listOf("Loading")),
        StateCase("study-error", "study", "error", Renderer.STUDY, listOf("Try again")),
        StateCase("study-empty", "study", "empty", Renderer.STUDY, listOf("Study")),
        StateCase("study-active", "study", "active", Renderer.STUDY, listOf("Pass", "Fail")),
        StateCase("study-done", "study", "done", Renderer.STUDY, listOf("Today's focus done")),
        StateCase("games-unavailable", "games", "unavailable", Renderer.GAMES, listOf("Needs more data")),
        StateCase("games-question", "games", "question", Renderer.GAMES, listOf("Round", "Score")),
        StateCase("games-result", "games", "result", Renderer.GAMES, listOf("Score")),
        StateCase("missing-scanning", "missing-kanji", "scanning", Renderer.MISSING_KANJI, listOf("Scanning")),
        StateCase(
            "missing-provider-error",
            "missing-kanji",
            "provider-error",
            Renderer.MISSING_KANJI,
            listOf("Scan needs attention"),
        ),
        StateCase("missing-empty", "missing-kanji", "empty", Renderer.MISSING_KANJI, listOf("0 missing")),
        StateCase("sync-skipped", "home", "sync-skipped", Renderer.HOME_SYNC, listOf("Sync already running")),
        StateCase("sync-success", "home", "sync-success", Renderer.HOME_SYNC, listOf("Sync complete")),
        StateCase(
            "sync-failure",
            "home",
            "sync-failure",
            Renderer.HOME_SYNC,
            listOf("AnkiDroid needs attention", "Try sync again"),
        ),
        StateCase("settings-loading", "settings", "loading", Renderer.SETTINGS_LOADING, listOf("Loading")),
        StateCase("settings-error", "settings", "error", Renderer.SETTINGS_ERROR, listOf("Try again")),
    )

    val captureCases: List<CaptureCase> =
        durableRoutes.flatMap { route ->
            listOf(1f, 2f).map { fontScale ->
                capture(
                    id = "${route.key}-data-fs${fontScaleToken(fontScale)}",
                    routeKey = route.key,
                    state = "data",
                    renderer = route.renderer,
                    fontScale = fontScale,
                    semanticAnchors = route.semanticAnchors,
                    readinessTags = route.readinessTags,
                )
            }
        } + stateCases.map { state ->
            capture(
                id = "${state.key}-fs100",
                routeKey = state.routeKey,
                state = state.state,
                renderer = state.renderer,
                fontScale = 1f,
                semanticAnchors = state.semanticAnchors,
                readinessTags = state.readinessTags,
            )
        }

    fun renderContract(): String = buildString {
        appendLine("goal165 android route baseline v1")
        appendLine("viewport=360x640 density=160dpi theme=light locale=en-GB")
        appendLine("durable-font-scales=1.0,2.0")
        appendLine("state-font-scale=1.0")
        appendLine()
        appendLine("[durable-routes]")
        durableRoutes.forEach { route ->
            appendLine(
                "${route.key} route=${route.route} renderer=${route.renderer.contractKey} " +
                    "anchors=${route.semanticAnchors.joinToString("|")}" +
                        readyTags(route.readinessTags),
            )
        }
        appendLine()
        appendLine("[representative-states]")
        stateCases.forEach { state ->
            appendLine(
                "${state.key} route=${state.routeKey} state=${state.state} renderer=${state.renderer.contractKey} " +
                    "anchors=${state.semanticAnchors.joinToString("|")}" +
                        readyTags(state.readinessTags),
            )
        }
        appendLine()
        appendLine("[capture-counts]")
        appendLine("durable=${durableRoutes.size}")
        appendLine("durable-images-and-semantics=${durableRoutes.size * 2}")
        appendLine("representative-images-and-semantics=${stateCases.size}")
        appendLine("total=${captureCases.size}")
    }.trimEnd()

    private fun capture(
        id: String,
        routeKey: String,
        state: String,
        renderer: Renderer,
        fontScale: Float,
        semanticAnchors: List<String>,
        readinessTags: List<String>,
    ): CaptureCase = CaptureCase(
        id = id,
        routeKey = routeKey,
        state = state,
        renderer = renderer,
        fontScale = fontScale,
        semanticAnchors = semanticAnchors,
        readinessTags = readinessTags,
        imageGolden = "goal165/ui/images/$id.png",
        semanticsGolden = "goal165/ui/semantics/$id.txt",
    )

    private fun readyTags(tags: List<String>): String =
        if (tags.isEmpty()) "" else " ready-tags=${tags.joinToString("|")}"

    private fun fontScaleToken(value: Float): Int = (value * 100).toInt()

    const val HOME_DATA_READY_TAG = "goal165-home-data-ready"
    const val HOME_EMPTY_READY_TAG = "goal165-home-empty-ready"
}

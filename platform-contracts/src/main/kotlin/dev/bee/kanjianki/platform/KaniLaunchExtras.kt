package dev.bee.kanjianki.platform

/**
 * The intent-extra names that address a screen, as durable strings.
 *
 * **Every value here is permanent.** These are baked into `PendingIntent`s that outlive the
 * process — reminder notifications, the four home-screen widgets, the update notifier — and a
 * widget a user placed months ago is never re-created. Renaming one silently turns a tap into
 * a plain launch, which reads as the feature quietly breaking rather than as a bug.
 *
 * Split out of `:app`'s `KaniLaunchIntents` so `:widget` can name them from its own module.
 * The reader that turns an `Intent` into a launch target stays in `:app`, because it needs
 * `:presentation-api`'s codec to arbitrate between simultaneously-present extras and only the
 * host has to answer that question — a widget writes extras, it never reads them.
 *
 * Here rather than in `:presentation-api` because these are Android intent extras: the
 * portable codec deals in targets, and desktop has no intents at all.
 */
object KaniLaunchExtras {
    const val EXTRA_OPEN_HOME: String = "dev.bee.kanjianki.extra.OPEN_HOME"
    const val EXTRA_OPEN_UPDATE: String = "dev.bee.kanjianki.extra.OPEN_UPDATE"
    const val EXTRA_OPEN_STUDY: String = "dev.bee.kanjianki.extra.OPEN_STUDY"
    const val EXTRA_OPEN_STATS: String = "dev.bee.kanjianki.extra.OPEN_STATS"
    const val EXTRA_OPEN_KANJI_DETAIL: String = "dev.bee.kanjianki.extra.OPEN_KANJI_DETAIL"

    const val ACTION_OPEN_STUDY: String = "dev.bee.kanjianki.action.OPEN_STUDY"
    const val ACTION_OPEN_BROWSE: String = "dev.bee.kanjianki.action.OPEN_BROWSE"
    const val ACTION_OPEN_GAMES: String = "dev.bee.kanjianki.action.OPEN_GAMES"

    /**
     * The harness extras a screenshot or benchmark run pins a screen with.
     *
     * Not carried by any `PendingIntent`, but here for the same reason: the screenshot
     * tooling passes them on the `am instrument` command line, so the strings are a contract
     * with scripts outside this codebase.
     */
    const val EXTRA_SCREENSHOT_ROUTE: String = "dev.bee.kanjianki.extra.SCREENSHOT_ROUTE"
    const val EXTRA_SCREENSHOT_THEME: String = "dev.bee.kanjianki.extra.SCREENSHOT_THEME"
    const val EXTRA_SCREENSHOT_LOCALE: String = "dev.bee.kanjianki.extra.SCREENSHOT_LOCALE"
    const val EXTRA_BENCHMARK_ROUTE: String = "dev.bee.kanjianki.extra.BENCHMARK_ROUTE"
}

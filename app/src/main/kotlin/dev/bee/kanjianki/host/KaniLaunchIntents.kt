package dev.bee.kanjianki.host

import android.content.Intent
import dev.bee.kanjianki.platform.KaniLaunchExtras
import dev.bee.kanjianki.presentation.KaniLaunchCodec

/**
 * The Android launch-intent contract: the extras and actions that name a screen.
 *
 * **Every string here is durable.** These are the extras and actions baked into
 * `PendingIntent`s that outlive the process — reminder notifications, the four home-screen
 * widgets, the update notifier, and launcher shortcuts. A `PendingIntent` created before a
 * reboot still names the old string, and a widget the user placed months ago is never
 * re-created. Renaming one silently turns a tap into a plain launch, which looks like the
 * feature quietly stopped working rather than like a bug. They keep the values they were
 * born with, and the `MainActivity*` chain reads them from here rather than the other way
 * round, so the strings survive that chain's deletion.
 *
 * This is deliberately the *only* place that knows the wire format. The precedence between
 * simultaneously-present targets is [KaniLaunchCodec]'s, shared with desktop; the mapping
 * from an `Intent` to that codec's input is here, because only Android has intents.
 */
internal object KaniLaunchIntents {
    const val ACTION_OPEN_STUDY: String = KaniLaunchExtras.ACTION_OPEN_STUDY
    const val ACTION_OPEN_BROWSE: String = KaniLaunchExtras.ACTION_OPEN_BROWSE
    const val ACTION_OPEN_GAMES: String = KaniLaunchExtras.ACTION_OPEN_GAMES

    const val EXTRA_OPEN_HOME: String = KaniLaunchExtras.EXTRA_OPEN_HOME
    const val EXTRA_OPEN_UPDATE: String = KaniLaunchExtras.EXTRA_OPEN_UPDATE
    const val EXTRA_OPEN_STUDY: String = KaniLaunchExtras.EXTRA_OPEN_STUDY
    const val EXTRA_OPEN_STATS: String = KaniLaunchExtras.EXTRA_OPEN_STATS
    const val EXTRA_OPEN_KANJI_DETAIL: String = KaniLaunchExtras.EXTRA_OPEN_KANJI_DETAIL

    /**
     * The harness extras: a screenshot or benchmark run pinning one screen.
     *
     * Not durable in the `PendingIntent` sense — no notification or widget carries these,
     * only a test runner does — but here for the same reason the rest are: they outlive the
     * `MainActivity*` chain that used to define them, and the thin host's own resume gate
     * reads them to decide whether background work is allowed. A harness run that armed an
     * alarm would leave a side effect outliving the run.
     *
     * Values unchanged from `MainActivityBase`, because the screenshot and benchmark
     * tooling passes them on the `am instrument` command line.
     */
    const val EXTRA_SCREENSHOT_ROUTE: String = KaniLaunchExtras.EXTRA_SCREENSHOT_ROUTE
    const val EXTRA_SCREENSHOT_THEME: String = KaniLaunchExtras.EXTRA_SCREENSHOT_THEME
    const val EXTRA_SCREENSHOT_LOCALE: String = KaniLaunchExtras.EXTRA_SCREENSHOT_LOCALE
    const val EXTRA_BENCHMARK_ROUTE: String = KaniLaunchExtras.EXTRA_BENCHMARK_ROUTE

    /**
     * Whether [intent] is an ordinary launch rather than a harness one.
     *
     * The gate `MainActivityStartup.backgroundStartupTasksAllowed` was: a screenshot or
     * benchmark run observes one screen, so arming alarms, re-checking updates, or posting
     * notifications during it both perturbs the measurement and leaves state behind.
     */
    fun allowsBackgroundWork(intent: Intent?): Boolean =
        intent?.getStringExtra(EXTRA_SCREENSHOT_ROUTE).isNullOrBlank() &&
            intent?.getStringExtra(EXTRA_BENCHMARK_ROUTE).isNullOrBlank()

    /**
     * Every target [intent] names, for [KaniLaunchCodec] to arbitrate between.
     *
     * A set rather than one value because an `Intent` can carry several extras at once and
     * choosing between them is the shared codec's job, not this reader's — that is what
     * keeps deep-link precedence identical on both hosts.
     */
    fun targetsIn(intent: Intent?): Set<KaniLaunchCodec.Target> {
        intent ?: return emptySet()
        return buildSet {
            // `hasExtra`, not a parsed glyph: an unusable kanji is still a request to open a
            // card, and it must fall back to Home rather than resuming study the way an
            // ordinary launch would.
            if (intent.hasExtra(EXTRA_OPEN_KANJI_DETAIL)) add(KaniLaunchCodec.Target.KANJI_DETAIL)
            if (intent.getBooleanExtra(EXTRA_OPEN_STUDY, false)) add(KaniLaunchCodec.Target.STUDY)
            if (intent.getBooleanExtra(EXTRA_OPEN_UPDATE, false)) add(KaniLaunchCodec.Target.UPDATE)
            if (intent.getBooleanExtra(EXTRA_OPEN_STATS, false)) add(KaniLaunchCodec.Target.STATS)
            if (intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) add(KaniLaunchCodec.Target.HOME)
            shortcutTarget(intent.action)?.let(::add)
        }
    }

    /**
     * The target a launcher-shortcut action names, or null for any other action.
     *
     * An allowlist, and deliberately narrow: `Intent.action` is caller-controlled, so only
     * these three may pick a screen and everything else — `ACTION_MAIN` included — falls
     * through to the ordinary launch path.
     */
    fun shortcutTarget(action: String?): KaniLaunchCodec.Target? = when (action) {
        ACTION_OPEN_STUDY -> KaniLaunchCodec.Target.STUDY
        ACTION_OPEN_BROWSE -> KaniLaunchCodec.Target.BROWSE
        ACTION_OPEN_GAMES -> KaniLaunchCodec.Target.GAMES
        else -> null
    }

    /** The kanji [intent] asks to open, unnormalized — the caller decides what is usable. */
    fun kanjiIn(intent: Intent?): String? =
        runCatching { intent?.getStringExtra(EXTRA_OPEN_KANJI_DETAIL) }.getOrNull()
}

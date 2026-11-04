package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.ExternalNavigator
import java.net.URI

/**
 * Desktop's [ExternalNavigator]: hands a URL to the user's browser and asks Anki
 * to open its own card browser.
 *
 * **Only `https` and `http` are opened, and the host must be present.** Everything
 * a desktop browser-open call reaches is a shell-level "open this with whatever
 * handles it", so an unfiltered scheme is a command-execution surface: `file:` reads
 * local files, and on Windows a handler-registered scheme can launch a program with
 * attacker-influenced arguments. Kani's own uses are the GitHub release page, the
 * licence pages, and the AnkiConnect docs — all `https` — so nothing legitimate is
 * lost by refusing the rest. The check is a positive allowlist for the usual
 * reason: a denylist of dangerous schemes is a guess about what handlers a host has
 * registered.
 *
 * [openCollectionBrowser] is not a URL at all: it is AnkiConnect's `guiBrowse`
 * action, one of the few writes-adjacent actions Kani may call, and it only changes
 * what the user is *looking at* in Anki. It is injected as a callback because
 * `:platform-desktop` must not depend on `:provider-ankiconnect` (the reviewed DAG
 * gives this module a single edge, to `:platform-contracts`) — and because the
 * provider owns the outbound action allowlist, which is where a decision about
 * talking to Anki belongs.
 */
class DesktopExternalNavigator(
    private val browse: (URI) -> Boolean,
    private val guiBrowse: (String) -> Boolean = { false },
) : ExternalNavigator {
    override fun openUrl(uri: URI): Boolean {
        if (!isOpenableWebUrl(uri)) return false
        return runCatching { browse(uri) }.getOrDefault(false)
    }

    override fun openCollectionBrowser(query: String): Boolean {
        if (query.isBlank()) return false
        return runCatching { guiBrowse(query) }.getOrDefault(false)
    }

    companion object {
        private val OPENABLE_SCHEMES = setOf("https", "http")

        /**
         * Whether [uri] is a web URL safe to hand to the host's URL handler.
         *
         * Requires an absolute URI with an allowlisted scheme and a non-blank host.
         * The host requirement matters independently of the scheme: `http:/etc/passwd`
         * parses with a null host, and what a platform handler does with a
         * hostless web URL is not something to find out in production.
         */
        fun isOpenableWebUrl(uri: URI): Boolean =
            uri.isAbsolute &&
                uri.scheme?.lowercase() in OPENABLE_SCHEMES &&
                !uri.host.isNullOrBlank()
    }
}

package dev.bee.kanjianki.host

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.bee.kanjianki.requireKaniContainer
import dev.bee.kanjianki.core.DatabaseBackupAvailabilityPolicy
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.platform.android.AndroidPlatformFileAccess
import dev.bee.kanjianki.presentation.KaniEffect
import dev.bee.kanjianki.presentation.KaniLaunchCodec
import dev.bee.kanjianki.presentation.KaniLaunchRequest
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PlatformCapability

/**
 * The thin Android host (Goal 199): one activity that owns the four host
 * responsibilities and hands everything else to the shared shell.
 *
 * Those four are the whole reason an Activity is still in the picture:
 *
 * 1. **Lifecycle** — `setContent`, and nothing else yet. The old host's `onPause`/`onResume`
 *    did four things the shared graph has no equivalent for: flushing Study recovery,
 *    resuming a paused task, re-arming reminders, and re-checking a pending update. Each is
 *    a separate port with its own state, so this host deliberately has no `onPause`
 *    override rather than a partial one — a half-restored Study task is worse than a
 *    cold-started route. The composition's own `Lifecycle.Entered`/`Exited` already reload
 *    per route.
 * 2. **Activity results** — the launchers in [AndroidHostLaunchers], whose registration
 *    order is load-bearing.
 * 3. **Intent translation** — the shared [KaniLaunchCodec] for both the cold-start intent
 *    and `onNewIntent`, so deep-link precedence has one owner across both hosts.
 * 4. **Process recreation** — [AndroidHostSavedState], the `Bundle` side of the shared
 *    destination codec.
 *
 * Everything else — navigation, content, effects — is the shared graph, which is why this
 * file has no route names, no rendering, and no product logic in it. It deliberately does
 * not extend the `MainActivity*` inheritance chain (that chain is what Goal 199 removes)
 * and it is not yet the launcher activity: it runs in parallel with `MainActivity` until
 * the instrumented gate vouches for it.
 */
internal class KaniHostActivity : ComponentActivity() {
    private lateinit var hostState: AndroidHostState
    private lateinit var launchers: AndroidHostLaunchers

    /**
     * A reminder change waiting on POST_NOTIFICATIONS, surviving process death.
     *
     * Held here rather than in [AndroidHostState] because it is not presentation state:
     * the composition never reads it, and the only code that cares is the permission
     * callback that settles it. It is restored in `onCreate` and re-saved in
     * `onSaveInstanceState`, because the system can kill the activity while the
     * permission dialog is on screen — the case this field exists for.
     *
     * Nothing sets it yet, and that is the honest state of the port rather than an
     * oversight: the shared settings surface has no reminder-time action, so the only
     * code that can populate this is the Settings > Automation flow still living on the
     * old host. The save/restore path is wired and tested now because it is the *host*
     * half of that flow — the half that has to exist before the settings half can be
     * ported, and the half a process-death test can reach without a settings screen.
     */
    private var pendingReminder: AndroidHostSavedState.PendingReminder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = requireKaniContainer()
        val gateway = container.ankiDroidGateway
        pendingReminder = AndroidHostSavedState.readPendingReminder(savedInstanceState)

        val backupExport = AndroidBackupExport(
            cacheRoot = ::getCacheDir,
            databaseFile = { getDatabasePath(DatabaseBackupPolicy.DB_NAME) },
            fileAccess = AndroidPlatformFileAccess(this),
            // The store's own `VACUUM INTO`, ignoring the source path the interface
            // offers: the live database is the one the store has open, and snapshotting
            // some other file would silently export a stale copy.
            snapshotter = { _, destination -> container.localStore.snapshotInto(destination) },
            operationsAllowed = {
                DatabaseBackupAvailabilityPolicy.forAndroidApi(Build.VERSION.SDK_INT).operationsAllowed
            },
        )

        // Registered before the activity reaches STARTED, which is the framework's
        // requirement and the reason this is the first thing after reading saved state.
        launchers = AndroidHostLaunchers(
            context = this,
            caller = this,
            onAnkiPermissionResult = { hostState.requestRefresh() },
            // Only cleared, not settled: settling means telling the user whether reminders
            // will actually fire, and that copy belongs to the Settings > Automation flow
            // that has not been ported. Clearing is still correct on its own -- a stale
            // pending change must not survive into the next dialog -- and the reload picks
            // up the granted permission wherever the user is.
            onNotificationPermissionResult = {
                pendingReminder = null
                hostState.requestRefresh()
            },
            // Export writes the snapshot `beforePick` took into the chosen document; a
            // cancelled dialog arrives here as a null reference and discards it. Restore
            // has no consumer yet -- it validates and *stages* a whole-file replacement
            // that only startup may publish, so it is its own port rather than a branch.
            // The returned copy is dropped, matching the desktop handler: an effect the
            // host performs has no way back into the shell's message queue, because
            // `KaniEffect` is one-directional by design. Both hosts owe the user the
            // outcome and neither delivers it yet; the export itself is correct, and the
            // refresh re-reads the archive count so Settings shows the new snapshot.
            onFilePicked = { purpose, file ->
                if (purpose == KaniEffect.FilePurpose.BACKUP_EXPORT) {
                    backupExport.copyInto(file)
                    hostState.requestRefresh()
                }
            },
            // Android snapshots before the dialog, not after; see AndroidBackupExport.
            beforePick = { purpose ->
                purpose != KaniEffect.FilePurpose.BACKUP_EXPORT || backupExport.prepare().mayPick
            },
        )

        hostState = AndroidHostState(
            initialLaunch = decodeKaniLaunch(intent),
            restored = AndroidHostSavedState.readDestination(savedInstanceState),
        )

        val host = AndroidShellHost(
            container = container,
            providerProbe = AndroidProviderProbe.of { gateway.status() },
            effectHandler = AndroidEffectHandler(this, launchers),
            capabilities = androidHostCapabilities(),
            hostState = hostState,
            requests = object : AndroidHostRequests {
                // Asking for a permission the user already granted shows a dialog that
                // answers itself, and the permission's own name comes from the live
                // status, so the gateway decides both whether and what to ask.
                override fun requestProviderPermission() {
                    val status = gateway.status()
                    val permission = status.permission ?: return
                    if (!status.permissionGranted) {
                        launchers.requestAnkiDatabasePermission(permission)
                    }
                }

                override fun requestNotificationPermission() {
                    launchers.requestNotificationPermission()
                }
            },
        )
        setContent {
            AndroidShellScaffold(host = host)
        }
    }

    /**
     * Persists what the user is looking at, plus any in-flight reminder change.
     *
     * The destination comes from [AndroidHostState.current], which the scaffold publishes
     * on every shell change — so this runs outside composition without needing to ask a
     * composable anything, which it could not do.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        AndroidHostSavedState.writeDestination(outState, hostState.current)
        AndroidHostSavedState.writePendingReminder(outState, pendingReminder)
        super.onSaveInstanceState(outState)
    }

    /**
     * Handles a warm-launch intent — a notification, widget, or shortcut tap.
     *
     * `setIntent` first, because the intent is read back later (background-task gating
     * keys off it), then the decoded request goes to [AndroidHostState] for the
     * composition to navigate on. The old host also had to clear three restore markers
     * here; there is nothing to clear now, because the restored destination was consumed
     * once when the shell was constructed rather than left sitting in a field.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        hostState.warmLaunch(decodeKaniLaunch(intent))
    }

}

/**
 * The launch request [intent] encodes, or null for an ordinary launch.
 *
 * A pure function of the intent, and a top-level one so it can be tested without an
 * activity: every branch below is a decision about untrusted, *durable* input — extras
 * written by a widget the user placed months ago — and each is a wrong-screen bug rather
 * than a crash, which is the kind that ships.
 *
 * Two things happen here that the shared codec cannot do, and both are load-bearing:
 *
 * The kanji is **normalized** before the codec sees it. [KaniLaunchCodec] documents that
 * it does not — `TextUtil` lives in `:core`, which `:presentation-api` cannot see — so the
 * host normalizes first. Without this the codec happily builds `Detail("not-a-kanji")` and
 * the user lands on a detail screen for a card that does not exist.
 *
 * A [KaniLaunchCodec.Target.KANJI_DETAIL] whose glyph is unusable falls back to **Home, not
 * to null**. Null means "ordinary launch", and an ordinary launch resumes the study session
 * the user abandoned yesterday — so returning it here would answer a tap on a kanji widget
 * with a study session. The tap was still deliberate; only its argument was bad. This
 * mirrors the old host's `renderLaunchTarget`, which read `suppressesStudyResume` off the
 * *target* precisely so the invalid-glyph path could not disagree with the happy one, and
 * it works here for the same reason: `Target.HOME.suppressesStudyResume` is true.
 */
internal fun decodeKaniLaunch(intent: Intent?): KaniLaunchRequest? {
    val target = KaniLaunchCodec.resolve(KaniLaunchIntents.targetsIn(intent)) ?: return null
    // Read only for the one target that carries it, so a stale kanji extra riding along
    // with an OPEN_STATS intent cannot end up as the destination's argument.
    val kanji = KaniLaunchIntents.kanjiIn(intent)
        .takeIf { target == KaniLaunchCodec.Target.KANJI_DETAIL }
        ?.let(TextUtil::normalizeSingleKanji)
        ?.takeIf(String::isNotEmpty)
    return KaniLaunchCodec.request(target, kanji)
        ?: KaniLaunchCodec.request(KaniLaunchCodec.Target.HOME)
}

/**
 * The capabilities the Android host advertises.
 *
 * Android ships an on-device Japanese handwriting recognizer (ML Kit via
 * `:writing-core`), so [PlatformCapability.WRITING_RECOGNITION] is present — the one
 * capability that differs from desktop, and the reason the shared study runtime keeps
 * the writing task here rather than re-routing it. Provider capabilities are not listed
 * here: they are per-connection and come from the live [AndroidProviderProbe].
 */
internal fun androidHostCapabilities(): PlatformCapabilities =
    PlatformCapabilities.of(
        PlatformCapability.WRITING_RECOGNITION,
        PlatformCapability.BACKUP_RESTORE,
        PlatformCapability.SECRET_PERSISTENCE,
    )

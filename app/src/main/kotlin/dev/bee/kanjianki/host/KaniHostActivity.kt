package dev.bee.kanjianki.host

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import dev.bee.kanjianki.hostpresentation.AutomationSettingsStore
import dev.bee.kanjianki.AndroidKaniContainer
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.requireKaniContainer
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.update.ResumeUpdateInstaller
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
 * 1. **Lifecycle** — `setContent`, plus the work only the activity is told about: pausing
 *    and resuming the shared study task timer, and the reminder/pending-update work a
 *    return to the foreground owes ([AndroidHostResume] decides whether, this performs it).
 *    Two of the old host's `onResume` concerns are deliberately absent rather than
 *    half-ported: the Study recovery flush has no store here because the shared surfaces
 *    keep a typed draft in saved instance state instead, and the deferred settings-preview
 *    re-render has nothing to refresh because the shared Settings has no sample preview.
 * 2. **Activity results** — the launchers in [AndroidHostLaunchers], whose registration
 *    order is load-bearing.
 * 3. **Intent translation** — the shared [KaniLaunchCodec] for both the cold-start intent
 *    and `onNewIntent`, so deep-link precedence has one owner across both hosts.
 * 4. **Process recreation** — [AndroidHostSavedState], the `Bundle` side of the shared
 *    destination codec.
 *
 * Everything else — navigation, content, effects — is the shared graph, which is why this
 * file has no route names, no rendering, and no product logic in it. It does not extend the
 * `MainActivity*` inheritance chain, which is what Goal 199 removes, and it is the launcher:
 * `singleTop`, so every deep link reaches the running instance through [onNewIntent] rather
 * than stacking a second copy of the app.
 */
internal class KaniHostActivity : ComponentActivity() {
    private lateinit var hostState: AndroidHostState
    private lateinit var launchers: AndroidHostLaunchers

    /**
     * The shell host, held because the lifecycle callbacks need its study runtime.
     *
     * A field rather than a `setContent` local only for [onPause]/[onResume]: the
     * active-task timer lives in the shared runtime, and pausing it is the activity's
     * job because only the activity is told the app went away.
     */
    private lateinit var host: AndroidShellHost

    /**
     * The container, for the executor the resume work dispatches to.
     *
     * Read once in `onCreate` rather than per call: `requireKaniContainer` walks to the
     * Application and would throw during teardown, which is exactly when a late lifecycle
     * callback can arrive.
     */
    private lateinit var container: AndroidKaniContainer

    /**
     * Whether this resume owes reminder work, and whether it is throttled.
     *
     * Stateful across resumes — it holds the last re-arm time — so it is a field rather
     * than something built per callback.
     */
    private val resume = AndroidHostResume(
        // Read through [KaniLaunchIntents] rather than the `MainActivityStartup` helper that
        // used to answer this, so the gate outlives that chain's deletion.
        backgroundWorkAllowed = { KaniLaunchIntents.allowsBackgroundWork(intent) },
    )

    /**
     * Installs an already-downloaded, verified update when the user returns.
     *
     * Reused unchanged from the old host: it was already Activity-free, taking its
     * permission check, status read, executor, and install call as lambdas, so the thin
     * host shares the install policy rather than reimplementing it.
     */
    private val resumeUpdateInstaller by lazy {
        ResumeUpdateInstaller(
            { canRequestPackageInstalls(this) },
            { container.localStore.autoUpdateStatus() },
            container.maintenanceExecutor,
        ) {
            GitHubUpdater(this).installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)
        }
    }

    /**
     * A reminder change waiting on POST_NOTIFICATIONS, surviving process death.
     *
     * Held here rather than in [AndroidHostState] because it is not presentation state:
     * the composition never reads it, and the only code that cares is the permission
     * callback that settles it. It is restored in `onCreate` and re-saved in
     * `onSaveInstanceState`, because the system can kill the activity while the
     * permission dialog is on screen — the case this field exists for.
     *
     * Set when an Automation write asks for POST_NOTIFICATIONS and settled by the
     * permission callback: a grant arms the reminder for real, a denial leaves the setting
     * saved for the section to report as blocked.
     */
    private var pendingReminder: AndroidHostSavedState.PendingReminder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = requireKaniContainer()
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
            // Settling the parked change now that Automation reports blocked state: on a
            // grant the reminder the user saved is armed for real, and on a denial it stays
            // saved and the section says "Blocked" rather than claiming a time it cannot
            // keep. Cleared either way, so a stale change cannot survive into a later
            // dialog, and the reload picks up whichever answer arrived.
            onNotificationPermissionResult = { granted ->
                val parked = pendingReminder
                pendingReminder = null
                if (granted && parked?.enabled == true) {
                    ReminderScheduler.ensureNotificationChannel(this)
                    container.maintenanceExecutor.execute {
                        runCatching { ReminderScheduler.schedule(this) }
                    }
                }
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

        host = AndroidShellHost(
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

                override fun requestNotificationPermissionIfNeeded() {
                    if (!NotificationPermissionPolicy.shouldRequest(
                            apiLevel = Build.VERSION.SDK_INT,
                            granted = ContextCompat.checkSelfPermission(
                                this@KaniHostActivity,
                                AndroidHostLaunchers.PERMISSION_POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED,
                        )
                    ) {
                        return
                    }
                    // Parked before the dialog, because the system can kill this activity
                    // while it is on screen: the reminder state the user just saved is what
                    // the permission callback has to settle, and it has to survive that.
                    pendingReminder = AutomationSettingsStore.read(container.deviceSettingsStore)
                        .let {
                            AndroidHostSavedState.PendingReminder(
                                enabled = it.reminderEnabled,
                                hour = it.reminderHour,
                                minute = it.reminderMinute,
                            )
                        }
                    launchers.requestNotificationPermission()
                }
            },
        )
        setContent {
            AndroidShellScaffold(host = host)
        }
    }

    /**
     * Freezes the visible study task's timer.
     *
     * The one piece of the old host's `onPause` that is now portable: the shared
     * [dev.bee.kanjianki.StudyRuntime] owns the active-task timer, so time spent with the
     * app backgrounded is excluded from the card's active elapsed measure rather than
     * counted as time spent studying it.
     *
     * Deliberately not a partial port of the rest. `MainActivityBase.onPause` also flushes
     * Study recovery, which the shared runtime has no store for yet — see the KDoc above —
     * and a half-restored Study task is worse than a cold-started route.
     */
    override fun onPause() {
        host.studyRuntime.pauseTask()
        super.onPause()
    }

    /**
     * Resumes the study timer, then does the background work a return to the app owes.
     *
     * The order matters: the timer resumes first because it is the user-visible one, and
     * the reminder work below dispatches to a background executor. [AndroidHostResume]
     * decides *whether* — the throttle and the screenshot/benchmark-harness gate — and this
     * only performs it, so those rules are assertable without an alarm manager.
     *
     * The pending-update check comes from [ResumeUpdateInstaller] unchanged; it was already
     * Activity-free, so the thin host reuses the same class the old one did rather than a
     * second copy of the install policy.
     */
    override fun onResume() {
        super.onResume()
        host.studyRuntime.resumeTask()
        val actions = resume.onResume()
        if (actions.cancelPostedReminder) {
            // Opening the app is the user acknowledging the reminder.
            ReminderScheduler.cancelPostedNotification(this)
        }
        if (actions.rearmReminder) {
            container.maintenanceExecutor.execute {
                runCatching { ReminderScheduler.schedule(this) }
            }
        }
        resumeUpdateInstaller.onResume()
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
 * The capabilities the thin Android host advertises.
 *
 * [PlatformCapability.WRITING_RECOGNITION] is deliberately **absent**, which is the one
 * answer here that needs justifying. Android does ship an on-device Japanese recognizer
 * (ML Kit via `:writing-core`) and the old `MainActivity` chain used it — but this host has
 * no ink surface to collect a stroke with: the shared `StudyCard.Writing` renders a prompt
 * and Pass/Fail, and the pad is Goal 196's remaining work.
 *
 * The capability is not a statement about the device, it is what
 * `StudyCapabilityPolicy.reroute` keys off. Claiming it makes the runtime present a
 * `write_kanji` card as a writing card, and on this host that card cannot accept ink — a
 * self-graded "did I write it right?" with nothing to write on. Declining it re-routes the
 * card to core recognition and records a `write_unavailable` trace, which is the same
 * honest degradation desktop takes under ADR 0005 and keeps the card studyable.
 *
 * Restore this the moment the ink surface lands, not before: the two must flip together or
 * one of them is lying to the scheduler.
 *
 * Provider capabilities are not listed here: they are per-connection and come from the live
 * [AndroidProviderProbe].
 */
internal fun androidHostCapabilities(): PlatformCapabilities =
    PlatformCapabilities.of(
        PlatformCapability.BACKUP_RESTORE,
        PlatformCapability.SECRET_PERSISTENCE,
    )

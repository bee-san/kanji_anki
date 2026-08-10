package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.hostpresentation.DesktopAutomationPlan

/**
 * The in-process automation worker (Goal 201): one timer that fires reminders and
 * periodic sync on schedule, without an OS alarm.
 *
 * The timing decision is the pure [DesktopAutomationPlan]; this is the loop around it,
 * and it stays testable by taking its scheduler as a seam. A real host passes a
 * `ScheduledExecutorService`-backed [Scheduler]; a test passes a fake that runs tasks
 * on demand, so suspend/resume, a settings change mid-wait, and re-arming are all
 * driven deterministically with no real time.
 *
 * Each wake: read the current [State], compute the plan, run whatever is due through
 * [onEvaluateReminders]/[onRunAutoSync], then re-arm to the plan's next wake. A wake
 * with no next time (both schedules disabled) arms nothing; [refresh] re-arms after a
 * settings change so turning a reminder on does not wait for a wake that was never
 * scheduled.
 */
internal class DesktopAutomationWorker(
    private val scheduler: Scheduler,
    private val clock: () -> Long,
    private val state: () -> DesktopAutomationPlan.State,
    private val onEvaluateReminders: () -> Unit,
    private val onRunAutoSync: () -> Unit,
) {
    /** A one-shot delayed scheduler and its cancellation, the worker's only OS need. */
    fun interface Scheduler {
        fun schedule(delayMillis: Long, task: () -> Unit): Cancellable
    }

    fun interface Cancellable {
        fun cancel()
    }

    private var armed: Cancellable? = null
    private var stopped = false

    /** Arms the first wake. Safe to call once at startup. */
    fun start() {
        stopped = false
        arm()
    }

    /** Re-arms from the current state, e.g. after the user changes reminder settings. */
    fun refresh() {
        if (!stopped) arm()
    }

    /** Cancels any pending wake; no further work runs until [start]/[refresh]. */
    fun stop() {
        stopped = true
        armed?.cancel()
        armed = null
    }

    private fun runWake() {
        if (stopped) return
        val plan = DesktopAutomationPlan.wake(state(), clock())
        if (plan.evaluateReminders) onEvaluateReminders()
        if (plan.runAutoSync) onRunAutoSync()
        arm()
    }

    private fun arm() {
        armed?.cancel()
        armed = null
        if (stopped) return
        val nextWake = DesktopAutomationPlan.wake(state(), clock()).nextWakeAtMillis ?: return
        // A wake time already in the past (a clock jump, a long-blocked wake) fires
        // immediately rather than scheduling a negative delay.
        val delay = (nextWake - clock()).coerceAtLeast(0L)
        armed = scheduler.schedule(delay) { runWake() }
    }
}

package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.DailyReminderDecision
import dev.bee.kanjianki.core.ReminderFamily
import dev.bee.kanjianki.platform.NotificationCategory
import dev.bee.kanjianki.platform.NotificationRequest

/**
 * Turns a [DailyReminderDecision] into the notification a host should post, or nothing.
 *
 * The scheduling decision — whether to fire, which family, the copy, the anti-spam
 * caps — is `:core`'s [dev.bee.kanjianki.core.DailyReminderDecisionPolicy], shared by
 * both hosts. This is the thin mapping from that decision to the portable
 * [NotificationRequest] the platform notification port takes, so neither host
 * re-derives it. A decision that should not schedule, or one with no family, produces
 * null: the caller posts nothing rather than inventing a notification.
 *
 * The `id` is the family name, so a later decision in the same family replaces rather
 * than stacks — a second "5 due" balloon over the first is noise, and the family is
 * exactly the anti-spam unit the caps already count.
 */
object ReminderNotificationPresenter {
    fun present(decision: DailyReminderDecision): NotificationRequest? {
        if (!decision.shouldSchedule) return null
        val family = decision.family ?: return null
        if (decision.title.isBlank() || decision.body.isBlank()) return null
        return NotificationRequest(
            id = idFor(family),
            category = categoryFor(family),
            title = decision.title,
            body = decision.body,
        )
    }

    /** The stable per-family notification id, so same-family reminders coalesce. */
    fun idFor(family: ReminderFamily): String = when (family) {
        ReminderFamily.DUE -> "kani-reminder-due"
        ReminderFamily.STREAK -> "kani-reminder-streak"
        ReminderFamily.SYNC -> "kani-reminder-sync"
    }

    private fun categoryFor(family: ReminderFamily): NotificationCategory = when (family) {
        // Due and streak are both study nudges; sync is its own category so a host can
        // route or style it separately from a "time to study" prompt.
        ReminderFamily.DUE, ReminderFamily.STREAK -> NotificationCategory.REMINDER
        ReminderFamily.SYNC -> NotificationCategory.SYNC
    }
}

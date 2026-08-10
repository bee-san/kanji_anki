package dev.bee.kanjianki.host

/**
 * Whether asking for POST_NOTIFICATIONS would do anything.
 *
 * Two ways the answer is no, and both produce the same visible symptom — no dialog — from
 * different causes, which is why they are decided here rather than inferred at the call
 * site. On API 32 and below the permission does not exist: `requestPermissions` returns
 * immediately with a grant, so the callback fires having asked the user nothing. And a
 * permission already held shows a dialog that answers itself.
 *
 * Getting this wrong is not cosmetic. The caller parks the user's in-flight reminder change
 * before launching the dialog, so a request that never shows one leaves that change parked
 * with no callback coming to settle it.
 */
internal object NotificationPermissionPolicy {
    /** The first Android version with a user-visible notification permission. */
    const val MIN_API_WITH_PERMISSION: Int = 33

    /** Whether a request would actually put a dialog in front of the user. */
    fun shouldRequest(apiLevel: Int, granted: Boolean): Boolean =
        apiLevel >= MIN_API_WITH_PERMISSION && !granted
}

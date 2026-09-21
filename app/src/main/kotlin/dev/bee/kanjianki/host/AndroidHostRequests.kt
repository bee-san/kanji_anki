package dev.bee.kanjianki.host

/**
 * The three things only a live Activity can ask the OS for, as one seam.
 *
 * Each is a *request*, not a result: the answer comes back through
 * [AndroidHostLaunchers]' callbacks, arbitrarily later and possibly to a different process.
 * Grouping them behind an interface rather than passing three lambdas is what lets a JVM
 * test drive the whole host with a recording fake — there is no `ActivityResultLauncher`
 * without an activity, so a test that cannot substitute here cannot cover any of this.
 *
 * The AnkiDroid install page is deliberately *not* here: opening a URL is
 * [dev.bee.kanjianki.shell.ShellEffectHandler.openUrl]'s job on both hosts, and routing
 * it through the tested adapter keeps the scheme allowlist in one place.
 */
internal interface AndroidHostRequests {
    /**
     * Asks for AnkiDroid's database permission if it is missing.
     *
     * Deciding whether to ask is the implementation's, not the caller's: the permission
     * name comes from the live gateway status, and re-requesting an already-granted
     * permission shows the user a dialog that answers itself.
     */
    fun requestProviderPermission()

    /**
     * Asks for POST_NOTIFICATIONS if it is missing and this OS version has it.
     *
     * Named `IfNeeded` for the same reason [requestProviderPermission] decides for itself:
     * the caller is a settings write that has already happened, and it has no business
     * knowing that the permission does not exist below API 33 or that the user may have
     * granted it months ago. Asking again for a held permission shows a dialog that answers
     * itself; asking on API 32 does nothing at all.
     */
    fun requestNotificationPermissionIfNeeded()

    companion object {
        /** Where [dev.bee.kanjianki.presentation.KaniAction.Provider.Connect] sends a user with no AnkiDroid. */
        const val PROVIDER_INSTALL_URL: String = "https://ankidroid.org/#download"

        /**
         * A host that can ask for nothing.
         *
         * For a preview or a test that only renders. Not a default anywhere a real host
         * is constructed: a Home screen whose "Allow access" button silently did nothing
         * would look like a product bug, not a missing wire.
         */
        val None: AndroidHostRequests = object : AndroidHostRequests {
            override fun requestProviderPermission() = Unit

            override fun requestNotificationPermissionIfNeeded() = Unit
        }
    }
}

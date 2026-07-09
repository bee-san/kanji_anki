package dev.bee.kanjianki

data class MainActivityShellModel(
    val selectedRoute: String = "home",
    val scrollPositionLabel: String? = null,
    val studyBadgeCount: Int? = null,
    /**
     * True while the study route is showing an unrevealed typing card, which
     * auto-focuses its answer field and force-opens the keyboard. The bottom
     * nav is hidden for this whole state (not just while the IME is visible),
     * so its ~90dp disappearance never coincides with the keyboard animation
     * — it toggles only at card boundaries (KB1).
     */
    val studyCardKeyboardResident: Boolean = false,
) {
    val routeTestTag: String
        get() = "main-route-$selectedRoute"

    val routeContentDescription: String
        get() = buildString {
            append("Kani route ")
            append(selectedRoute)
            scrollPositionLabel?.takeIf { it.isNotBlank() }?.let { label ->
                append(" scroll ")
                append(label)
            }
        }
}

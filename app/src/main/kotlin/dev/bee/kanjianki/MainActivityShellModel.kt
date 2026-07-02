package dev.bee.kanjianki

data class MainActivityShellModel(
    val selectedRoute: String = "home",
    val scrollPositionLabel: String? = null,
    val studyBadgeCount: Int? = null,
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

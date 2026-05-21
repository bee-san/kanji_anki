package dev.bee.kanjianki

data class MainActivityShellModel(
    val selectedRoute: String = "home",
) {
    val routeTestTag: String
        get() = "main-route-$selectedRoute"

    val routeContentDescription: String
        get() = "Kani route $selectedRoute"
}

package dev.bee.kanjianki

data class HomeTodayPlanModel(
    val title: String,
    val summary: String,
    val details: List<String>,
    val actionLabel: String? = null,
    val onClick: (() -> Unit)? = null,
)

internal fun homeTodayPlanTestTag(): String = "home-today-plan"

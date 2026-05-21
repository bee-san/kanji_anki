package dev.bee.kanjianki

data class HomeMetricModel(
    val iconRes: Int,
    val accent: Int,
    val label: String,
    val value: String,
    val body: String?,
    val onClick: (() -> Unit)?,
)

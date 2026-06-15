package dev.bee.kanjianki

data class HomeActionModel(
    val label: String,
    val iconRes: Int,
    val onClick: () -> Unit,
    val traceSection: String = buttonTraceSection("home-action-${label.ifBlank { "action" }}"),
)

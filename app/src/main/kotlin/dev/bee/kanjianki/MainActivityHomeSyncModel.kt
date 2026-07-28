package dev.bee.kanjianki

data class SyncResultScreenModel(
    val title: String,
    val headline: String?,
    val lines: List<String>,
    val accentColor: Int,
    val primaryLabel: String?,
    val primaryColor: Int,
    val onPrimary: Runnable?,
    val secondaryLabel: String,
    val onSecondary: Runnable,
    val additionalActions: List<SyncResultActionModel> = emptyList(),
)

data class SyncResultActionModel(
    val label: String,
    val onAction: Runnable,
)

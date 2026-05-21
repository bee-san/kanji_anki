package dev.bee.kanjianki

data class BrowseDetailPanelModel(
    val title: String,
    val lines: List<String>,
    val color: Int,
    val style: BrowseDetailPanelStyle,
)

enum class BrowseDetailPanelStyle {
    BAND,
    CARD,
}

data class BrowseDetailHeroModel(
    val kanji: String,
    val navigationLabel: String,
    val onNavigate: Runnable,
)

data class BrowseDetailIdentityModel(
    val title: String,
    val reading: String,
    val suspended: Boolean,
)

data class BrowseDetailActionsModel(
    val reviewLabel: String?,
    val onReview: Runnable?,
    val copyLabel: String?,
    val copiedLabel: String,
    val onCopy: Runnable?,
    val suspendLabel: String,
    val onSuspend: Runnable,
)

internal data class BrowseDetailScreenModel(
    val hero: BrowseDetailHeroModel,
    val identity: BrowseDetailIdentityModel,
    val reason: BrowseDetailPanelModel,
    val localInventory: BrowseDetailPanelModel?,
    val actions: BrowseDetailActionsModel,
    val timeline: MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel,
    val examplesTitle: String,
    val examples: List<BrowseExampleCardModel>,
)

data class BrowseDetailMissingModel(
    val homeLabel: String,
    val onHome: Runnable,
    val title: String,
    val body: String,
)

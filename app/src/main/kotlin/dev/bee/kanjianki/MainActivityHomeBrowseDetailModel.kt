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
    val stateBadges: List<BrowseStateBadgeModel>,
)

data class BrowseStateBadgeModel(
    val label: String,
    val color: Int,
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

data class BrowseMnemonicNoteModel(
    val title: String,
    val fieldLabel: String,
    val helper: String,
    val initialNote: String,
    val saveLabel: String,
    val onSave: (String) -> Unit,
)

data class BrowseStrokeOrderModel(
    val title: String,
    val panels: List<BrowseStrokeOrderPanelModel>,
    val overflowText: String?,
)

data class BrowseStrokeOrderPanelModel(
    val strokes: List<BrowseStrokeOrderStrokeModel>,
    val startPointX: Float?,
    val startPointY: Float?,
    val strokeNumber: Int,
)

data class BrowseStrokeOrderStrokeModel(
    val points: List<Pair<Float, Float>>,
    val highlighted: Boolean,
)

internal data class BrowseDetailScreenModel(
    val hero: BrowseDetailHeroModel,
    val identity: BrowseDetailIdentityModel,
    val strokeOrder: BrowseStrokeOrderModel?,
    val reason: BrowseDetailPanelModel,
    val localInventory: BrowseDetailPanelModel?,
    val mnemonicNote: BrowseMnemonicNoteModel,
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

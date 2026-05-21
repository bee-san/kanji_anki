package dev.bee.kanjianki

data class StatsScreenModel(
    val title: String,
    val intro: String,
    val verdict: StatsCardModel,
    val sections: List<StatsCardModel>,
)

data class StatsCardModel(
    val title: String,
    val summary: String? = null,
    val body: String? = null,
    val lines: List<StatsLineModel> = emptyList(),
    val fillColor: Int = STATS_WHITE_COLOR,
    val strokeColor: Int,
    val titleColor: Int = STATS_MUTED_COLOR,
    val summaryColor: Int = STATS_INK_COLOR,
    val bodyColor: Int = STATS_MUTED_COLOR,
    val titleSizeSp: Int = 18,
    val summarySizeSp: Int = 25,
    val bodySizeSp: Int = 15,
)

data class StatsLineModel(
    val text: String,
    val color: Int = STATS_INK_COLOR,
    val bold: Boolean = true,
    val sizeSp: Int = 16,
)

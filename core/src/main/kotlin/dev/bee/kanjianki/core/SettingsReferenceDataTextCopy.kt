package dev.bee.kanjianki.core

object SettingsReferenceDataTextCopy {
    @JvmStatic
    fun frequencyRangeTitle(): String = "Suspended card range"

    @JvmStatic
    fun frequencyRangeBody(): String {
        return "Set Jiten ranks for suspended cards, then sync."
    }

    @JvmStatic
    fun minRankLabel(): String = "Min rank"

    @JvmStatic
    fun maxRankLabel(): String = "Max rank"

    @JvmStatic
    fun minimumRankLabel(): String = "Minimum rank"

    @JvmStatic
    fun maximumRankLabel(): String = "Maximum rank"

    @JvmStatic
    fun saveFrequencyRangeLabel(): String = "Save rank range"

    @JvmStatic
    fun numericRanksToast(): String = "Use numbers for ranks."

    @JvmStatic
    fun rankRangeToast(): String = "Use ranks 1-20000."

    @JvmStatic
    fun frequencyRangeSavedToast(): String = "Range saved. Sync to refresh practice."

    @JvmStatic
    fun offlineDataLicensesTitle(): String = "Offline data licenses"

    @JvmStatic
    fun offlineDataLicensesBody(): String {
        return "Dictionary, stroke, and font credits."
    }

    @JvmStatic
    fun openDataLicensesLabel(): String = "Open data licenses"

    @JvmStatic
    fun dataLicensesTitle(): String = "Data licenses"

    @JvmStatic
    fun dataLicensesBody(): String = "Dictionary, stroke, and font attributions."

    @JvmStatic
    fun dictionaryDataTitle(): String = "Dictionary data"

    @JvmStatic
    fun strokeDataTitle(): String = "Stroke data"

    @JvmStatic
    fun fontsTitle(): String = "Fonts"
}

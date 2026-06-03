package dev.bee.kanjianki.core

object SettingsReferenceDataTextCopy {
    @JvmStatic
    fun frequencyRangeTitle(): String = "Suspended card range"

    @JvmStatic
    fun frequencyRangeBody(): String {
        return "Import suspended cards only inside this Jiten rank range. Defaults to 100-3000."
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
    fun saveFrequencyRangeLabel(): String = "Save frequency range"

    @JvmStatic
    fun numericRanksToast(): String = "Enter numeric ranks."

    @JvmStatic
    fun rankRangeToast(): String = "Use ranks from 1 to 20000."

    @JvmStatic
    fun frequencyRangeSavedToast(): String = "Suspended card range saved. Sync again to rebuild practice."

    @JvmStatic
    fun offlineDataLicensesTitle(): String = "Offline data licenses"

    @JvmStatic
    fun offlineDataLicensesBody(): String {
        return "View KANJIDIC2, Jiten, KanjiVG, and font credits."
    }

    @JvmStatic
    fun openDataLicensesLabel(): String = "Open data licenses"

    @JvmStatic
    fun dataLicensesTitle(): String = "Data licenses"

    @JvmStatic
    fun dataLicensesBody(): String = "Dictionary and stroke data bundled for offline use."

    @JvmStatic
    fun dictionaryDataTitle(): String = "Dictionary data"

    @JvmStatic
    fun strokeDataTitle(): String = "Stroke data"

    @JvmStatic
    fun fontsTitle(): String = "Fonts"
}

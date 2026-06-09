package dev.bee.kanjianki.core

object SettingsReferenceDataTextCopy {
    @JvmStatic
    fun frequencyRangeTitle(): String = "Jiten rank range"

    @JvmStatic
    fun frequencyRangeBody(): String {
        return "Set Jiten ranks to import. Default: 100-3000."
    }

    @JvmStatic
    fun minRankLabel(): String = "Most frequent rank"

    @JvmStatic
    fun maxRankLabel(): String = "Least frequent rank"

    @JvmStatic
    fun minimumRankLabel(): String = "Most frequent"

    @JvmStatic
    fun maximumRankLabel(): String = "Least frequent"

    @JvmStatic
    fun saveFrequencyRangeLabel(): String = "Save ranks"

    @JvmStatic
    fun numericRanksToast(): String = "Enter rank numbers."

    @JvmStatic
    fun rankRangeToast(): String = "Use ranks 1-20000."

    @JvmStatic
    fun frequencyRangeSavedToast(): String = "Ranks saved. Sync to refresh practice."

    @JvmStatic
    fun offlineDataLicensesTitle(): String = "Offline data licenses"

    @JvmStatic
    fun offlineDataLicensesBody(): String {
        return "Review KANJIDIC2, Jiten, KanjiVG, and font credits."
    }

    @JvmStatic
    fun openDataLicensesLabel(): String = "Open data licenses"

    @JvmStatic
    fun dataLicensesTitle(): String = "Data licenses"

    @JvmStatic
    fun dataLicensesBody(): String = "Bundled dictionaries, stroke data, and fonts."

    @JvmStatic
    fun dictionaryDataTitle(): String = "Dictionary data"

    @JvmStatic
    fun strokeDataTitle(): String = "Stroke data"

    @JvmStatic
    fun fontsTitle(): String = "Fonts"
}

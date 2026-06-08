package dev.bee.kanjianki.core

object SettingsReferenceDataTextCopy {
    @JvmStatic
    fun frequencyRangeTitle(): String = "Kanji frequency range"

    @JvmStatic
    fun frequencyRangeBody(): String {
        return "Choose Jiten ranks to suspend. Default: 100-3000."
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
    fun rankRangeToast(): String = "Ranks must be 1-20000."

    @JvmStatic
    fun frequencyRangeSavedToast(): String = "Saved. Sync to refresh practice."

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

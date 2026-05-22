package dev.bee.kanjianki.core

object SettingsReferenceDataTextCopy {
    @JvmStatic
    fun frequencyRangeTitle(): String = "Frequency range"

    @JvmStatic
    fun frequencyRangeBody(): String {
        return "Suspended cards are imported only when the kanji has a known Jiten rank inside this range. Lower ranks are more common. Default: 100-3000."
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
    fun frequencyRangeSavedToast(): String = "Frequency range saved. Sync again to rebuild practice."

    @JvmStatic
    fun offlineDataLicensesTitle(): String = "Offline data & licenses"

    @JvmStatic
    fun offlineDataLicensesBody(): String {
        return "One reference page covers KANJIDIC2, Jiten rank data, KanjiVG stroke order, and bundled font attribution."
    }

    @JvmStatic
    fun openDataLicensesLabel(): String = "Open data licenses"

    @JvmStatic
    fun dataLicensesTitle(): String = "Data licenses"

    @JvmStatic
    fun dataLicensesBody(): String = "Dictionary and stroke-order data bundled for offline study."

    @JvmStatic
    fun dictionaryDataTitle(): String = "Dictionary data"

    @JvmStatic
    fun strokeDataTitle(): String = "Stroke data"

    @JvmStatic
    fun fontsTitle(): String = "Fonts"
}

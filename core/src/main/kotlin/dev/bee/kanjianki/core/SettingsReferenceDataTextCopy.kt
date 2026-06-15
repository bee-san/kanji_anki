package dev.bee.kanjianki.core

import java.util.Locale

object SettingsReferenceDataTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun frequencyRangeTitle(): String = localizedText("Suspended card range", "停止カード範囲")

    @JvmStatic
    fun frequencyRangeBody(): String {
        return localizedText("Set the rank range, then sync.", "順位範囲を設定してから同期する。")
    }

    @JvmStatic
    fun minRankLabel(): String = localizedText("Min rank", "最小順位")

    @JvmStatic
    fun maxRankLabel(): String = localizedText("Max rank", "最大順位")

    @JvmStatic
    fun minimumRankLabel(): String = localizedText("Minimum rank", "最小順位")

    @JvmStatic
    fun maximumRankLabel(): String = localizedText("Maximum rank", "最大順位")

    @JvmStatic
    fun saveFrequencyRangeLabel(): String = localizedText("Save rank range", "順位範囲を保存")

    @JvmStatic
    fun numericRanksToast(): String = localizedText("Enter numbers for ranks.", "順位には数字を入力してください。")

    @JvmStatic
    fun rankRangeToast(): String = localizedText("Enter ranks 1-20000.", "順位は1-20000で入力してください。")

    @JvmStatic
    fun frequencyRangeSavedToast(): String = localizedText("Range saved. Sync to refresh.", "範囲を保存しました。同期すると更新されます。")

    @JvmStatic
    fun offlineDataLicensesTitle(): String = localizedText("Offline data licenses", "オフラインデータライセンス")

    @JvmStatic
    fun offlineDataLicensesBody(): String {
        return localizedText("Dictionary, stroke, and font credits.", "辞書、筆順、フォントのクレジット。")
    }

    @JvmStatic
    fun openDataLicensesLabel(): String = localizedText("Open data licenses", "データライセンスを開く")

    @JvmStatic
    fun dataLicensesTitle(): String = localizedText("Data licenses", "データライセンス")

    @JvmStatic
    fun dataLicensesBody(): String = localizedText("Dictionary, stroke, and font credits.", "辞書、筆順、フォントのクレジット。")

    @JvmStatic
    fun dictionaryDataTitle(): String = localizedText("Dictionary data", "辞書データ")

    @JvmStatic
    fun strokeDataTitle(): String = localizedText("Stroke data", "筆順データ")

    @JvmStatic
    fun fontsTitle(): String = localizedText("Fonts", "フォント")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}

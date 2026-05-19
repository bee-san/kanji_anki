package dev.bee.kanjianki.core;

public final class SettingsReferenceDataTextCopy {
    private SettingsReferenceDataTextCopy() {
    }

    public static String frequencyRangeTitle() {
        return "Frequency range";
    }

    public static String frequencyRangeBody() {
        return "Suspended cards are imported only when the kanji has a known Jiten rank inside this range. Lower ranks are more common. Default: 100-3000.";
    }

    public static String minRankLabel() {
        return "Min rank";
    }

    public static String maxRankLabel() {
        return "Max rank";
    }

    public static String minimumRankLabel() {
        return "Minimum rank";
    }

    public static String maximumRankLabel() {
        return "Maximum rank";
    }

    public static String saveFrequencyRangeLabel() {
        return "Save frequency range";
    }

    public static String numericRanksToast() {
        return "Enter numeric ranks.";
    }

    public static String rankRangeToast() {
        return "Use ranks from 1 to 20000.";
    }

    public static String frequencyRangeSavedToast() {
        return "Frequency range saved. Sync again to rebuild practice.";
    }

    public static String offlineDataLicensesTitle() {
        return "Offline data & licenses";
    }

    public static String offlineDataLicensesBody() {
        return "One reference page covers KANJIDIC2, Jiten rank data, KanjiVG stroke order, and bundled font attribution.";
    }

    public static String openDataLicensesLabel() {
        return "Open data licenses";
    }

    public static String dataLicensesTitle() {
        return "Data licenses";
    }

    public static String dataLicensesBody() {
        return "Dictionary and stroke-order data bundled for offline study.";
    }

    public static String dictionaryDataTitle() {
        return "Dictionary data";
    }

    public static String strokeDataTitle() {
        return "Stroke data";
    }

    public static String fontsTitle() {
        return "Fonts";
    }
}

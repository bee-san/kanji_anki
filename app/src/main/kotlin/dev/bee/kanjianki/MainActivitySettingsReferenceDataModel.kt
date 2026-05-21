package dev.bee.kanjianki

data class SettingsReferenceDataLinkModel(
    val title: String,
    val body: String,
    val actionLabel: String,
    val onAction: Runnable,
) : SettingsPanelModel

data class SettingsReferenceDataIntroModel(
    val backLabel: String,
    val title: String,
    val body: String,
    val onBack: Runnable,
)

data class SettingsReferenceDataModel(
    val dictionaryTitle: String,
    val dictionaryBody: String,
    val strokeTitle: String,
    val strokeBody: String,
    val fontsTitle: String,
    val fontsBody: String,
)

data class SettingsReferenceDataScreenModel(
    val homeLabel: String,
    val onHome: Runnable,
    val intro: SettingsReferenceDataIntroModel,
    val dataSources: SettingsReferenceDataModel,
)

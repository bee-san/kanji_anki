package dev.bee.kanjianki.core

enum class KaniThemeChoice(val storageKey: String) {
    GIRLYPOP("girlypop"),
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system"),
    AUTUMN("autumn"),
    MATCHA_MILK("matcha_milk"),
    OCEAN_STUDY("ocean_study"),
    MIDNIGHT_ARCADE("midnight_arcade"),
    GRAPE_SODA("grape_soda"),
    FOREST_MOSS("forest_moss");

    companion object {
        const val SETTING_KEY = "app_theme_choice"

        @JvmStatic
        fun fromStorageKey(value: String?): KaniThemeChoice {
            for (choice in entries) {
                if (choice.storageKey == value) {
                    return choice
                }
            }
            return GIRLYPOP
        }
    }
}

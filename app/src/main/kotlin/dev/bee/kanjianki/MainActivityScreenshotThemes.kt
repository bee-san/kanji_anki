package dev.bee.kanjianki

import dev.bee.kanjianki.theme.KaniThemeChoice

internal fun screenshotThemeChoiceOrNull(rawValue: String?): KaniThemeChoice? {
    val normalized = rawValue?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return KaniThemeChoice.entries.firstOrNull { it.storageKey == normalized }
}

package dev.bee.kanjianki.core

import java.util.Locale

/**
 * The native menu bar's own wording — the few strings no screen already supplies.
 *
 * Deliberately small. A menu bar is a second way to reach things the app already names,
 * so almost every item's label is read off the copy the visible control uses:
 * [NavigationCopy] for the tabs, [HomeTextCopy] for the Home destinations,
 * [SettingsKeybindingTextCopy.commandLabel] for the Study commands. A menu that invented
 * its own names would let the menu and the button drift apart, which is the same failure
 * the shared command model exists to prevent one layer down.
 *
 * What is left is the two menu titles and Back. Back is here rather than reused from
 * `:feature-shell`'s `shell_back` string because the menu model is plain JVM code that
 * cannot read a Compose resource, and `:core` is where both hosts' shared copy lives.
 */
object MenuBarCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    /** The navigation menu's title. */
    @JvmStatic
    fun goMenuLabel(): String = localizedText("Go", "移動")

    /**
     * The study menu's title, which is the Study tab's own name.
     *
     * Delegated rather than restated: a menu called anything else would name a screen
     * the navigation bar calls something different.
     */
    @JvmStatic
    fun studyMenuLabel(): String = NavigationCopy.studyLabel()

    /** The "go back one screen" item's label. */
    @JvmStatic
    fun backLabel(): String = localizedText("Back", "戻る")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}

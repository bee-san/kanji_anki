package dev.bee.kanjianki

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element

class LauncherShortcutsManifestTest {
    @Test
    fun manifestPublishesStaticShortcutsFromSingleTopMainActivity() {
        val manifest = xmlFile("src/main/AndroidManifest.xml")
        val activities = manifest.documentElement.getElementsByTagName("activity")
        val mainActivity = (0 until activities.length)
            .map { activities.item(it) as Element }
            .first { it.androidAttribute("name") == ".MainActivity" }

        assertEquals("singleTop", mainActivity.androidAttribute("launchMode"))

        val shortcutMetadata = (0 until mainActivity.getElementsByTagName("meta-data").length)
            .map { mainActivity.getElementsByTagName("meta-data").item(it) as Element }
            .firstOrNull { it.androidAttribute("name") == "android.app.shortcuts" }
        assertNotNull(shortcutMetadata)
        assertEquals("@xml/shortcuts", shortcutMetadata!!.androidAttribute("resource"))
    }

    @Test
    fun staticShortcutsTargetAllowlistedMainActivityActions() {
        val shortcutsDocument = xmlFile("src/main/res/xml/shortcuts.xml")
        val shortcutNodes = shortcutsDocument.documentElement.getElementsByTagName("shortcut")
        val shortcuts = (0 until shortcutNodes.length)
            .map { shortcutNodes.item(it) as Element }
            .associateBy { it.androidAttribute("shortcutId") }

        assertEquals(setOf("study", "browse", "games"), shortcuts.keys)
        assertShortcut(
            shortcuts.getValue("study"),
            icon = "@drawable/ic_study_24",
            shortLabel = "@string/shortcut_study_short_label",
            longLabel = "@string/shortcut_study_long_label",
            action = MainActivityBase.ACTION_OPEN_STUDY,
        )
        assertShortcut(
            shortcuts.getValue("browse"),
            icon = "@drawable/ic_book_24",
            shortLabel = "@string/shortcut_browse_short_label",
            longLabel = "@string/shortcut_browse_long_label",
            action = MainActivityBase.ACTION_OPEN_BROWSE,
        )
        assertShortcut(
            shortcuts.getValue("games"),
            icon = "@drawable/ic_game_24",
            shortLabel = "@string/shortcut_games_short_label",
            longLabel = "@string/shortcut_games_long_label",
            action = MainActivityBase.ACTION_OPEN_GAMES,
        )
    }

    @Test
    fun shortcutLabelsAreLocalizedInEnglishAndJapanese() {
        val english = stringResources("src/main/res/values/strings.xml")
        val japanese = stringResources("src/main/res/values-ja/strings.xml")

        assertEquals(
            mapOf(
                "shortcut_study_short_label" to "Study",
                "shortcut_study_long_label" to "Study now",
                "shortcut_browse_short_label" to "Browse",
                "shortcut_browse_long_label" to "Browse kanji",
                "shortcut_games_short_label" to "Games",
                "shortcut_games_long_label" to "Play kanji games",
            ),
            english.filterKeys { it.startsWith("shortcut_") },
        )
        assertEquals(
            mapOf(
                "shortcut_study_short_label" to "学習",
                "shortcut_study_long_label" to "今すぐ学習",
                "shortcut_browse_short_label" to "閲覧",
                "shortcut_browse_long_label" to "漢字を閲覧",
                "shortcut_games_short_label" to "ゲーム",
                "shortcut_games_long_label" to "漢字ゲームで遊ぶ",
            ),
            japanese.filterKeys { it.startsWith("shortcut_") },
        )
    }

    private fun assertShortcut(
        shortcut: Element,
        icon: String,
        shortLabel: String,
        longLabel: String,
        action: String,
    ) {
        assertEquals("true", shortcut.androidAttribute("enabled"))
        assertEquals(icon, shortcut.androidAttribute("icon"))
        assertEquals(shortLabel, shortcut.androidAttribute("shortcutShortLabel"))
        assertEquals(longLabel, shortcut.androidAttribute("shortcutLongLabel"))

        val intent = shortcut.getElementsByTagName("intent").item(0) as Element
        assertEquals(action, intent.androidAttribute("action"))
        assertEquals("dev.bee.kanjianki", intent.androidAttribute("targetPackage"))
        assertEquals("dev.bee.kanjianki.MainActivity", intent.androidAttribute("targetClass"))
    }

    private fun stringResources(path: String): Map<String, String> {
        val strings = xmlFile(path).documentElement.getElementsByTagName("string")
        return (0 until strings.length)
            .map { strings.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NS, name)

    private fun xmlFile(path: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File(path))

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}

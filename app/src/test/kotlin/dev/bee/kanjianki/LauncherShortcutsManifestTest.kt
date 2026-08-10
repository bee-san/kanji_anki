package dev.bee.kanjianki

import dev.bee.kanjianki.host.KaniLaunchIntents
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element

class LauncherShortcutsManifestTest {
    @Test
    fun theLauncherIsTheSingleTopThinHostAndItPublishesTheStaticShortcuts() {
        val manifest = xmlFile("src/main/AndroidManifest.xml")
        val activities = (0 until manifest.documentElement.getElementsByTagName("activity").length)
            .map { manifest.documentElement.getElementsByTagName("activity").item(it) as Element }

        // Exactly one launcher, found by its filter rather than by name, so the assertion is
        // about what the system will launch rather than about what a name suggests.
        val launchers = activities.filter { activity ->
            (0 until activity.getElementsByTagName("category").length)
                .map { activity.getElementsByTagName("category").item(it) as Element }
                .any { it.androidAttribute("name") == "android.intent.category.LAUNCHER" }
        }
        assertEquals(1, launchers.size)
        val launcher = launchers.single()
        assertEquals(THIN_HOST_RELATIVE, launcher.androidAttribute("name"))
        assertEquals("true", launcher.androidAttribute("exported"))

        // singleTop is what makes every deep link — notification, widget, shortcut — arrive
        // at the running instance through onNewIntent instead of stacking a second copy.
        assertEquals("singleTop", launcher.androidAttribute("launchMode"))

        // The shortcuts meta-data has to be on the launcher: that is where the system reads
        // the static definitions from, so leaving it behind publishes nothing.
        val shortcutMetadata = (0 until launcher.getElementsByTagName("meta-data").length)
            .map { launcher.getElementsByTagName("meta-data").item(it) as Element }
            .firstOrNull { it.androidAttribute("name") == "android.app.shortcuts" }
        assertNotNull(shortcutMetadata)
        assertEquals("@xml/shortcuts", shortcutMetadata!!.androidAttribute("resource"))
    }

    @Test
    fun theOldHostIsDeclaredButNoLongerReachableFromOutside() {
        val manifest = xmlFile("src/main/AndroidManifest.xml")
        val activities = (0 until manifest.documentElement.getElementsByTagName("activity").length)
            .map { manifest.documentElement.getElementsByTagName("activity").item(it) as Element }
        val old = activities.firstOrNull { it.androidAttribute("name") == ".MainActivity" }
            ?: return // Already deleted; nothing left to constrain.

        // Kept declared while its inheritance chain is removed separately, but not exported:
        // an entry point that still answers an implicit intent would leave two live hosts,
        // and a user could land on the unported one from a stale launcher entry.
        assertEquals("false", old.androidAttribute("exported"))
        assertEquals(0, old.getElementsByTagName("intent-filter").length)
    }

    @Test
    fun staticShortcutsTargetAllowlistedActionsOnTheLauncher() {
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
            action = KaniLaunchIntents.ACTION_OPEN_STUDY,
        )
        assertShortcut(
            shortcuts.getValue("browse"),
            icon = "@drawable/ic_book_24",
            shortLabel = "@string/shortcut_browse_short_label",
            longLabel = "@string/shortcut_browse_long_label",
            action = KaniLaunchIntents.ACTION_OPEN_BROWSE,
        )
        assertShortcut(
            shortcuts.getValue("games"),
            icon = "@drawable/ic_game_24",
            shortLabel = "@string/shortcut_games_short_label",
            longLabel = "@string/shortcut_games_long_label",
            action = KaniLaunchIntents.ACTION_OPEN_GAMES,
        )
    }

    @Test
    fun shortcutLabelsAreLocalizedInEnglishAndJapanese() {
        // Both source sets, because a shortcut label is a resource the *merged* app resolves,
        // and one of them now lives in `:widget`: `shortcut_study_long_label` is also the
        // Quick Study widget preview's caption, so it sits in the module that owns that
        // layout. Reading only this module's strings would report a live, resolvable label as
        // missing — which is what it did when the widget module was extracted.
        val english = stringResources(
            "src/main/res/values/strings.xml",
            "../widget/src/main/res/values/strings.xml",
        )
        val japanese = stringResources(
            "src/main/res/values-ja/strings.xml",
            "../widget/src/main/res/values-ja/strings.xml",
        )

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
        // A static shortcut names its target class outright, so this file has to move with
        // the launcher. Left behind, every shortcut would open the unported host — or
        // nothing at all once that class is deleted.
        assertEquals(THIN_HOST_CLASS, intent.androidAttribute("targetClass"))
    }

    private fun stringResources(vararg paths: String): Map<String, String> =
        paths.fold(emptyMap()) { merged, path ->
            val strings = xmlFile(path).documentElement.getElementsByTagName("string")
            merged + (0 until strings.length)
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

        /**
         * The launcher, spelled both ways the two files spell it.
         *
         * The manifest uses a package-relative name and `shortcuts.xml` needs the fully
         * qualified one; naming both here is what makes a half-done rename — one file moved,
         * the other not — a failure rather than a shortcut that opens the wrong host.
         */
        const val THIN_HOST_RELATIVE = ".host.KaniHostActivity"
        const val THIN_HOST_CLASS = "dev.bee.kanjianki.host.KaniHostActivity"
    }
}

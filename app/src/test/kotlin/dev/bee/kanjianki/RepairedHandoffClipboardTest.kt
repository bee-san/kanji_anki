package dev.bee.kanjianki

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RepairedHandoffPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepairedHandoffClipboardTest {
    @Test
    fun primaryActionCopiesExactAnkiBrowserSearch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = HomeRepairedHandoffCardModel(
            card = requireNotNull(RepairedHandoffPolicy.card(listOf("徴", "微"))),
            onCopySearch = {
                copyRepairedAnkiSearch(context, RepairedHandoffPolicy.ANKI_BROWSER_SEARCH)
            },
            onDismiss = {},
        )

        model.onCopySearch()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(
            "tag:kani_repaired is:suspended",
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString(),
        )
    }
}

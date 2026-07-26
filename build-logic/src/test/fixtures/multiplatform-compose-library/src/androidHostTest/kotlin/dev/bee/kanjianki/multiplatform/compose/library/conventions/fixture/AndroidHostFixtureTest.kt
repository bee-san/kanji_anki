package dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidHostFixtureTest {
    @Test
    fun hostTestSurfaceCanUseAndroidMainAndResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Kani shared UI foundation", androidFoundationLabel())
        assertEquals(
            "Kani Android host foundation",
            context.getString(R.string.android_foundation_label),
        )
    }
}

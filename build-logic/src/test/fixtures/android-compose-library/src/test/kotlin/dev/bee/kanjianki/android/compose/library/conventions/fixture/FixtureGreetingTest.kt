package dev.bee.kanjianki.android.compose.library.conventions.fixture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FixtureGreetingTest {
    @Test
    fun conventionProvidesComposeResourcesAndRobolectric() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val greeting = FixtureGreeting("Kani")
        val channel = NotificationChannel(
            "fixture",
            "Fixture channel",
            NotificationManager.IMPORTANCE_DEFAULT,
        )

        assertEquals("Convention fixture", context.getString(R.string.fixture_label))
        assertEquals("Kani", greeting.text())
        assertEquals("Fixture channel", greeting.channelLabel(channel))
        assertEquals("Java 17", FixtureJavaRecord("Java 17").value())
    }
}

package dev.bee.kanjianki.android.compose.library.conventions.fixture

import android.app.NotificationChannel
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

class FixtureGreeting(private val value: String) {
    fun text(): String = value

    fun channelLabel(channel: NotificationChannel): CharSequence = channel.name

    @Composable
    fun Content() {
        Text(text())
    }
}

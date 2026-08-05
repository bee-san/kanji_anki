package dev.bee.kanjianki.host

import android.os.Bundle
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniDestinationCodec

/**
 * The thin host's saved-instance-state, as a [Bundle] over the shared codec.
 *
 * Process recreation is the one place the Android host must persist presentation
 * state, and the *shape* of that state belongs to [KaniDestinationCodec] rather than
 * here: the desktop window persists the same route/argument map into its session file,
 * and two hosts each deriving the shape from the same prose is how they end up
 * disagreeing about what a restored Browse query means. This file owns only the
 * Android container — flattening the codec's `Map<String, String>` into a `Bundle` and
 * back — plus the two things the codec deliberately cannot do.
 *
 * The first is kanji normalization. [KaniDestinationCodec] checks a kanji argument
 * non-blank but does not canonicalize it, because `TextUtil` lives in `:core`, which
 * `:presentation-api` cannot see. Saved state is untrusted input (it survives an app
 * upgrade), so the glyph is normalized here exactly as `HomeRouteRestoration` does
 * today, and a glyph that normalizes to nothing restores nothing.
 *
 * The second is the pending reminder settings, which are not a destination at all:
 * they are an in-flight permission request that has to survive the system killing the
 * activity while the POST_NOTIFICATIONS dialog is up.
 */
internal object AndroidHostSavedState {
    const val KEY_DESTINATION_BUNDLE: String = "kani.host.destination"
    const val KEY_PENDING_REMINDER: String = "kani.host.pending-reminder"
    const val KEY_PENDING_REMINDER_ENABLED: String = "enabled"
    const val KEY_PENDING_REMINDER_HOUR: String = "hour"
    const val KEY_PENDING_REMINDER_MINUTE: String = "minute"

    /** The destination [outState] should restore to, or nothing when it is not restorable. */
    fun writeDestination(outState: Bundle, destination: KaniDestination?) {
        val encoded = destination?.let(KaniDestinationCodec::encode) ?: return
        outState.putBundle(
            KEY_DESTINATION_BUNDLE,
            Bundle().apply { encoded.forEach { (key, value) -> putString(key, value) } },
        )
    }

    /**
     * The destination [savedInstanceState] encodes, or null to land on Home.
     *
     * Fail-closed at every step: a missing bundle, an unknown route, or a kanji that
     * does not normalize all yield null. Restoring nothing and landing on Home beats
     * restoring a half-built screen, which is the same rule the codec applies.
     */
    fun readDestination(savedInstanceState: Bundle?): KaniDestination? {
        val bundle = savedInstanceState?.getBundle(KEY_DESTINATION_BUNDLE) ?: return null
        val encoded = bundle.keySet().mapNotNull { key ->
            bundle.getString(key)?.let { key to it }
        }.toMap()
        return KaniDestinationCodec.decode(encoded)?.let(::normalizeKanji)
    }

    fun writePendingReminder(outState: Bundle, pending: PendingReminder?) {
        val reminder = pending ?: return
        outState.putBundle(
            KEY_PENDING_REMINDER,
            Bundle().apply {
                putBoolean(KEY_PENDING_REMINDER_ENABLED, reminder.enabled)
                putInt(KEY_PENDING_REMINDER_HOUR, reminder.hour)
                putInt(KEY_PENDING_REMINDER_MINUTE, reminder.minute)
            },
        )
    }

    fun readPendingReminder(savedInstanceState: Bundle?): PendingReminder? {
        val bundle = savedInstanceState?.getBundle(KEY_PENDING_REMINDER) ?: return null
        return PendingReminder(
            enabled = bundle.getBoolean(KEY_PENDING_REMINDER_ENABLED),
            hour = bundle.getInt(KEY_PENDING_REMINDER_HOUR),
            minute = bundle.getInt(KEY_PENDING_REMINDER_MINUTE),
        )
    }

    /**
     * A reminder change the user made that is waiting on the notification permission.
     *
     * Deliberately not `LocalStoreBase.ReminderSettings`: this is host state in flight,
     * and the store's type is what gets written once the permission settles.
     */
    internal data class PendingReminder(
        val enabled: Boolean,
        val hour: Int,
        val minute: Int,
    )

    /**
     * Canonicalizes a restored destination's kanji, or null when it does not survive.
     *
     * The kanji-bearing destinations are the only ones with an argument that untrusted
     * state can corrupt into something the screen cannot render.
     */
    private fun normalizeKanji(destination: KaniDestination): KaniDestination? =
        when (destination) {
            is KaniDestination.Detail ->
                TextUtil.normalizeSingleKanji(destination.kanji)
                    .takeIf(String::isNotEmpty)
                    ?.let { destination.copy(kanji = it) }

            is KaniDestination.ReadOnlyDetail ->
                TextUtil.normalizeSingleKanji(destination.kanji)
                    .takeIf(String::isNotEmpty)
                    ?.let { destination.copy(kanji = it) }

            else -> destination
        }
}

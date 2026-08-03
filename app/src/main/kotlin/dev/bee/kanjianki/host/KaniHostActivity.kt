package dev.bee.kanjianki.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.bee.kanjianki.MainActivityStartup
import dev.bee.kanjianki.requireKaniContainer
import dev.bee.kanjianki.presentation.KaniLaunchCodec
import dev.bee.kanjianki.presentation.KaniLaunchRequest
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PlatformCapability

/**
 * The thin Android host (Goal 199): one activity that owns `setContent` and hands the
 * rest to the shared shell.
 *
 * It does only what a host must: obtain the process container, decode the launch
 * intent through the shared [KaniLaunchCodec] (so the deep-link precedence has one
 * owner across both hosts), build the Android provider probe and effect handler, and
 * compose [AndroidShellScaffold]. It deliberately does not extend the
 * `MainActivity*` inheritance chain — that chain is what Goal 199 removes — and it is
 * not yet the launcher activity: it runs in parallel with `MainActivity` until the
 * instrumented gate vouches for it.
 */
internal class KaniHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = requireKaniContainer()
        val host = AndroidShellHost(
            container = container,
            providerProbe = AndroidProviderProbe.of { container.ankiDroidGateway.status() },
            effectHandler = AndroidEffectHandler(this),
            capabilities = androidHostCapabilities(),
            launch = decodeLaunch(),
        )
        setContent {
            AndroidShellScaffold(host = host)
        }
    }

    /** The launch request this intent encodes, or null for an ordinary launch. */
    private fun decodeLaunch(): KaniLaunchRequest? {
        val target = KaniLaunchCodec.resolve(
            MainActivityStartup.launchTargetsPresentIn(intent),
        ) ?: return null
        val kanji = if (target == KaniLaunchCodec.Target.KANJI_DETAIL) {
            intent.getStringExtra(dev.bee.kanjianki.MainActivityBase.EXTRA_OPEN_KANJI_DETAIL)
        } else {
            null
        }
        return KaniLaunchCodec.request(target, kanji)
    }
}

/**
 * The capabilities the Android host advertises.
 *
 * Android ships an on-device Japanese handwriting recognizer (ML Kit via
 * `:writing-core`), so [PlatformCapability.WRITING_RECOGNITION] is present — the one
 * capability that differs from desktop, and the reason the shared study runtime keeps
 * the writing task here rather than re-routing it. Provider capabilities are not listed
 * here: they are per-connection and come from the live [AndroidProviderProbe].
 */
internal fun androidHostCapabilities(): PlatformCapabilities =
    PlatformCapabilities.of(
        PlatformCapability.WRITING_RECOGNITION,
        PlatformCapability.BACKUP_RESTORE,
        PlatformCapability.SECRET_PERSISTENCE,
    )

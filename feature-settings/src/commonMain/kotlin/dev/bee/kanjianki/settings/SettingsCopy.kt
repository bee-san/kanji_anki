package dev.bee.kanjianki.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.bee.kanjianki.feature.settings.generated.resources.Res
import dev.bee.kanjianki.feature.settings.generated.resources.settings_placeholder
import dev.bee.kanjianki.feature.settings.generated.resources.settings_placeholder_hint
import org.jetbrains.compose.resources.stringResource

/**
 * The Settings shell's two structural lines.
 *
 * Everything else — category titles, control labels, capability notices — is
 * host-computed on the portable model. Only the not-yet-shared placeholder text is
 * the shell's own.
 */
data class SettingsCopy(
    val placeholder: String,
    val placeholderHint: String,
)

/** Resolves [SettingsCopy] from this module's resources. */
@Composable
fun rememberSettingsCopy(): SettingsCopy {
    val placeholder = stringResource(Res.string.settings_placeholder)
    val hint = stringResource(Res.string.settings_placeholder_hint)
    return remember(placeholder, hint) { SettingsCopy(placeholder = placeholder, placeholderHint = hint) }
}

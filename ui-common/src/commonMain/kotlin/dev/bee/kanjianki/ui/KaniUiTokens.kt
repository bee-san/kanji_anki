package dev.bee.kanjianki.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * The shared shape, radius, and type scale.
 *
 * Only the values that are genuinely theme-independent live here. The Android
 * object of the same name also exposed `Ink`, `Primary`, `White` and friends as
 * composable palette accessors; those are omitted deliberately, because a token
 * that reads the palette is just a second, less obvious spelling of
 * `KaniTheme.colors`, and having both is how two names for one color drift apart.
 */
object KaniUiTokens {
    val PanelShape = RoundedCornerShape(24.dp)
    val LeafShape = RoundedCornerShape(18.dp)
    val PillShape = CircleShape
    val ButtonShape = RoundedCornerShape(12.dp)
    val WideButtonShape = RoundedCornerShape(22.dp)

    // Study screens deliberately use a small, shared scale. Keeping these tokens
    // here prevents each renderer from growing its own near-identical type,
    // radius, and elevation vocabulary.
    val StudyRadiusSmall = 12.dp
    val StudyRadiusMedium = 20.dp
    val StudyRadiusLarge = 28.dp
    val StudyShapeSmall = RoundedCornerShape(StudyRadiusSmall)
    val StudyShapeMedium = RoundedCornerShape(StudyRadiusMedium)
    val StudyShapeLarge = RoundedCornerShape(StudyRadiusLarge)
    val StudyElevation = 0.dp
    const val StudyCaptionTextSizeSp = 13
    const val StudyBodyTextSizeSp = 15
    const val StudyActionTextSizeSp = 17
    const val StudyHeadingTextSizeSp = 21
    const val StudyQuestionTextSizeSp = 27
    const val StudyHeroTextSizeSp = 80

    // Content heroes are deliberate scale exceptions: the compact value is the
    // KB1 keyboard fit, while word and front-kanji heroes carry the card's focus.
    const val StudyCompactHeroTextSizeSp = 64
    const val StudyWordHeroTextSizeSp = 44
    const val StudyFrontHeroTextSizeSp = 116

    /**
     * The floor for anything the user can click, tap, or activate.
     *
     * Material's own `TextButton`, `OutlinedButton`, and chip defaults are 40dp tall,
     * which is under every published minimum for a reliable pointer or touch target —
     * comfortable with a mouse, awkward with a thumb, and genuinely hard with a
     * trackpad on a laptop. A prominent action wants more than this and each feature
     * sets its own larger value for those; this is the line below which a control is
     * not reliably hittable at all, so it belongs to every module rather than to one.
     *
     * It lives here rather than in a feature because the defect it prevents was found
     * in five modules at once, and a floor that each module spells for itself is a
     * floor the sixth module will forget.
     */
    val MinTouchTarget = 44.dp

    /**
     * Picks dark ink or white, whichever reads better on [background].
     *
     * Backgrounds passed here are saturated accents, so the answer does not
     * depend on the active theme. The dark candidate is the Girlypop ink, which
     * is the value the Android implementation used.
     */
    fun readableTextColor(background: Color): Color {
        val ink = GirlypopKaniColors.ink
        val inkContrast = contrastRatio(ink, background)
        val whiteContrast = contrastRatio(Color.White, background)
        val preferred = if (inkContrast >= whiteContrast) ink else Color.White
        if (max(inkContrast, whiteContrast) >= WCAG_AA_CONTRAST) {
            return preferred
        }
        // A palette accent can land fractionally below the WCAG AA boundary
        // against both the branded ink and white. Pure black is the safe
        // final fallback.
        return Color.Black
    }

    private const val WCAG_AA_CONTRAST = 4.5
}

/** WCAG relative-luminance contrast ratio, in the range 1.0 to 21.0. */
fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = foreground.luminance().toDouble()
    val backgroundLuminance = background.luminance().toDouble()
    val lighter = max(foregroundLuminance, backgroundLuminance)
    val darker = min(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

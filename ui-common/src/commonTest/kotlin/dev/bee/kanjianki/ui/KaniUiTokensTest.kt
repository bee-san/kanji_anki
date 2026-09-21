package dev.bee.kanjianki.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tokens are mostly declarations; [KaniUiTokens.readableTextColor] is not.
 *
 * It decides what a user can actually read on a colored surface, so the tests are
 * about that decision — and about the token values that other code compares
 * against rather than merely passes through.
 */
class KaniUiTokensTest {
    @Test
    fun contrastIsSymmetricAndSpansTheFullWcagRange() {
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), 0.01)
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrastRatio(Color.White, Color.White), 0.01)

        val accent = GirlypopKaniColors.primary
        assertEquals(
            contrastRatio(accent, Color.White),
            contrastRatio(Color.White, accent),
            0.01,
            "contrast must not depend on argument order",
        )
    }

    @Test
    fun readableTextPicksWhicheverCandidateReadsBetterOnTheBackground() {
        assertEquals(Color.White, KaniUiTokens.readableTextColor(Color.Black))
        assertEquals(
            GirlypopKaniColors.ink,
            KaniUiTokens.readableTextColor(Color.White),
        )
    }

    @Test
    fun readableTextFallsBackToBlackWhenNeitherCandidateReachesWcagAa() {
        // A mid-grey is the case the branded ink and white both fail against, and
        // it is reachable from a real palette accent. Falling back to pure black is
        // the difference between hard-to-read and readable.
        val midGrey = Color(0xFF7E7E7E)
        assertTrue(contrastRatio(GirlypopKaniColors.ink, midGrey) < 4.5)
        assertTrue(contrastRatio(Color.White, midGrey) < 4.5)
        assertEquals(Color.Black, KaniUiTokens.readableTextColor(midGrey))
    }

    @Test
    fun everyPaletteAccentStaysReadableUnderTheChosenTextColor() {
        // The point of the helper is that no shipped accent can end up with
        // unreadable label text on it, so assert that across the whole palette set
        // rather than trusting one hand-picked color.
        for (theme in KaniThemeId.entries) {
            for (hostIsDark in listOf(false, true)) {
                val palette = theme.resolvePalette(hostIsDark)
                for (accent in listOf(palette.primary, palette.coral, palette.teal)) {
                    val text = KaniUiTokens.readableTextColor(accent)
                    assertTrue(
                        contrastRatio(text, accent) >= 3.0,
                        "$theme accent $accent reads at " +
                            "${contrastRatio(text, accent)} against $text",
                    )
                }
            }
        }
    }

    @Test
    fun theStudyScaleIsOrderedAndTheSharedShapesMatchTheirRadii() {
        val sizes = listOf(
            KaniUiTokens.StudyCaptionTextSizeSp,
            KaniUiTokens.StudyBodyTextSizeSp,
            KaniUiTokens.StudyActionTextSizeSp,
            KaniUiTokens.StudyHeadingTextSizeSp,
            KaniUiTokens.StudyQuestionTextSizeSp,
            KaniUiTokens.StudyHeroTextSizeSp,
        )
        assertEquals(sizes.sorted(), sizes, "the study scale must stay ordered")
        // The heroes are deliberate exceptions to that ordering, but they still
        // have to be larger than body text or they are not heroes.
        for (hero in listOf(
            KaniUiTokens.StudyCompactHeroTextSizeSp,
            KaniUiTokens.StudyWordHeroTextSizeSp,
            KaniUiTokens.StudyFrontHeroTextSizeSp,
        )) {
            assertTrue(hero > KaniUiTokens.StudyQuestionTextSizeSp)
        }

        assertTrue(KaniUiTokens.StudyRadiusSmall < KaniUiTokens.StudyRadiusMedium)
        assertTrue(KaniUiTokens.StudyRadiusMedium < KaniUiTokens.StudyRadiusLarge)
        assertEquals(0.dp, KaniUiTokens.StudyElevation)
    }
}

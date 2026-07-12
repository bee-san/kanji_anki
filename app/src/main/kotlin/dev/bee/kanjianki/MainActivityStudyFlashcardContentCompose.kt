@file:JvmName("MainActivityStudyFlashcardContentCompose")

package dev.bee.kanjianki

import android.graphics.Typeface
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.StudyTextCopy

private val HeroPanelFill: Color @Composable get() = KaniTheme.colors.panelSoft
private val HeroPlum: Color @Composable get() = KaniTheme.colors.ink
internal val StudyCardShadowElevation = KaniUiTokens.StudyElevation

@Composable
internal fun FlashcardCard(
    model: FlashcardCardModel,
    modifier: Modifier = Modifier,
    onTypingDone: Runnable? = null,
    onBrowseAction: Runnable? = null,
    swipeFeedback: StudySwipeFeedbackState? = null,
    imeVisible: Boolean = kaniImeVisible(),
) {
    // An unrevealed typing card is laid out compact from the first frame (KB1):
    // it auto-focuses its field and opens the keyboard on entry, so committing to
    // the reduced layout up front means the kanji prompt and the answer field
    // already fit and nothing reshapes when the IME animates in. Compact is a
    // function of card state, not live IME visibility (imeVisible is unused here
    // now; kept for signature compatibility with the reveal-time full layout).
    val compact = studyCardImeCompact(
        imeVisible = imeVisible,
        hasTypingAnswer = model.typingAnswer != null,
        revealed = model.revealState.isRevealed,
    )
    // Typing cards stay in their compact geometry through reveal. This preserves
    // KB1 and prevents the hero jumping when the IME/prompt disappears.
    val stableCompact = compact || model.typingAnswer != null
    StudySwipeReleaseEffect(swipeFeedback)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size -> swipeFeedback?.updateCardWidth(size.width.toFloat()) }
            .studySwipeFeedback(swipeFeedback)
            .animateContentSize()
            .heightIn(min = if (stableCompact) 0.dp else 360.dp),
        shape = KaniUiTokens.StudyShapeLarge,
        color = KaniTheme.colors.surface,
        border = BorderStroke(1.dp, KaniTheme.colors.border),
        shadowElevation = KaniUiTokens.StudyElevation,
    ) {
        Column(
            modifier = Modifier.padding(if (stableCompact) 12.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            FlashcardPromptHeader(
                model = model.promptHeader,
                showQuestion = !model.revealState.isRevealed,
                compact = stableCompact,
            )
            FlashcardHeroPanel(
                model.heroPanel,
                Modifier.padding(top = if (stableCompact) 10.dp else 16.dp),
                compact = stableCompact,
                revealed = model.revealState.isRevealed,
            )
            if (!model.revealState.isRevealed) {
                model.typingAnswer?.let { typingAnswerState ->
                    TypingMeaningAnswer(
                        label = if (model.typingReading) StudyTextCopy.readingLabel() else StudyTextCopy.meaningLabel(),
                        state = typingAnswerState,
                        onDone = onTypingDone,
                    )
                }
            }
            if (model.revealState.isRevealed) {
                StudyFlashcardAnswerContent(
                    model.answerPanel,
                    Modifier
                        .padding(top = 12.dp, bottom = 10.dp)
                        .fillMaxWidth(),
                    onBrowseAction = onBrowseAction,
                )
            }
        }
    }
}

/**
 * Translates the card with the active Fail/Pass swipe and tints it coral (fail) or
 * teal (pass). The tint steps up once the drag crosses the swipe threshold so the
 * commit point is visible while dragging.
 */
@Composable
private fun Modifier.studySwipeFeedback(swipeFeedback: StudySwipeFeedbackState?): Modifier {
    if (swipeFeedback == null) {
        return this
    }
    val passTint = KaniTheme.colors.teal
    val failTint = KaniTheme.colors.coral
    val cornerRadius = KaniUiTokens.StudyRadiusLarge
    return this
        .graphicsLayer {
            translationX = swipeFeedback.dragOffsetX
            rotationZ = swipeFeedback.progress * 1.5f
        }
        .drawWithContent {
            drawContent()
            val progress = swipeFeedback.progress
            if (progress != 0f) {
                val strength = kotlin.math.abs(progress)
                val alpha = if (strength >= 1f) 0.30f else 0.16f * strength
                drawRoundRect(
                    color = if (progress > 0f) passTint else failTint,
                    alpha = alpha,
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                )
            }
        }
}

@Composable
fun FlashcardPromptHeader(
    model: FlashcardPromptHeaderModel,
    showQuestion: Boolean = true,
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        StudyModeChip(model.modeLabel)
        // Keep the question's measured slot after reveal so the persistent hero
        // shrinks in place instead of jumping upward. Hidden copy is also removed
        // from semantics, so screen readers do not repeat the answered prompt.
        Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
        FlashcardHeaderText(
            text = model.question,
            sizeSp = if (compact) KaniUiTokens.StudyHeadingTextSizeSp else KaniUiTokens.StudyQuestionTextSizeSp,
            color = HeroPlum,
            bold = true,
            includeFontPadding = false,
            modifier = if (showQuestion) {
                Modifier
            } else {
                Modifier.alpha(0f).clearAndSetSemantics { }
            },
        )
    }
}

@Composable
private fun FlashcardHeaderText(
    text: String,
    sizeSp: Int,
    color: Color,
    bold: Boolean,
    includeFontPadding: Boolean,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = color,
        fontSize = sizeSp.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = textAlign,
        lineHeight = (sizeSp * 1.05f).sp,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = includeFontPadding))
    )
}

/** Hero panel minimum height while the keyboard is open on a typing card. */
internal val StudyHeroCompactMinHeight = 120.dp

/** Hero glyph size (sp) while the keyboard is open on a typing card. */
internal const val StudyHeroCompactGlyphSizeSp = KaniUiTokens.StudyCompactHeroTextSizeSp

@Composable
fun FlashcardHeroPanel(
    model: FlashcardHeroPanelModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    revealed: Boolean = false,
) {
    val fontFamily = model.typeface?.let { FontFamily(Typeface.create(it, Typeface.BOLD)) }
    val targetGlyphSizeSp = when {
        compact -> minOf(model.glyphSizeSp, StudyHeroCompactGlyphSizeSp)
        revealed -> minOf(model.glyphSizeSp, KaniUiTokens.StudyHeroTextSizeSp)
        else -> model.glyphSizeSp
    }
    val glyphSizeSp by animateFloatAsState(
        targetValue = targetGlyphSizeSp.toFloat(),
        label = "study-hero-glyph-size",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) StudyHeroCompactMinHeight else 210.dp),
        shape = KaniUiTokens.StudyShapeLarge,
        color = HeroPanelFill,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = model.glyph,
                color = HeroPlum,
                fontSize = glyphSizeSp.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (glyphSizeSp * 1.05f).sp,
                textAlign = TextAlign.Center,
                fontFamily = fontFamily,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

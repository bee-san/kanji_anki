@file:JvmName("MainActivityStudyFlashcardContentCompose")

package dev.bee.kanjianki

import android.graphics.Typeface
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.StudyTextCopy

private val HeroPanelFill: Color @Composable get() = KaniTheme.colors.panelSoft
private val HeroPanelBorder: Color @Composable get() = KaniTheme.colors.border
private val HeroMuted: Color @Composable get() = KaniTheme.colors.muted
private val HeroPlum: Color @Composable get() = KaniTheme.colors.ink
private val HeroPink: Color @Composable get() = KaniTheme.colors.primary
private val HeroPillFill: Color @Composable get() = KaniTheme.colors.pill
internal val StudyCardShadowElevation = 0.dp

@Composable
internal fun FlashcardCard(
    model: FlashcardCardModel,
    modifier: Modifier = Modifier,
    onTypingDone: Runnable? = null,
    onBrowseAction: Runnable? = null,
    swipeFeedback: StudySwipeFeedbackState? = null,
    imeVisible: Boolean = kaniImeVisible(),
) {
    // While the keyboard is open on a typing card, compact the layout so the kanji
    // prompt and the answer field both fit in the reduced viewport. Without this the
    // focused field's bring-into-view scrolls the 210dp+ hero (and the kanji) off
    // the top of the screen.
    val compact = studyCardImeCompact(
        imeVisible = imeVisible,
        hasTypingAnswer = model.typingAnswer != null,
        revealed = model.revealState.isRevealed,
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .studySwipeFeedback(swipeFeedback)
            .animateContentSize()
            .heightIn(min = if (compact) 0.dp else 360.dp),
        shape = RoundedCornerShape(32.dp),
        color = KaniTheme.colors.surface,
        shadowElevation = StudyCardShadowElevation
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            FlashcardPromptHeader(
                model = model.promptHeader,
                showHiddenHint = !model.revealState.isRevealed,
                compact = compact,
            )
            if (!model.revealState.isRevealed) {
                FlashcardHeroPanel(
                    model.heroPanel,
                    Modifier.padding(top = if (compact) 10.dp else 16.dp),
                    compact = compact,
                )
                model.typingAnswer?.let { typingAnswerState ->
                    TypingMeaningAnswer(
                        label = StudyTextCopy.meaningLabel(),
                        state = typingAnswerState,
                        onDone = onTypingDone,
                    )
                }
            }
            if (model.revealState.isRevealed) {
                StudyAnswerPanel(
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
    val cornerRadiusDp = 32.dp
    return this
        .graphicsLayer {
            translationX = swipeFeedback.dragOffsetX * 0.5f
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
                    cornerRadius = CornerRadius(cornerRadiusDp.toPx()),
                )
            }
        }
}

@Composable
fun FlashcardPromptHeader(
    model: FlashcardPromptHeaderModel,
    showHiddenHint: Boolean = true,
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Compact (keyboard open) keeps only the question line: the pill, title, and
        // hidden-answer hint are secondary chrome that would push the kanji hero and
        // the answer field below the fold.
        if (!compact) {
            RecognitionPill(model.modeLabel)
            Spacer(modifier = Modifier.height(14.dp))
            FlashcardHeaderText(
                text = model.title,
                sizeSp = 21,
                color = HeroPlum,
                bold = true,
                includeFontPadding = false
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        FlashcardHeaderText(
            text = model.question,
            sizeSp = if (compact) 21 else 27,
            color = HeroPlum,
            bold = true,
            includeFontPadding = false
        )
        if (showHiddenHint && !compact) {
            Spacer(modifier = Modifier.height(6.dp))
            FlashcardHeaderText(
                text = model.hiddenHint,
                sizeSp = 14,
                color = HeroMuted,
                bold = false,
                includeFontPadding = false
            )
        }
    }
}

@Composable
fun RecognitionPill(label: String) {
    Surface(
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(24.dp),
        color = HeroPillFill
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_eye_24),
                contentDescription = null,
                tint = HeroPink,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = HeroPink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (18 * 1.05f).sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

@Composable
private fun FlashcardHeaderText(
    text: String,
    sizeSp: Int,
    color: Color,
    bold: Boolean,
    includeFontPadding: Boolean,
    textAlign: TextAlign = TextAlign.Center,
) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
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
internal const val StudyHeroCompactGlyphSizeSp = 64

@Composable
fun FlashcardHeroPanel(
    model: FlashcardHeroPanelModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val fontFamily = model.typeface?.let { FontFamily(Typeface.create(it, Typeface.BOLD)) }
    val glyphSizeSp = if (compact) minOf(model.glyphSizeSp, StudyHeroCompactGlyphSizeSp) else model.glyphSizeSp
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) StudyHeroCompactMinHeight else 210.dp),
        shape = RoundedCornerShape(28.dp),
        color = HeroPanelFill,
        border = BorderStroke(1.dp, HeroPanelBorder)
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

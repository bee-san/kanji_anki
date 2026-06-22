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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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

@Composable
internal fun FlashcardCard(
    model: FlashcardCardModel,
    modifier: Modifier = Modifier,
    onTypingDone: Runnable? = null,
    onBrowseAction: Runnable? = null,
) {
    val cardHeightModifier = if (model.revealState.isRevealed) {
        val windowHeight = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp()
        }
        Modifier.height(maxOf(360.dp, windowHeight * 0.8f))
    } else {
        Modifier.heightIn(min = 360.dp)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .then(cardHeightModifier),
        shape = RoundedCornerShape(32.dp),
        color = KaniTheme.colors.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            FlashcardPromptHeader(
                model = model.promptHeader,
                showHiddenHint = !model.revealState.isRevealed,
            )
            if (!model.revealState.isRevealed) {
                FlashcardHeroPanel(model.heroPanel, Modifier.padding(top = 16.dp))
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
                        .weight(1f),
                    onBrowseAction = onBrowseAction,
                )
            }
        }
    }
}

@Composable
fun FlashcardPromptHeader(model: FlashcardPromptHeaderModel, showHiddenHint: Boolean = true) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
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
        FlashcardHeaderText(
            text = model.question,
            sizeSp = 27,
            color = HeroPlum,
            bold = true,
            includeFontPadding = false
        )
        if (showHiddenHint) {
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

@Composable
fun FlashcardHeroPanel(model: FlashcardHeroPanelModel, modifier: Modifier = Modifier) {
    val fontFamily = model.typeface?.let { FontFamily(Typeface.create(it, Typeface.BOLD)) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 210.dp),
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
                fontSize = model.glyphSizeSp.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (model.glyphSizeSp * 1.05f).sp,
                textAlign = TextAlign.Center,
                fontFamily = fontFamily,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

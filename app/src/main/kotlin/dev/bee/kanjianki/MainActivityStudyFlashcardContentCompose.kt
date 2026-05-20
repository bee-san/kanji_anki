@file:JvmName("MainActivityStudyFlashcardContentCompose")

package dev.bee.kanjianki

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HeroPanelFill = Color(MainActivityUiSupport.STUDY_HERO_PANEL)
private val HeroPanelBorder = Color(MainActivityUiSupport.STUDY_BORDER)
private val HeroPlum = Color(MainActivityUiSupport.STUDY_HERO_PLUM)
private val HeroPink = Color(MainActivityUiSupport.STUDY_HERO_PINK)
private val HeroPillFill = Color(MainActivityUiSupport.STUDY_HERO_PILL)

data class FlashcardHeroPanelModel(
    val glyph: String,
    val glyphSizeSp: Int,
    val typeface: Typeface?,
)

internal fun recognitionPillView(context: Context, label: String): View {
    return ComposeView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        setContent {
            MaterialTheme {
                RecognitionPill(label)
            }
        }
    }
}

internal fun heroKanjiPanelView(activity: MainActivityStudy, model: FlashcardHeroPanelModel): View {
    return ComposeView(activity).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            activity.dp(210)
        ).apply {
            setMargins(0, activity.dp(16), 0, 0)
        }
        setContent {
            MaterialTheme {
                FlashcardHeroPanel(model)
            }
        }
    }
}

@Composable
fun RecognitionPill(label: String) {
    Surface(
        modifier = Modifier.height(44.dp),
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
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

@Composable
fun FlashcardHeroPanel(model: FlashcardHeroPanelModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
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
                textAlign = TextAlign.Center,
                fontFamily = model.typeface?.let { FontFamily(it) },
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

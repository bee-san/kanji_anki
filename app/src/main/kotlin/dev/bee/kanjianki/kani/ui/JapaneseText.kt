package dev.bee.kanjianki.kani.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LocaleSpan
import java.util.Locale

/**
 * Kanji share code points with Chinese hanzi, so Android picks the glyph shapes by the text's
 * locale. Without one it uses the phone's language, and an English phone draws kanji in a
 * Chinese font (different strokes in 直, 骨, 角 …). Every piece of Kani text is tagged Japanese;
 * Latin text looks the same either way.
 */
val JapaneseLocales = LocaleList("ja-JP")

/** [style] with its text tagged Japanese. */
fun TextStyle.japanese(): TextStyle = merge(TextStyle(localeList = JapaneseLocales))

/** [text] tagged Japanese, for views outside Compose (widgets, notifications). */
fun japaneseSpan(text: CharSequence): CharSequence = SpannableString(text).apply {
    setSpan(LocaleSpan(Locale.JAPAN), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
}

/**
 * Material's `Text`, always in Japanese glyphs. The Kani UI imports this one instead of
 * `androidx.compose.material3.Text` (a source-hygiene test keeps it that way).
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style.japanese(),
    )
}

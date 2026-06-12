package dev.bee.kanjianki

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

internal val GamesInk: Color @Composable get() = KaniUiTokens.Ink
internal val GamesMuted: Color @Composable get() = KaniUiTokens.Muted
internal val GamesCoral: Color @Composable get() = KaniUiTokens.Coral
internal val GamesTeal: Color @Composable get() = KaniUiTokens.Teal
internal val GamesBlue: Color @Composable get() = KaniUiTokens.Blue
internal val GamesGrey: Color @Composable get() = KaniUiTokens.Grey
internal val GamesWhite: Color @Composable get() = KaniUiTokens.White
internal val GamesStudyPlum: Color @Composable get() = KaniUiTokens.StudyPlum
internal val GamesPanelBorder: Color @Composable get() = KaniUiTokens.PanelBorder
internal val GamesButtonBorder: Color @Composable get() = KaniUiTokens.ButtonBorder
internal val GamesPanelShape = KaniUiTokens.PanelShape
internal val GamesButtonShape = KaniUiTokens.ButtonShape
internal val GamesChoiceShape = RoundedCornerShape(18.dp)
internal val GamesPillShape = RoundedCornerShape(999.dp)
internal val GamesScoreShape = RoundedCornerShape(8.dp)
internal val GamesKanjiFontFamily = FontFamily(Font(R.font.kaisei_tokumin_regular))

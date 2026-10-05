package baseballgm.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import baseballgm.app.generated.resources.Res
import baseballgm.app.generated.resources.pretendard_bold
import baseballgm.app.generated.resources.pretendard_regular
import baseballgm.app.generated.resources.pretendard_semibold
import org.jetbrains.compose.resources.Font

/** 앱 전체 테마. 토큰을 [LocalTokens]로 내려주고, Material 컴포넌트도 같은 색·글꼴을 쓰게 맞춘다 */
@Composable
fun ScoutTheme(tokens: Tokens = Tokens(), content: @Composable () -> Unit) {
    val family = FontFamily(
        Font(Res.font.pretendard_regular, FontWeight.Normal),
        Font(Res.font.pretendard_semibold, FontWeight.SemiBold),
        Font(Res.font.pretendard_bold, FontWeight.Bold),
    )
    val c = tokens.color
    val scheme = lightColorScheme(
        primary = c.primary,
        onPrimary = c.onPrimary,
        secondary = c.accent,
        background = c.background,
        onBackground = c.ink,
        surface = c.surface,
        onSurface = c.ink,
        error = c.bad,
    )
    val base = TextStyle(fontFamily = family, color = c.ink)
    val typography = Typography(
        bodyMedium = base.copy(fontSize = tokens.type.body),
        bodySmall = base.copy(fontSize = tokens.type.caption, color = c.inkSoft),
        titleLarge = base.copy(fontSize = tokens.type.title, fontWeight = FontWeight.Bold),
        displaySmall = base.copy(fontSize = tokens.type.hero, fontWeight = FontWeight.Bold),
        labelLarge = base.copy(fontSize = tokens.type.body, fontWeight = FontWeight.SemiBold),
    )
    CompositionLocalProvider(LocalTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** 화면 코드에서 `T.color.primary` 처럼 토큰을 읽는다 */
val T: Tokens
    @Composable @ReadOnlyComposable
    get() = LocalTokens.current

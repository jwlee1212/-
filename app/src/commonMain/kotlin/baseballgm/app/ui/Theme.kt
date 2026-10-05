package baseballgm.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import baseballgm.app.generated.resources.Res
import baseballgm.app.generated.resources.pretendard_bold
import baseballgm.app.generated.resources.pretendard_regular
import baseballgm.model.TeamId
import org.jetbrains.compose.resources.Font

/**
 * 앱 테마 (docs/16 비서 브리핑형).
 *
 * 토큰([AppTokens])을 라이트/다크 두 벌로 만들어 내려보내고, Material 컴포넌트가 같은 색을 쓰도록
 * colorScheme 도 토큰에서 만든다. 화면 코드는 모드를 모른다.
 */
@Composable
fun AppTheme(
    teamColors: List<TeamColorSpec> = emptyList(),
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = remember(dark, teamColors) {
        AppTokens(
            isDark = dark,
            grade = if (dark) DarkGrade else LightGrade,
            semantic = if (dark) DarkSemantic else LightSemantic,
            base = if (dark) DarkBase else LightBase,
            teams = teamColors.associate { it.teamId to parseHexColor(if (dark) it.dark else it.light) },
        )
    }
    val family = pretendard()
    val typography = remember(family) { appTypography(family) }
    val radii = tokens.radii

    CompositionLocalProvider(LocalAppTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorSchemeOf(tokens),
            typography = typography,
            shapes = Shapes(
                small = RoundedCornerShape(radii.chip),
                medium = RoundedCornerShape(radii.control),
                large = RoundedCornerShape(radii.card),
                extraLarge = RoundedCornerShape(radii.card),
            ),
            content = content,
        )
    }
}

/** 화면 코드가 토큰을 읽는 입구 */
object AppTheme {
    val tokens: AppTokens
        @Composable @ReadOnlyComposable
        get() = LocalAppTokens.current
}

/**
 * 자주 쓰는 토큰 바로가기. 예전 이름(good/warn/bad/muted/forRating)을 유지해 화면 코드가 그대로 읽힌다.
 * 값은 전부 토큰에서 온다 — 여기에 색상값을 쓰지 않는다.
 */
object AppColors {
    val good: Color @Composable @ReadOnlyComposable get() = LocalAppTokens.current.semantic.good
    val warn: Color @Composable @ReadOnlyComposable get() = LocalAppTokens.current.semantic.warn
    val bad: Color @Composable @ReadOnlyComposable get() = LocalAppTokens.current.semantic.bad
    val muted: Color @Composable @ReadOnlyComposable get() = LocalAppTokens.current.base.textMuted

    /** 능력치 1~100 → 등급 색 */
    @Composable @ReadOnlyComposable
    fun forRating(rating: Double): Color = LocalAppTokens.current.grade.of(rating)
}

/** 구단 색. 모드에 맞는 쪽(라이트/다크 변형)이 자동으로 나온다 */
object TeamColors {
    @Composable @ReadOnlyComposable
    fun of(teamId: TeamId): Color = LocalAppTokens.current.teamColor(teamId)
}

private fun colorSchemeOf(tokens: AppTokens) = with(tokens.base) {
    if (tokens.isDark) {
        darkColorScheme(
            primary = brand, onPrimary = onBrand,
            primaryContainer = cardInset, onPrimaryContainer = text,
            secondary = brand, onSecondary = onBrand,
            secondaryContainer = cardInset, onSecondaryContainer = text,
            background = background, onBackground = text,
            surface = background, onSurface = text,
            surfaceVariant = card, onSurfaceVariant = textSecondary,
            surfaceContainer = card, surfaceContainerHigh = card, surfaceContainerHighest = cardInset,
            surfaceContainerLow = card, surfaceContainerLowest = background,
            outline = line, outlineVariant = line,
            error = tokens.semantic.bad,
        )
    } else {
        lightColorScheme(
            primary = brand, onPrimary = onBrand,
            primaryContainer = cardInset, onPrimaryContainer = text,
            secondary = brand, onSecondary = onBrand,
            secondaryContainer = cardInset, onSecondaryContainer = text,
            background = background, onBackground = text,
            surface = background, onSurface = text,
            surfaceVariant = card, onSurfaceVariant = textSecondary,
            surfaceContainer = card, surfaceContainerHigh = card, surfaceContainerHighest = cardInset,
            surfaceContainerLow = card, surfaceContainerLowest = background,
            outline = line, outlineVariant = line,
            error = tokens.semantic.bad,
        )
    }
}

@Composable
private fun pretendard(): FontFamily = FontFamily(
    Font(Res.font.pretendard_regular, FontWeight.Normal),
    Font(Res.font.pretendard_bold, FontWeight.Bold),
)

/**
 * 글꼴 크기 토큰 — **세 단계, 굵기 두 가지** (docs/16 절제 규칙, 2026-10-01).
 *
 * | 단계 | 크기 | 쓰는 곳 |
 * |---|---|---|
 * | 큰 숫자·제목 | 20sp Bold | 화면의 주인공 하나 (전적, 전력, 비서 한 줄 등) |
 * | 본문 | 14sp Regular / Bold | 문장, 이름, 숫자, 카드 제목(Bold) |
 * | 보조 | 12sp Regular | 회색 설명, 라벨, 칩 |
 *
 * Material 의 열두 슬롯은 이 세 단계에 몰아서 맵핑한다. 화면 코드가 어느 슬롯을 고르든
 * 세 단계 밖으로 나갈 수 없다. **모든 스타일에 고정폭 숫자(tnum)** (docs/16 §8).
 */
private fun appTypography(family: FontFamily): Typography {
    fun style(size: Int, weight: FontWeight) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = (size * 1.45).toInt().sp,
        fontFeatureSettings = "tnum",
    )
    val display = style(TYPE_DISPLAY, FontWeight.Bold)
    val body = style(TYPE_BODY, FontWeight.Normal)
    val bodyBold = style(TYPE_BODY, FontWeight.Bold)
    val caption = style(TYPE_CAPTION, FontWeight.Normal)
    return Typography(
        displayLarge = display, displayMedium = display, displaySmall = display,
        headlineLarge = display, headlineMedium = display, headlineSmall = display,
        titleLarge = display,
        titleMedium = bodyBold, titleSmall = bodyBold,
        bodyLarge = body, bodyMedium = body,
        labelLarge = bodyBold,
        bodySmall = caption, labelMedium = caption, labelSmall = caption,
    )
}

private const val TYPE_DISPLAY = 20
private const val TYPE_BODY = 14
private const val TYPE_CAPTION = 12

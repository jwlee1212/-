package baseballgm.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 앱 테마.
 *
 * 실제 구단의 컬러를 쓰지 않는다 (CLAUDE.md 상표 원칙). 야간 경기장 느낌의 중립적인 색만 쓴다.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FB3FF),
    onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF1B3B66),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF9CCC9C),
    surface = Color(0xFF14171C),
    onSurface = Color(0xFFE3E6EB),
    surfaceVariant = Color(0xFF1E232B),
    onSurfaceVariant = Color(0xFFB6BEC9),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE3E6EB),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF16508F),
    secondary = Color(0xFF2E6B41),
    surface = Color(0xFFF7F8FA),
    surfaceVariant = Color(0xFFE6E9EF),
    background = Color(0xFFFFFFFF),
)

/** 숫자가 많은 화면이라 본문을 조금 작게, 굵기 대비를 크게 잡았다. */
private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp),
)

/** 성적·상태를 색으로 구분할 때 쓰는 값들. */
object AppColors {
    val good = Color(0xFF6FD08C)
    val warn = Color(0xFFE8C06A)
    val bad = Color(0xFFE87A7A)
    val muted = Color(0xFF8A93A0)

    /** 능력치 1~100 → 색. 60(1군 주전)을 기준으로 나눈다. */
    fun forRating(rating: Double): Color = when {
        rating >= 75 -> Color(0xFF7FB3FF)
        rating >= 60 -> good
        rating >= 45 -> warn
        else -> bad
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

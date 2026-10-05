package baseballgm.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 디자인 토큰 (docs/02-design.md, 불변 원칙 7).
 *
 * 화면 코드는 색·간격·모서리·글자 크기·애니메이션 길이를 여기서만 가져온다.
 * 캐주얼 톤이라 채도 높은 원색과 큰 모서리를 쓴다. 다크 모드는 S6에서 추가한다.
 */
@Immutable
data class ColorTokens(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val inkSoft: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val good: Color,
    val bad: Color,
    val warn: Color,
    /** 야구장: 잔디·흙·라인 */
    val grass: Color,
    val dirt: Color,
    val chalk: Color,
    val bat: Color,
    /** 공·타구 그림자 */
    val shadow: Color,
    /** 화면 위에 겹치는 반투명 바탕 (FPS 등) */
    val scrim: Color,
)

@Immutable
data class SpaceTokens(val xs: Dp = 4.dp, val sm: Dp = 8.dp, val md: Dp = 16.dp, val lg: Dp = 24.dp, val xl: Dp = 40.dp)

@Immutable
data class ShapeTokens(val card: Dp = 20.dp, val button: Dp = 28.dp)

@Immutable
data class TypeTokens(val caption: TextUnit = 12.sp, val body: TextUnit = 15.sp, val title: TextUnit = 22.sp, val hero: TextUnit = 34.sp)

/** 애니메이션 길이(ms). 반복 화면은 짧게, 큰 순간만 길게 (docs/02-design.md §5) */
@Immutable
data class MotionTokens(val quick: Int = 200, val normal: Int = 350, val big: Int = 900, val idleLoop: Int = 1400)

val LightColors = ColorTokens(
    background = Color(0xFFFFF8EC),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF26324A),
    inkSoft = Color(0xFF6B7690),
    primary = Color(0xFF2F6BFF),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFFF8A1F),
    good = Color(0xFF22B573),
    bad = Color(0xFFFF4D5E),
    warn = Color(0xFFFFC21A),
    grass = Color(0xFF4CC46A),
    dirt = Color(0xFFE2A66B),
    chalk = Color(0xFFFFFFFF),
    bat = Color(0xFF8B5A2B),
    shadow = Color(0x40000000),
    scrim = Color(0x99000000),
)

@Immutable
data class Tokens(
    val color: ColorTokens = LightColors,
    val space: SpaceTokens = SpaceTokens(),
    val shape: ShapeTokens = ShapeTokens(),
    val type: TypeTokens = TypeTokens(),
    val motion: MotionTokens = MotionTokens(),
)

val LocalTokens = staticCompositionLocalOf { Tokens() }

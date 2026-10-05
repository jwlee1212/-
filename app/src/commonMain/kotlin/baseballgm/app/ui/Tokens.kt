package baseballgm.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import baseballgm.model.TeamId

/**
 * 디자인 토큰 (docs/16 §6·7·11).
 *
 * **색상값은 이 파일에만 있다.** 화면 코드는 [AppTheme] 을 거쳐 토큰 이름으로만 색을 고른다.
 * 토큰 그룹 넷(등급·의미·구단·바탕)은 서로 섞지 않는다 — 예를 들어 "능력치가 낮다"(등급 회색)와
 * "부상이다"(의미 빨강)는 다른 색이어야 한다.
 */

/** 능력치 등급 색. 85+ 보라 / 75~84 파랑 / 60~74 초록 / 45~59 황토 / ~44 회색 */
@Immutable
data class GradeColors(
    val elite: Color,
    val high: Color,
    val solid: Color,
    val fringe: Color,
    val low: Color,
) {
    fun of(rating: Double): Color = when {
        rating >= 85 -> elite
        rating >= 75 -> high
        rating >= 60 -> solid
        rating >= 45 -> fringe
        else -> low
    }
}

/** 의미 색. 좋음 / 주의 / 나쁨 */
@Immutable
data class SemanticColors(val good: Color, val warn: Color, val bad: Color)

/** 바탕·글자·브랜드 색. 구단과 상관없는 중립 톤 */
@Immutable
data class BaseColors(
    val background: Color,
    val card: Color,
    /** 카드 안의 한 단계 들어간 면 (막대 바탕, 칩 바탕) */
    val cardInset: Color,
    /**
     * 목록 줄무늬. 카드 바탕과 아주 조금만 다른 면 — 구분선 대신 짝수 줄에 깐다 (선수 목록, 2026-10-02)
     */
    val rowAlt: Color,
    val line: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    /** 앱 자체의 강조색. 구단 색이 아니다 (링크, 선택 탭, 비서 아바타) */
    val brand: Color,
    val onBrand: Color,
    /** 구단 색으로 칠한 면(진행 버튼) 위의 글자 */
    val onTeam: Color,
)

/** 간격 토큰. **4·8·12·16·24·32dp 밖의 간격은 쓰지 않는다** (docs/16 절제 규칙) */
@Immutable
data class Spacing(
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val s: androidx.compose.ui.unit.Dp = 8.dp,
    val m: androidx.compose.ui.unit.Dp = 12.dp,
    val l: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 24.dp,
    val xxl: androidx.compose.ui.unit.Dp = 32.dp,
)

/**
 * 크기 토큰 (2026-10-02, 레퍼런스 리팩토링). 간격이 아니라 **요소 자체의 크기**다.
 * 여러 화면이 같은 크기를 써야 하는 것만 여기 둔다.
 */
@Immutable
data class Sizes(
    /** 하단 진행 버튼 높이. 화면에서 가장 두꺼운 조작부 */
    val progressButton: androidx.compose.ui.unit.Dp = 60.dp,
    /** 선수 목록 한 줄 높이 (이름 줄 + 포지션 줄) */
    val listRow: androidx.compose.ui.unit.Dp = 52.dp,
    /** 목록 머리줄 높이 */
    val listHeader: androidx.compose.ui.unit.Dp = 28.dp,
    /** 등번호 배지 (구단 색 원) */
    val numberBadge: androidx.compose.ui.unit.Dp = 28.dp,
    /** 선수 상세 머리의 큰 등번호 배지 */
    val numberBadgeLarge: androidx.compose.ui.unit.Dp = 48.dp,
    /** 상태 아이콘 배지 (폼·부상) 바깥 / 안 아이콘 */
    val statusBadge: androidx.compose.ui.unit.Dp = 18.dp,
    val statusIcon: androidx.compose.ui.unit.Dp = 12.dp,
    /** 숫자 아래 깔리는 미니 막대 두께 */
    val miniBar: androidx.compose.ui.unit.Dp = 3.dp,
    /** 진행률 같은 얇은 게이지 두께 */
    val meter: androidx.compose.ui.unit.Dp = 6.dp,
)

/**
 * 움직임 토큰 (2026-10-02, 재미 개선 1번 "결과 공개 연출"). 단위 ms.
 * 움직임은 **결과가 공개되는 순간에만** 쓴다 — 반복·장식용 애니메이션은 없다 (docs/16 §12).
 */
@Immutable
data class Motion(
    /** 경기 카드 사이 쉼 */
    val cardGap: Int = 350,
    /** 카드가 뒤집히며 열리는 시간 */
    val flip: Int = 420,
    /** 라인 스코어가 한 이닝씩 채워지는 간격 (보통 경기) */
    val inningStep: Int = 45,
    /** 접전(1점 차·끝내기·연장·역전)은 천천히 */
    val tenseInningStep: Int = 170,
    /** 접전의 마지막 이닝 앞에서 멈칫 */
    val tenseHold: Int = 650,
    /** 숫자가 0부터 올라가는 시간 (주간 전적) */
    val countUp: Int = 600,
    /** 결과 도장(승·패)이 자리 잡는 시간 */
    val settle: Int = 300,
    /** 그래프 막대가 처음 나타날 때 차오르는 시간 (2026-10-04, docs/16 §12 개정) */
    val barFill: Int = 500,
    /** 막대 여러 개가 위에서부터 차례로 차오르는 간격 */
    val barStagger: Int = 50,
)

@Immutable
data class Radii(
    val card: androidx.compose.ui.unit.Dp = 16.dp,
    val control: androidx.compose.ui.unit.Dp = 12.dp,
    val chip: androidx.compose.ui.unit.Dp = 8.dp,
)

/** 구단 색 한 벌. `data/teams.json` 의 color / colorDark 에서 온다 */
data class TeamColorSpec(val teamId: TeamId, val light: String, val dark: String)

@Immutable
data class AppTokens(
    val isDark: Boolean,
    val grade: GradeColors,
    val semantic: SemanticColors,
    val base: BaseColors,
    /** 지금 모드(라이트/다크)에 맞춰 고른 구단 색 */
    val teams: Map<TeamId, Color>,
    val spacing: Spacing = Spacing(),
    val radii: Radii = Radii(),
    val sizes: Sizes = Sizes(),
    val motion: Motion = Motion(),
) {
    fun teamColor(teamId: TeamId): Color = teams[teamId] ?: base.brand
}

internal val LocalAppTokens = staticCompositionLocalOf<AppTokens> {
    error("AppTheme 밖에서 디자인 토큰을 읽었다")
}

// ---------- 값 (라이트 / 다크) ----------

internal val LightGrade = GradeColors(
    elite = Color(0xFF7B4DFF),
    high = Color(0xFF2F7BFF),
    solid = Color(0xFF1FA463),
    fringe = Color(0xFFC98A12),
    low = Color(0xFF8A919C),
)

internal val DarkGrade = GradeColors(
    elite = Color(0xFFB39DFF),
    high = Color(0xFF7FB0FF),
    solid = Color(0xFF5FD69A),
    fringe = Color(0xFFF0BE55),
    low = Color(0xFF8A919C),
)

internal val LightSemantic = SemanticColors(
    good = Color(0xFF1E9E5A),
    warn = Color(0xFFD08A00),
    bad = Color(0xFFE0413A),
)

internal val DarkSemantic = SemanticColors(
    good = Color(0xFF58D08E),
    warn = Color(0xFFF2C04E),
    bad = Color(0xFFFF7B72),
)

internal val LightBase = BaseColors(
    background = Color(0xFFF2F4F8),
    card = Color(0xFFFFFFFF),
    cardInset = Color(0xFFEDF0F5),
    rowAlt = Color(0xFFF7F8FB),
    line = Color(0xFFE2E6ED),
    text = Color(0xFF161A22),
    textSecondary = Color(0xFF4A5261),
    textMuted = Color(0xFF8A919C),
    brand = Color(0xFF3B6CF6),
    onBrand = Color(0xFFFFFFFF),
    onTeam = Color(0xFFFFFFFF),
)

internal val DarkBase = BaseColors(
    background = Color(0xFF0F1320),
    card = Color(0xFF1A2030),
    cardInset = Color(0xFF252C3E),
    rowAlt = Color(0xFF1E2435),
    line = Color(0xFF2A3244),
    text = Color(0xFFEDF0F6),
    textSecondary = Color(0xFFB3BAC7),
    textMuted = Color(0xFF7D8594),
    brand = Color(0xFF7EA2FF),
    onBrand = Color(0xFF0F1320),
    // 다크 모드 구단 색은 밝은 변형이라 흰 글자가 묻힌다 → 짙은 글자
    onTeam = Color(0xFF10131C),
)

/** `#RRGGBB` → 색. 형식은 teams.json 검증기(TeamTemplates.validate)가 이미 확인했다 */
internal fun parseHexColor(hex: String): Color =
    Color(0xFF000000 or hex.removePrefix("#").toLong(16))

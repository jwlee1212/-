package baseballgm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import baseballgm.model.TeamId
import baseballgm.scouting.ScoutingAccuracy
import baseballgm.scouting.ScoutingPrecision

/** 화면 한 덩어리. 제목 + 내용. 둥근 흰 카드 (docs/16 §7). */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = AppTheme.tokens
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.radii.card),
        // 기본 글자는 본문색. Material 은 이 바탕에 보조 회색을 골라서 본문이 흐려졌다 (글자 위계: 본문 진하게, 보조만 회색)
        colors = CardDefaults.cardColors(containerColor = tokens.base.card, contentColor = tokens.base.text),
    ) {
        Column(Modifier.padding(tokens.spacing.l)) {
            // 제목이 탭 이름 등을 되풀이할 때는 null 로 뺀다 (절제 규칙 "중복 문구 금지")
            if (title != null || trailing != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    trailing?.invoke()
                }
                Spacer(Modifier.height(tokens.spacing.s))
            }
            content()
        }
    }
}

/**
 * 카드 안 구역 나누기. 카드 안에 카드를 넣지 않고 여백 + 얇은 선으로 나눈다 (절제 규칙 "카드 중첩 금지").
 */
@Composable
fun SectionDivider() {
    val tokens = AppTheme.tokens
    androidx.compose.material3.HorizontalDivider(
        Modifier.padding(vertical = tokens.spacing.m),
        color = tokens.base.line,
    )
}

/**
 * 누르면 다른 화면으로 가는 한 줄: 제목(본문) + 요약(보조 회색) + 화살표 아이콘.
 * 메인 화면은 요약만 두고 자세한 건 이 줄로 보낸다 (절제 규칙 "점진 공개").
 */
@Composable
fun NavRow(
    title: String,
    caption: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    captionColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val tokens = AppTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = tokens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(tokens.spacing.s))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = captionColor ?: tokens.base.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) {
            androidx.compose.material3.Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = tokens.base.textMuted,
            )
        }
    }
}

/**
 * 작은 상태 칩. **상태(부상·폼·등급)에만** 쓰고 한 행에 최대 2개 (docs/16 절제 규칙).
 * 설명용 라벨은 칩이 아니라 회색 글자로 쓴다. 색만으로 구분하지 않도록 항상 글자를 담는다
 */
@Composable
fun Pill(text: String, color: Color = AppColors.muted, modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    Box(
        modifier
            .clip(RoundedCornerShape(tokens.radii.chip))
            .background(color.copy(alpha = if (tokens.isDark) 0.22f else 0.14f))
            .padding(horizontal = tokens.spacing.s),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

/** 이름 = 값 한 줄. */
@Composable
fun StatRow(label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = AppTheme.tokens.spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 능력치 막대 — 이 게임의 핵심 시각 요소 (docs/16 §5).
 *
 * - 정확히 아는 값(우리 팀): 선명한 막대 + 숫자 하나
 * - 범위로만 아는 값(타 팀): **범위 구간만 칠한 옅은 막대** + "62~72".
 *   범위가 좁을수록(정보 정확도가 높을수록) 막대가 진해진다. 옅은 정도는 가장 부정확한
 *   정보([ScoutingAccuracy.MINIMAL])를 기준으로 잰다.
 *   예전엔 양 끝이 흐려지는 그라데이션이었는데, 절제 규칙(그라데이션 금지, 2026-10-01)으로 단색 농도로 바꿨다.
 */
@Composable
fun RatingBar(label: String, low: Int, high: Int, modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    val exact = low == high
    val color = tokens.grade.of((low + high) / 2.0)
    val barHeight = 8.dp
    Row(
        modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs).semantics {
            contentDescription = if (exact) "$label $low" else "$label ${low}에서 $high 사이"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(barHeight)
                .clip(CircleShape)
                .background(tokens.base.cardInset),
        ) {
            val lowFraction = low.coerceIn(1, 100) / 100f
            val highFraction = high.coerceIn(1, 100) / 100f
            if (exact) {
                Box(Modifier.fillMaxWidth(highFraction).height(barHeight).clip(CircleShape).background(color))
            } else {
                val sharpness = 1f - ((high - low) / 2f / ScoutingAccuracy.MINIMAL.halfWidth).coerceIn(0f, 1f)
                // 범위 앞쪽 빈칸 + 범위 막대. 그라데이션 금지(절제 규칙)라 **단색 농도**로 정확도를 보인다
                Row(Modifier.fillMaxWidth(highFraction)) {
                    if (lowFraction > 0f) Spacer(Modifier.weight(lowFraction))
                    Box(
                        Modifier
                            .weight((highFraction - lowFraction).coerceAtLeast(0.01f))
                            .height(barHeight)
                            .clip(CircleShape)
                            .background(color.copy(alpha = RANGE_ALPHA_MIN + RANGE_ALPHA_SPAN * sharpness)),
                    )
                }
            }
        }
        Spacer(Modifier.width(tokens.spacing.s))
        Text(
            if (exact) "$low" else "$low~$high",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

/** 범위 막대 농도: 가장 넓은 범위 → 가장 좁은 범위 */
internal const val RANGE_ALPHA_MIN = 0.3f
internal const val RANGE_ALPHA_SPAN = 0.55f

/**
 * 팀 전력 막대 (로스터 화면, 2026-10-01). [RatingBar] 와 같은 표현 규칙을 팀 전력 스케일에 쓴다.
 *
 * - [low] == [high] (우리 팀): 축 왼쪽부터 값까지 선명하게 채운다
 * - 범위(타 팀): 범위 구간만 칠한 단색 막대. 넓을수록 옅다 (그라데이션 금지)
 * - [ghost]: 같은 색을 옅게 깔아 "여기까지 올라갈 수 있다"를 보여준다 (베스트 전력)
 * - [marker]: 가는 세로선 (리그 평균)
 *
 * 팀 전력은 40~90 에 몰려 있어서 1~100 축이면 차이가 안 보인다. 그래서 [axis] 를 화면이 고른다.
 */
@Composable
fun StrengthBar(
    low: Double,
    high: Double,
    color: Color,
    axis: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
    ghost: Double? = null,
    marker: Double? = null,
) {
    val tokens = AppTheme.tokens
    val track = tokens.base.cardInset
    val markerColor = tokens.base.textSecondary
    fun fraction(value: Double): Float =
        ((value - axis.start) / (axis.endInclusive - axis.start)).toFloat().coerceIn(0f, 1f)
    Canvas(modifier.height(10.dp).clip(CircleShape)) {
        val w = size.width
        val h = size.height
        drawRect(track)
        ghost?.let { drawRect(color.copy(alpha = 0.28f), size = Size(w * fraction(it), h)) }
        if (low == high) {
            drawRect(color, size = Size(w * fraction(high), h))
        } else {
            val start = w * fraction(low)
            val end = w * fraction(high)
            // 범위 반폭 10(전력 스케일)이면 가장 옅게, 0 에 가까울수록 진하게
            val sharpness = 1f - ((high - low) / 2.0 / 10.0).toFloat().coerceIn(0f, 1f)
            drawRect(
                color.copy(alpha = RANGE_ALPHA_MIN + RANGE_ALPHA_SPAN * sharpness),
                topLeft = Offset(start, 0f),
                size = Size((end - start).coerceAtLeast(1f), h),
            )
        }
        marker?.let {
            val x = w * fraction(it)
            drawRect(markerColor, topLeft = Offset(x - 1f, 0f), size = Size(2f, h))
        }
    }
}

/** 0~100 짜리 게이지 (피로도 등). */
@Composable
fun MeterBar(value: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth((value.coerceIn(0, 100)) / 100f)
                .height(6.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * 정보 정확도 게이지 + 비서 코멘트 (docs/16 §5). 타 팀 선수 화면 맨 위에 둔다.
 * 게이지 칸 수는 범위 반폭으로 정한다 — 반폭이 0 이면 가득, 가장 흐린 정보면 한 칸.
 */
@Composable
fun AccuracyGauge(precision: ScoutingPrecision, modifier: Modifier = Modifier) {
    SecretaryCard(
        message = accuracyComment(precision),
        modifier = modifier,
    ) { AccuracyGaugeRow(precision) }
}

/** 정보 정확도 게이지 한 줄 (라벨 · 칸 · 등급 칩). 다른 비서 카드 안에 넣을 때 쓴다 (선수 프로필 요약, 2026-10-03) */
@Composable
fun AccuracyGaugeRow(precision: ScoutingPrecision) {
    val tokens = AppTheme.tokens
    val maxWidth = ScoutingAccuracy.MINIMAL.halfWidth.toFloat()
    val filled = (1 + ((1f - precision.halfWidth / maxWidth).coerceIn(0f, 1f) * (GAUGE_CELLS - 1)))
        .toInt().coerceIn(1, GAUGE_CELLS)
    val color = when {
        filled >= GAUGE_CELLS - 1 -> tokens.semantic.good
        filled >= GAUGE_CELLS / 2 -> tokens.semantic.warn
        else -> tokens.semantic.bad
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "정보 정확도",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(tokens.spacing.s))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
            repeat(GAUGE_CELLS) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (index < filled) color else tokens.base.cardInset),
                )
            }
        }
        Spacer(Modifier.width(tokens.spacing.s))
        Pill(precision.label, color)
    }
}

private fun accuracyComment(precision: ScoutingPrecision): String = when {
    precision.isExact && precision.traitsExact -> "우리 선수라 숫자는 정확해요. 잠재력만 등급으로 봐 주세요."
    precision.isExact -> "1군에서 충분히 뛴 선수라 기록으로 능력치가 다 드러나 있어요. 잠재력·성향만 추정이에요."
    precision.halfWidth <= ScoutingAccuracy.HIGH.halfWidth -> "오래 지켜본 선수예요. 범위가 꽤 좁혀졌어요."
    precision.halfWidth <= ScoutingAccuracy.MEDIUM.halfWidth -> "1군에서 뛰는 걸 봐 온 정도예요. 대략적인 그림은 나와요."
    else -> "정보가 아직 부족해요. 숫자는 넓게 잡아서 보시는 게 좋아요."
}

private const val GAUGE_CELLS = 5

// ---------- 비서 ----------

/** 비서 캐릭터 (docs/16 §1). 이름은 가칭 — 여기 한 곳에서만 바꾼다 */
object Secretary {
    const val NAME: String = "미스 백"
}

/**
 * 비서 아바타 — 단색 플랫 벡터 (docs/16 §9).
 *
 * 실사·AI 얼굴 금지 원칙대로 **얼굴 없는 실루엣**만 그린다: 머리, 올림머리, 어깨와 V 깃.
 * 브랜드 색 원 위에 한 가지 색으로만 칠한다.
 */
@Composable
fun SecretaryAvatar(size: Dp = 36.dp) {
    val tokens = AppTheme.tokens
    val background = tokens.base.brand
    val ink = tokens.base.onBrand
    Canvas(
        Modifier
            .size(size)
            .clip(CircleShape)
            .semantics { contentDescription = "비서 ${Secretary.NAME}" },
    ) {
        drawRect(background)
        val w = this.size.width
        val h = this.size.height
        // 어깨
        drawOval(ink, topLeft = Offset(w * 0.14f, h * 0.66f), size = Size(w * 0.72f, h * 0.62f))
        // V 깃 (바탕색으로 파낸다)
        val collar = Path().apply {
            moveTo(w * 0.40f, h * 0.68f)
            lineTo(w * 0.50f, h * 0.84f)
            lineTo(w * 0.60f, h * 0.68f)
            close()
        }
        drawPath(collar, background)
        // 목·머리
        drawRect(ink, topLeft = Offset(w * 0.44f, h * 0.52f), size = Size(w * 0.12f, h * 0.16f))
        drawCircle(ink, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.40f))
        // 올림머리
        drawCircle(ink, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.19f))
    }
}

/**
 * 팬 아바타 — 단색 플랫 실루엣. 글쓴이 이름으로 모자를 쓸지 정해 글마다 조금씩 달라 보이게 한다.
 */
@Composable
fun FanAvatar(author: String, size: Dp = 32.dp) {
    val tokens = AppTheme.tokens
    val background = tokens.base.cardInset
    val ink = tokens.base.textMuted
    val cap = author.hashCode() % 2 == 0
    Canvas(Modifier.size(size).clip(CircleShape)) {
        drawRect(background)
        val w = this.size.width
        val h = this.size.height
        drawOval(ink, topLeft = Offset(w * 0.16f, h * 0.68f), size = Size(w * 0.68f, h * 0.6f))
        drawCircle(ink, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.44f))
        if (cap) {
            // 야구 모자: 반원 + 챙
            drawArc(ink, 180f, 180f, useCenter = true, topLeft = Offset(w * 0.30f, h * 0.22f), size = Size(w * 0.40f, h * 0.30f))
            drawRect(ink, topLeft = Offset(w * 0.48f, h * 0.35f), size = Size(w * 0.30f, h * 0.05f))
        }
    }
}

/**
 * 비서 메시지 카드 — 메모지·보고서를 대신한다 (docs/16 §2).
 * 아바타 + 말풍선 문장 + (있으면) 핵심 숫자·버튼 같은 내용.
 * 바탕은 일반 카드와 같은 중립색이다 — 비서 카드라는 건 아바타로 알 수 있다 (절제 규칙 "색은 면이 아니라 점에").
 */
@Composable
fun SecretaryCard(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    hero: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val tokens = AppTheme.tokens
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.radii.card),
        // 기본 글자는 본문색. Material 은 이 바탕에 보조 회색을 골라서 본문이 흐려졌다 (글자 위계: 본문 진하게, 보조만 회색)
        colors = CardDefaults.cardColors(containerColor = tokens.base.card, contentColor = tokens.base.text),
    ) {
        Column(Modifier.padding(tokens.spacing.l)) {
            Row(verticalAlignment = Alignment.Top) {
                SecretaryAvatar()
                Spacer(Modifier.width(tokens.spacing.m))
                Column(Modifier.weight(1f)) {
                    // 이름은 여기서만 붙인다. 부르는 쪽은 맥락("9주차", "화요일 경기 뒤")만 넘긴다
                    Text(
                        if (title == null) Secretary.NAME else "${Secretary.NAME} · $title",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(tokens.spacing.xs))
                    // 주인공 카드(홈 브리핑)면 비서 한 줄이 화면에서 가장 큰 글자다 (절제 규칙 "화면당 주인공 하나")
                    Text(
                        message,
                        style = if (hero) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                        color = tokens.base.text,
                    )
                }
            }
            if (content != null) {
                Spacer(Modifier.height(tokens.spacing.m))
                content()
            }
        }
    }
}

// ---------- 구단 ----------

/**
 * 구단 엠블럼. 단순 도형(둥근 사각) + 글자 한 자 (docs/16 §9).
 * 실제 구단 로고를 연상시키지 않도록 모양은 모든 구단이 같고 색·글자만 다르다.
 */
@Composable
fun TeamEmblem(teamId: TeamId, letter: String, size: Dp = 36.dp) {
    val tokens = AppTheme.tokens
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(tokens.teamColor(teamId)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = MaterialTheme.typography.titleSmall, color = tokens.base.onTeam, fontWeight = FontWeight.Bold)
    }
}

// ---------- 표 ----------

/** 표의 숫자 열 하나 */
data class TableColumn(val header: String, val width: Dp = 44.dp)

/**
 * 긴 표 — **첫 열 고정, 나머지 가로 스크롤** (docs/16 §3).
 *
 * 첫 열(이름)은 항상 보이고, 숫자 열은 고정 폭 + 오른쪽 정렬 + 고정폭 숫자라 세로로 줄이 맞는다.
 */
@Composable
fun StatTable(
    firstHeader: String,
    firstWidth: Dp,
    columns: List<TableColumn>,
    rowCount: Int,
    firstCell: @Composable RowScope.(row: Int) -> Unit,
    cell: (row: Int, column: Int) -> TableCell,
    sortedColumn: Int? = null,
    sortDescending: Boolean = true,
    onSort: ((column: Int) -> Unit)? = null,
    onRowClick: ((row: Int) -> Unit)? = null,
) {
    val tokens = AppTheme.tokens
    val scroll = rememberScrollState()
    val rowHeight = 28.dp
    // 정렬한 열이 화면 밖이면 그 열이 보이도록 가로로 넘겨 둔다 (폰 폭에선 OPS·WAR 가 오른쪽에 숨는다)
    if (sortedColumn != null) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        // 열마다 왼쪽 끝 위치(px). 넘길 때는 열 경계에 맞춘다 — 중간에 멈추면 "0.297"이 "297"처럼 잘려 보인다
        val edges = with(density) { columns.runningFold(0) { acc, column -> acc + column.width.roundToPx() } }
        val start = edges[sortedColumn]
        val end = edges[sortedColumn + 1]
        androidx.compose.runtime.LaunchedEffect(sortedColumn, scroll.viewportSize) {
            val viewport = scroll.viewportSize
            if (viewport > 0 && (start < scroll.value || end > scroll.value + viewport)) {
                val target = edges.firstOrNull { it >= end - viewport } ?: start
                scroll.scrollTo(target.coerceAtMost(start))
            }
        }
    }
    // 선수 목록과 같은 위계: 구분선 대신 짝수 줄에 아주 옅은 바탕 (2026-10-02 전 화면 통일)
    val stripe = { row: Int -> if (row % 2 == 1) tokens.base.rowAlt else Color.Transparent }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(firstWidth)) {
            Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                Text(firstHeader, style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
            }
            repeat(rowCount) { row ->
                Row(
                    Modifier.fillMaxWidth().height(rowHeight).background(stripe(row))
                        .then(if (onRowClick != null) Modifier.clickable { onRowClick(row) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) { firstCell(row) }
            }
        }
        Column(Modifier.weight(1f).horizontalScroll(scroll)) {
            Row(Modifier.height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                columns.forEachIndexed { index, column ->
                    // 정렬할 수 있는 표: 머리글을 누르면 그 열로 정렬, 지금 정렬한 열은 본문색 굵게 + 방향 화살표
                    val sorted = index == sortedColumn
                    Row(
                        Modifier.width(column.width).height(rowHeight)
                            .then(if (onSort != null) Modifier.clickable { onSort(index) } else Modifier)
                            .semantics { if (onSort != null) contentDescription = "${column.header} 순으로 정렬" + if (sorted) " (지금 ${if (sortDescending) "높은" else "낮은"} 순)" else "" },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (sorted) {
                            androidx.compose.material3.Icon(
                                if (sortDescending) androidx.compose.material.icons.Icons.Filled.ArrowDownward else androidx.compose.material.icons.Icons.Filled.ArrowUpward,
                                contentDescription = null,
                                tint = tokens.base.text,
                                modifier = Modifier.size(tokens.sizes.statusIcon),
                            )
                        }
                        Text(
                            column.header,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sorted) tokens.base.text else tokens.base.textMuted,
                            fontWeight = if (sorted) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    }
                }
            }
            repeat(rowCount) { row ->
                Row(
                    Modifier.height(rowHeight).background(stripe(row))
                        .then(if (onRowClick != null) Modifier.clickable { onRowClick(row) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    columns.forEachIndexed { index, column ->
                        val value = cell(row, index)
                        Text(
                            value.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = value.color ?: tokens.base.text,
                            // 정렬한 열은 굵게 — 무엇으로 줄을 세웠는지 숫자에서 바로 보인다
                            fontWeight = if (value.bold || index == sortedColumn) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.width(column.width),
                        )
                    }
                }
            }
        }
    }
}

/** 표 칸 하나. 색은 호출하는 쪽이 토큰에서 골라 넣는다 */
data class TableCell(val text: String, val color: Color? = null, val bold: Boolean = false)

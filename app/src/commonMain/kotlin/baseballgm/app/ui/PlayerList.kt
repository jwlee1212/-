package baseballgm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.scouting.RatingRange
import baseballgm.scouting.ScoutingAccuracy

/*
 * 선수 표시 한 벌 (2026-10-02, 레퍼런스 리팩토링 → 같은 날 전 화면 통일).
 *
 * - [PlayerIdentity]: 선수가 나오는 곳이면 어디서든 같은 모양 — 구단 색 원 안 등번호 · 이름(Bold) · 상태 아이콘(최대 2) /
 *   포지션 배지 · 회색 보조 글자. 목록, 한 줄짜리 행([PlayerLine]), 선수 상세 머리가 모두 이것을 쓴다.
 * - [PlayerList] / [playerListItems]: 왼쪽 고정(정체) + 오른쪽 숫자 가로 스크롤 표 (FMM 식 위계: 선 대신 굵기·색·줄무늬).
 *   능력치는 숫자 아래 미니 막대(등급 색).
 * - 불확실성(docs/16 §5): 능력치는 [RatingRange] 로만 받는다 — 엔진의 ScoutingView 만 만드는 타입이라
 *   이 컴포넌트는 진짜 능력치를 받을 통로가 없다 (불변 원칙 4). 우리 선수는 숫자 하나 + 선명한 막대,
 *   타 팀은 "62~72" + 범위 구간만 칠한 옅은 단색 막대 (정확도가 높을수록 진하다).
 */

// ---------- 데이터 ----------

/** 의미 색 중 무엇을 쓸지 (색은 그릴 때 토큰에서 고른다) */
enum class StatusTone { GOOD, WARN, BAD, MUTED }

/**
 * 이름 옆 상태 아이콘. **모양이 전부 다르다** — 색만으로 구분하지 않는다 (docs/16 §6).
 * 글자는 접근성 라벨로 읽힌다.
 */
enum class PlayerStatus(val icon: ImageVector, val tone: StatusTone) {
    INJURED(Icons.Filled.LocalHospital, StatusTone.BAD),
    MILITARY(Icons.Filled.MilitaryTech, StatusTone.MUTED),
    RESTING(Icons.Filled.Bedtime, StatusTone.WARN),
    FORM_UP(Icons.AutoMirrored.Filled.TrendingUp, StatusTone.GOOD),
    FORM_DOWN(Icons.AutoMirrored.Filled.TrendingDown, StatusTone.BAD),

    /** 만족도 불만 이하 · 이적 희망 (docs/13, 2026-10-04) */
    UNHAPPY(Icons.Filled.SentimentDissatisfied, StatusTone.BAD),

    /** 스카우트 집중 관찰 중 (드래프트 후보·외국인 후보) */
    WATCHING(Icons.Filled.Visibility, StatusTone.GOOD),

    /** 이름난 고교 유망주 — 관찰 전부터 자료가 꽤 있다 (docs/10) */
    KNOWN_PROSPECT(Icons.Filled.Star, StatusTone.WARN),

    /** 우리가 조건을 낸 FA — 우리가 1순위이고 선수도 만족 */
    OFFERED(Icons.Filled.Handshake, StatusTone.GOOD),

    /** 우리가 1순위지만 선수가 아직 망설인다 */
    OFFER_WAITING(Icons.Filled.HourglassTop, StatusTone.WARN),

    /** 다른 구단 조건에 밀렸다 */
    OUTBID(Icons.Filled.Gavel, StatusTone.BAD),
}

/** 상태 하나 + 읽어 줄 글자 ("햄스트링 2주", "폼 급상승") */
data class StatusBadge(val status: PlayerStatus, val label: String)

/** 한 줄에 붙이는 상태 배지 최대 수 (docs/16 절제 규칙 "칩은 한 행에 최대 2개") */
const val MAX_STATUS_BADGES = 2

/**
 * 선수 정체 — 화면 어디서든 같은 모양으로 그린다.
 * @param number 등번호. 아마추어(드래프트 후보)처럼 번호가 없으면 null → 배지 자리에 빈 회색 원
 * @param teamId 소속. 없으면(FA·아마추어) 배지는 중립 회색
 */
data class PlayerTag(
    val id: PlayerId,
    val name: String,
    val number: Int?,
    val teamId: TeamId?,
    val position: String,
    /** 포지션 배지 옆 회색 글자 (나이 등) */
    val caption: String,
    val badges: List<StatusBadge> = emptyList(),
    /** 올해 데뷔한 신인. 상태가 아니라 정체 표시라서 상태 배지 수(최대 2)에 세지 않는다 */
    val rookie: Boolean = false,
    /** 팀내 핵심 유망주 (2026-10-04). 정체 표시라 상태 배지 수에 세지 않는다 */
    val coreProspect: Boolean = false,
    /** FA 시장: 직전 시즌 우리 팀 소속이었으면 우리 구단. 신인 표시처럼 정체 표시라 상태 배지 수에 세지 않는다 */
    val ourFormerTeam: TeamId? = null,
)

/** 목록의 숫자 칸 하나 */
sealed interface ListCell {
    /** 능력치. 숫자 + 미니 막대. [emphasized] 면 굵게 (종합) */
    data class Rating(val range: RatingRange, val emphasized: Boolean = false) : ListCell

    /** 기록·계약 같은 공개 숫자. [tone] 이 있으면 그 의미 색 (예: 피로 높음 = 나쁨) */
    data class Value(val text: String, val tone: StatusTone? = null, val bold: Boolean = false) : ListCell

    /** 칸 안의 작은 글자 버튼 (트레이드 목록의 "비교+"). [active] 면 좋음 색 */
    data class Action(val label: String, val active: Boolean = false, val onClick: () -> Unit) : ListCell
}

data class PlayerListRow(
    val tag: PlayerTag,
    val cells: List<ListCell>,
    /** 고른 줄(트레이드에 넣은 선수 등). 줄무늬보다 한 단계 진한 바탕으로 보인다 */
    val selected: Boolean = false,
) {
    val id: PlayerId get() = tag.id
}

/** 한 묶음(타자·투수). 묶음마다 열 구성이 달라 머리줄을 따로 둔다 */
data class PlayerListSection(val title: String, val columns: List<TableColumn>, val rows: List<PlayerListRow>)

/** 줄 끝에 고정되는 글자 버튼 ("말소", "관찰", "넣기") */
data class RowAction(val label: String, val onClick: () -> Unit)

// ---------- 정체 ----------

/** 왼쪽 고정 영역 폭: 배지 + 이름(4자) + 상태 아이콘 둘 */
private val FIXED_WIDTH = 140.dp

/** 등번호 배지 · 이름 · 상태 아이콘 / 포지션 배지 · 보조 글자 */
@Composable
fun PlayerIdentity(tag: PlayerTag, modifier: Modifier = Modifier, large: Boolean = false) {
    val tokens = AppTheme.tokens
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        NumberBadge(tag.number, tag.teamId, if (large) tokens.sizes.numberBadgeLarge else tokens.sizes.numberBadge)
        Spacer(Modifier.width(if (large) tokens.spacing.m else tokens.spacing.s))
        Column(Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tag.name,
                    style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.base.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tag.rookie) {
                    Spacer(Modifier.width(tokens.spacing.xs))
                    RookieMark()
                }
                if (tag.coreProspect) {
                    Spacer(Modifier.width(tokens.spacing.xs))
                    CoreProspectMark()
                }
                tag.ourFormerTeam?.let { team ->
                    Spacer(Modifier.width(tokens.spacing.xs))
                    OurFormerMark(team)
                }
                tag.badges.take(MAX_STATUS_BADGES).forEach { badge ->
                    Spacer(Modifier.width(tokens.spacing.xs))
                    StatusIcon(badge)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PositionBadge(tag.position)
                Spacer(Modifier.width(tokens.spacing.xs))
                Text(
                    tag.caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.base.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 선수 한 줄(표가 아닌 곳): 정체 + 오른쪽 내용. 누르면 [onClick] (보통 선수 상세) — 그때는 화살표 아이콘이 붙는다.
 * 카드 안에서 쓰므로 바탕을 칠하지 않는다 (카드 중첩 금지).
 */
@Composable
fun PlayerLine(
    tag: PlayerTag,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = AppTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIdentity(tag, Modifier.weight(1f))
        if (trailing != null) {
            Spacer(Modifier.width(tokens.spacing.s))
            trailing()
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.base.textMuted)
        }
    }
}

// ---------- 표 ----------

/** 묶음마다의 가로 스크롤 상태. 지연 목록(LazyColumn) 안에서도 머리줄·줄이 같이 움직이게 화면이 하나 들고 있는다 */
class PlayerListScroll {
    private val states = mutableMapOf<String, ScrollState>()
    fun of(section: String): ScrollState = states.getOrPut(section) { ScrollState(0) }
}

@Composable
fun rememberPlayerListScroll(): PlayerListScroll = remember { PlayerListScroll() }

/**
 * 선수 목록(지연 없음) — 40명 안쪽 목록용. 목록 전체가 둥근 카드 하나다.
 * @param action 줄 끝에 고정되는 글자 버튼. null 을 돌려주면 그 줄엔 버튼이 없다
 */
@Composable
fun PlayerList(
    sections: List<PlayerListSection>,
    onPlayer: (PlayerId) -> Unit,
    modifier: Modifier = Modifier,
    action: ((PlayerListRow) -> RowAction?)? = null,
) {
    val tokens = AppTheme.tokens
    val scroll = rememberPlayerListScroll()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radii.card))
            .background(tokens.base.card)
            .padding(vertical = tokens.spacing.s),
    ) {
        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(tokens.spacing.m))
            val state = scroll.of(section.title)
            PlayerListHeader(section, state, hasAction = action != null)
            section.rows.forEachIndexed { rowIndex, row ->
                PlayerListRowView(row, section.columns, state, rowIndex % 2 == 1, onPlayer, action != null, action?.invoke(row))
            }
        }
    }
}

/**
 * 선수 목록(지연) — 드래프트 풀처럼 긴 목록용. LazyColumn 안에 줄 단위로 들어간다.
 * 둥근 카드 모양은 머리줄(위 모서리)과 마지막 줄(아래 모서리)이 나눠 그린다. 묶음 사이 간격은 부르는 쪽 목록이 정한다.
 */
fun LazyListScope.playerListItems(
    section: PlayerListSection,
    scroll: PlayerListScroll,
    onPlayer: (PlayerId) -> Unit,
    action: ((PlayerListRow) -> RowAction?)? = null,
) {
    val state = scroll.of(section.title)
    item(key = "header-${section.title}") {
        val tokens = AppTheme.tokens
        val radius = tokens.radii.card
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = radius, topEnd = radius))
                .background(tokens.base.card)
                .padding(top = tokens.spacing.s),
        ) { PlayerListHeader(section, state, hasAction = action != null) }
    }
    itemsIndexed(section.rows, key = { _, row -> "${section.title}-${row.id.value}" }) { index, row ->
        val tokens = AppTheme.tokens
        val last = index == section.rows.lastIndex
        val shape: Shape = if (last) {
            RoundedCornerShape(bottomStart = tokens.radii.card, bottomEnd = tokens.radii.card)
        } else {
            RoundedCornerShape(0.dp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(tokens.base.card)
                .then(if (last) Modifier.padding(bottom = tokens.spacing.s) else Modifier),
        ) { PlayerListRowView(row, section.columns, state, index % 2 == 1, onPlayer, action != null, action?.invoke(row)) }
    }
}

@Composable
private fun PlayerListHeader(section: PlayerListSection, scroll: ScrollState, hasAction: Boolean) {
    val tokens = AppTheme.tokens
    Row(
        Modifier.fillMaxWidth().height(tokens.sizes.listHeader).padding(horizontal = tokens.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${section.title} ${section.rows.size}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.base.textSecondary,
            modifier = Modifier.width(FIXED_WIDTH),
        )
        Row(Modifier.weight(1f).horizontalScroll(scroll)) {
            section.columns.forEach { column ->
                Text(
                    column.header,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.base.textMuted,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(column.width),
                )
            }
            Spacer(Modifier.width(tokens.spacing.m))
        }
        if (hasAction) Spacer(Modifier.width(ACTION_WIDTH))
    }
}

private val ACTION_WIDTH = 52.dp

@Composable
private fun PlayerListRowView(
    row: PlayerListRow,
    columns: List<TableColumn>,
    scroll: ScrollState,
    striped: Boolean,
    onPlayer: (PlayerId) -> Unit,
    hasAction: Boolean,
    action: RowAction?,
) {
    val tokens = AppTheme.tokens
    val background = when {
        row.selected -> tokens.base.cardInset
        striped -> tokens.base.rowAlt
        else -> tokens.base.card
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(tokens.sizes.listRow)
            .background(background)
            .clickable { onPlayer(row.id) }
            .padding(start = tokens.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ---- 고정: 정체 ----
        PlayerIdentity(row.tag, Modifier.width(FIXED_WIDTH))
        // ---- 가로 스크롤: 숫자 칸 ----
        Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            columns.forEachIndexed { index, column ->
                when (val cell = row.cells.getOrNull(index)) {
                    is ListCell.Rating -> RatingCell(cell, column.width)
                    is ListCell.Value -> Text(
                        cell.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = cell.tone?.let { toneColor(it) } ?: tokens.base.textSecondary,
                        fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.width(column.width),
                    )
                    is ListCell.Action -> Box(Modifier.width(column.width), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = cell.onClick, contentPadding = PaddingValues(horizontal = tokens.spacing.xs)) {
                            Text(cell.label, maxLines = 1, color = if (cell.active) tokens.semantic.good else tokens.base.brand)
                        }
                    }
                    null -> Spacer(Modifier.width(column.width))
                }
            }
            Spacer(Modifier.width(tokens.spacing.m))
        }
        if (hasAction) {
            Box(Modifier.width(ACTION_WIDTH), contentAlignment = Alignment.Center) {
                if (action != null) {
                    TextButton(onClick = action.onClick, contentPadding = PaddingValues(horizontal = tokens.spacing.s)) {
                        Text(action.label, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 능력치 칸: 숫자(등급 색) 아래 아주 얇은 막대 */
@Composable
private fun RatingCell(cell: ListCell.Rating, width: Dp) {
    val tokens = AppTheme.tokens
    val range = cell.range
    val color = tokens.grade.of(range.center)
    Column(
        Modifier.width(width).padding(start = tokens.spacing.m).semantics {
            contentDescription = if (range.isExact) "${range.low}" else "${range.low}에서 ${range.high} 사이"
        },
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            range.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (cell.emphasized) FontWeight.Bold else FontWeight.Normal,
            color = color,
            maxLines = 1,
        )
        MiniRatingBar(range, color, Modifier.fillMaxWidth())
    }
}

/**
 * 미니 막대. 정확한 값은 0 → 값까지 선명하게, 범위는 low → high 구간만 옅은 단색으로.
 * 진하기 규칙은 [RatingBar] 와 같다 (범위가 좁을수록 진하다, 그라데이션 금지).
 */
@Composable
fun MiniRatingBar(range: RatingRange, color: Color, modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    val track = tokens.base.cardInset
    Canvas(modifier.height(tokens.sizes.miniBar).clip(CircleShape)) {
        val w = size.width
        val h = size.height
        drawRect(track)
        val low = range.low.coerceIn(1, 100) / 100f
        val high = range.high.coerceIn(1, 100) / 100f
        if (range.isExact) {
            drawRect(color, size = Size(w * high, h))
        } else {
            val sharpness = 1f - ((range.high - range.low) / 2f / ScoutingAccuracy.MINIMAL.halfWidth).coerceIn(0f, 1f)
            drawRect(
                color.copy(alpha = RANGE_ALPHA_MIN + RANGE_ALPHA_SPAN * sharpness),
                topLeft = Offset(w * low, 0f),
                size = Size((w * (high - low)).coerceAtLeast(1f), h),
            )
        }
    }
}

// ---------- 배지 ----------

/**
 * 구단 색 원 안에 등번호. 작은 점이라 "구단 색 큰 면은 진행 버튼뿐" 규칙에 걸리지 않는다.
 * 번호가 없으면(아마추어) 빈 회색 원, 소속이 없으면(FA) 회색 원에 번호.
 */
@Composable
fun NumberBadge(number: Int?, teamId: TeamId?, size: Dp = AppTheme.tokens.sizes.numberBadge) {
    val tokens = AppTheme.tokens
    val filled = number != null && teamId != null
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) tokens.teamColor(teamId!!) else tokens.base.cardInset)
            .semantics { contentDescription = if (number != null) "등번호 $number" else "등번호 없음" },
        contentAlignment = Alignment.Center,
    ) {
        if (number != null) {
            Text(
                "$number",
                style = if (size > tokens.sizes.numberBadge) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (filled) tokens.base.onTeam else tokens.base.textSecondary,
            )
        }
    }
}

/** 포지션 배지: 중립 바탕 위 굵은 회색 글자. 상태가 아니라 칩 규칙(최대 2개)에 세지 않는다 */
@Composable
fun PositionBadge(position: String) {
    val tokens = AppTheme.tokens
    Box(
        Modifier
            .clip(RoundedCornerShape(tokens.radii.chip))
            .background(tokens.base.cardInset)
            .padding(horizontal = tokens.spacing.xs),
    ) {
        Text(position, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.base.textSecondary)
    }
}

/**
 * 신인 표시: 상태 배지와 같은 높이의 둥근 칩 + "신인" 글자, 브랜드 색.
 * 처음엔 "NEW" 아이콘이었는데 배지 크기에선 글자가 읽히지 않아 한글로 바꿨다.
 * 상태(부상·폼)와 헷갈리지 않게 의미 색(좋음·나쁨)이 아니라 브랜드 색을 쓴다.
 */
@Composable
fun RookieMark() = IdentityChip("신인", AppTheme.tokens.base.brand)

/**
 * 팀내 핵심 유망주 표시 (2026-10-04). 잠재력 이야기라 **등급 색**(높음)을 쓴다 — 신인(브랜드 색)·우리 팀(구단 색)·
 * 상태 배지(의미 색)와 겹치지 않는다.
 */
@Composable
fun CoreProspectMark() = IdentityChip("핵심 유망주", AppTheme.tokens.grade.high)

/**
 * FA 시장의 "우리 팀" 표시: 직전 시즌 우리 팀에서 뛰다 시장에 나온 선수. **우리 구단 색**이라
 * 브랜드 색인 신인 표시와도, 의미 색인 상태 배지와도 겹치지 않는다
 */
@Composable
fun OurFormerMark(team: TeamId) {
    IdentityChip("우리 팀", AppTheme.tokens.teamColor(team))
}

/** 이름 옆 정체 칩 (신인·우리 팀 출신). 상태 배지와 같은 높이 */
@Composable
private fun IdentityChip(text: String, color: androidx.compose.ui.graphics.Color) {
    val tokens = AppTheme.tokens
    Box(
        Modifier
            .height(tokens.sizes.statusBadge)
            .clip(RoundedCornerShape(tokens.radii.chip))
            .background(color.copy(alpha = if (tokens.isDark) 0.22f else 0.14f))
            .padding(horizontal = tokens.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

/** 상태 아이콘 배지: 옅은 원 + 같은 색 아이콘. 모양이 상태마다 달라 색맹이어도 구분된다 */
@Composable
fun StatusIcon(badge: StatusBadge) {
    val tokens = AppTheme.tokens
    val color = toneColor(badge.status.tone)
    Box(
        Modifier
            .size(tokens.sizes.statusBadge)
            .clip(CircleShape)
            .background(color.copy(alpha = if (tokens.isDark) 0.22f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(badge.status.icon, contentDescription = badge.label, tint = color, modifier = Modifier.size(tokens.sizes.statusIcon))
    }
}

@Composable
@ReadOnlyComposable
private fun toneColor(tone: StatusTone): Color {
    val tokens = AppTheme.tokens
    return when (tone) {
        StatusTone.GOOD -> tokens.semantic.good
        StatusTone.WARN -> tokens.semantic.warn
        StatusTone.BAD -> tokens.semantic.bad
        StatusTone.MUTED -> tokens.base.textMuted
    }
}

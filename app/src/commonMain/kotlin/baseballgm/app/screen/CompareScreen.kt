package baseballgm.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.MiniRatingBar
import baseballgm.app.ui.NumberBadge
import baseballgm.app.ui.Pill
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionDivider
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.foundation.layout.size
import baseballgm.app.ui.SectionCard
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.scouting.RatingRange
import baseballgm.util.fixed

/**
 * 선수 비교 (2026-10-01 유저 요청). 최대 4명을 열로 나란히 놓는다.
 *
 * 타 팀 선수 능력치는 **스카우트 범위로만** 나온다 (불변 원칙 4 — `session.scout` 를 거친다).
 * 그래서 "누가 더 높다"를 숫자 하나로 단정하지 않는다:
 * - 범위의 가운데가 가장 높은 칸을 굵게,
 * - 그 선수의 **최저값이 다른 모든 선수의 최고값 이상**이면 "확실히 앞섬"(위 화살표 아이콘)을 붙인다.
 *   범위가 겹치면 정보가 더 쌓여야 가려진다는 뜻이다.
 * 기록(타율·평균자책·WAR)은 공개된 숫자라 그대로 보여 준다.
 */
@Composable
fun CompareScreen(
    session: GameSession,
    playerIds: List<PlayerId>,
    onPlayer: (PlayerId) -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val players = playerIds.mapNotNull { id -> runCatching { session.player(id) }.getOrNull() }
    val tokens = AppTheme.tokens

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
    ) {
        if (players.size < 2) {
            SecretaryCard("비교하려면 두 명 이상 담아 주세요. 선수 상세나 트레이드 목록에서 \"비교+\"를 누르면 담겨요.")
            return@Column
        }
        SecretaryCard(compareComment(session, players))

        // 머리: 이름(누르면 상세) · 소속 · 포지션·나이 · 정보 정확도
        SectionCard("선수") {
            CompareRow(null) { _ ->
                players.forEach { player ->
                    val scouted = session.scout(player)
                    val own = session.isOwn(player)
                    Column(Modifier.weight(1f).clickable { onPlayer(player.id) }.padding(horizontal = AppTheme.tokens.spacing.xs)) {
                        // 다른 화면과 같은 정체 표시: 구단 색 원 안 등번호 (2026-10-02 전 화면 통일)
                        NumberBadge(player.uniformNumber.takeIf { it > 0 }, player.teamId)
                        Spacer(Modifier.height(tokens.spacing.xs))
                        Text(
                            player.registeredName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.base.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            player.teamId?.let { if (own) "우리 팀" else session.league.team(it).nickname } ?: "무소속",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (own) tokens.base.brand else tokens.base.textMuted,
                        )
                        Text("${scouted.positionLabel} · ${scouted.age}세", style = MaterialTheme.typography.labelSmall, color = tokens.base.textSecondary)
                        // 정확도는 칸마다 칩이면 한 줄에 칩이 셋 — 회색 글자로
                        Text("정확도 ${if (own) "정확" else scouted.precision.label}", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
                    }
                }
            }
        }

        SectionCard("능력치", trailing = { LeadMark("확실히 앞섬") }) {
            val ranges = players.map { session.scout(it) }
            RangeRow("종합", ranges.map { it.overall })
            val batterRows = players.any { it is Batter }
            val pitcherRows = players.any { it is Pitcher }
            if (batterRows) {
                SubHeader("타자")
                Attribute.batterAttributes.forEach { attribute ->
                    RangeRow(attribute.label, ranges.map { it.ratings[attribute] })
                }
            }
            if (pitcherRows) {
                SubHeader("투수")
                Attribute.pitcherAttributes.forEach { attribute ->
                    RangeRow(attribute.label, ranges.map { it.ratings[attribute] })
                }
            }
            TextRow("잠재력", ranges.map { it.potentialLabel })
        }

        SectionCard("${session.league.season} 기록·계약") {
            if (players.any { it is Batter }) {
                SubHeader("타자")
                StatValueRow("타율", players) { p -> (p as? Batter)?.let { session.batting(it.id).takeIf { l -> l.plateAppearances > 0 }?.battingAverage } }
                StatValueRow("OPS", players) { p -> (p as? Batter)?.let { session.batting(it.id).takeIf { l -> l.plateAppearances > 0 }?.ops } }
                StatValueRow("홈런", players, digits = 0) { p -> (p as? Batter)?.let { session.batting(it.id).takeIf { l -> l.plateAppearances > 0 }?.homeRuns?.toDouble() } }
                StatValueRow("타석", players, digits = 0, higherIsBetter = null) { p -> (p as? Batter)?.let { session.batting(it.id).plateAppearances.toDouble() } }
            }
            if (players.any { it is Pitcher }) {
                SubHeader("투수")
                StatValueRow("평균자책", players, digits = 2, higherIsBetter = false) { p -> (p as? Pitcher)?.let { session.pitching(it.id).takeIf { l -> l.outs > 0 }?.era } }
                StatValueRow("이닝", players, digits = 1) { p -> (p as? Pitcher)?.let { session.pitching(it.id).takeIf { l -> l.outs > 0 }?.inningsPitched } }
                StatValueRow("탈삼진", players, digits = 0) { p -> (p as? Pitcher)?.let { session.pitching(it.id).takeIf { l -> l.outs > 0 }?.strikeouts?.toDouble() } }
                StatValueRow("WHIP", players, digits = 2, higherIsBetter = false) { p -> (p as? Pitcher)?.let { session.pitching(it.id).takeIf { l -> l.outs > 0 }?.whip } }
            }
            SubHeader("공통")
            StatValueRow("WAR", players, digits = 1) { p -> session.war(p).war.takeIf { played(session, p) } }

            // 계약은 따로 카드였는데 섹션이 다섯이라 기록 카드에 합쳤다
            SectionDivider()
            SubHeader("계약")
            StatValueRow("연봉(억)", players, digits = 1, higherIsBetter = null) { it.contract.salary }
            StatValueRow("잔여(년)", players, digits = 0, higherIsBetter = null) { it.contract.yearsRemaining.toDouble() }
            TextRow("FA까지", players.map { if (it.contract.seasonsToFreeAgency == 0) "올해 후" else "${it.contract.seasonsToFreeAgency}시즌" })
            TextRow("병역", players.map { session.militaryLabel(it) })
            TextRow("상태", players.map { p -> p.condition.injury?.let { "${it.part} ${it.weeksRemaining}주" } ?: "정상" })
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (playerIds == session.compareList.toList()) {
                TextButton(onClick = { session.clearCompare() }) { Text("비교함 비우기") }
            }
        }
        Spacer(Modifier.height(AppTheme.tokens.spacing.s))
    }
}

/** 비서 한마디: 정보가 가장 흐린 선수를 짚는다 */
private fun compareComment(session: GameSession, players: List<Player>): String {
    val blurry = players.filter { !session.isOwn(it) }
        .maxByOrNull { session.scout(it).precision.halfWidth }
    return if (blurry == null) {
        "전부 우리 선수라 숫자는 정확해요. 잠재력만 등급으로 봐 주세요."
    } else {
        "다른 팀 선수는 범위로만 보여요. ${blurry.registeredName} 선수 정보가 가장 흐려요(${session.scout(blurry).precision.label}). " +
            "막대가 겹치면 사실상 비슷하다고 보시면 돼요."
    }
}

private fun played(session: GameSession, player: Player): Boolean = when (player) {
    is Batter -> session.batting(player.id).plateAppearances > 0
    is Pitcher -> session.pitching(player.id).outs > 0
}

// ---------- 행 ----------

/** 이름 칸 + 선수마다 같은 폭의 칸 */
@Composable
private fun CompareRow(label: String?, cells: @Composable RowScope.(Unit) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = AppTheme.tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(LABEL_WIDTH)) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = AppTheme.tokens.base.textSecondary)
            }
        }
        cells(Unit)
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.tokens.base.textMuted,
        modifier = Modifier.padding(top = AppTheme.tokens.spacing.s, bottom = AppTheme.tokens.spacing.xs),
    )
}

/** 능력치 범위 한 줄. 가운데가 가장 높은 칸 굵게, 최저값이 남들 최고값 이상이면 앞섬 표시 */
@Composable
private fun RangeRow(label: String, ranges: List<RatingRange?>) {
    val tokens = AppTheme.tokens
    val present = ranges.filterNotNull()
    val bestCenter = present.maxOfOrNull { it.center }
    CompareRow(label) {
        ranges.forEachIndexed { index, range ->
            Column(Modifier.weight(1f).padding(horizontal = AppTheme.tokens.spacing.xs)) {
                if (range == null) {
                    Text("—", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    return@Column
                }
                val others = ranges.filterIndexed { i, r -> i != index && r != null }.filterNotNull()
                val clear = others.isNotEmpty() && others.all { range.low >= it.high } && others.any { range.low > it.high }
                val best = present.size > 1 && range.center == bestCenter
                val color = tokens.grade.of(range.center)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        range.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (best) FontWeight.Bold else FontWeight.Normal,
                        color = if (clear) AppColors.good else color,
                    )
                    if (clear) LeadMark(null)
                }
                MiniRange(range)
            }
        }
    }
}

/** 0~100 위의 얇은 막대 — 선수 목록과 같은 [MiniRatingBar] (범위는 옅은 단색, 정확한 값은 선명하게) */
@Composable
private fun MiniRange(range: RatingRange) {
    MiniRatingBar(range, AppTheme.tokens.grade.of(range.center), Modifier.fillMaxWidth())
}

@Composable
private fun TextRow(label: String, values: List<String>) {
    CompareRow(label) {
        values.forEach { value ->
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                modifier = Modifier.weight(1f).padding(horizontal = AppTheme.tokens.spacing.xs),
            )
        }
    }
}

/**
 * 공개 기록 한 줄. [higherIsBetter] 가 정해져 있으면 가장 좋은 값을 굵게. null 이면 표시만 한다(연봉 등).
 */
@Composable
private fun StatValueRow(
    label: String,
    players: List<Player>,
    digits: Int = 3,
    higherIsBetter: Boolean? = true,
    value: (Player) -> Double?,
) {
    val values = players.map(value)
    val present = values.filterNotNull()
    val best = when (higherIsBetter) {
        true -> present.maxOrNull()
        false -> present.minOrNull()
        null -> null
    }
    CompareRow(label) {
        values.forEach { v ->
            Text(
                v?.fixed(digits) ?: "—",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (best != null && present.size > 1 && v == best) FontWeight.Bold else FontWeight.Normal,
                color = if (v == null) AppTheme.tokens.base.textMuted else AppTheme.tokens.base.text,
                modifier = Modifier.weight(1f).padding(horizontal = AppTheme.tokens.spacing.xs),
            )
        }
    }
}

private val LABEL_WIDTH = 56.dp

/** "확실히 앞섬" 표시. 글자 삼각형(▲) 대신 앱 아이콘 세트의 위 화살표 (절제 규칙 "아이콘은 한 세트") */
@Composable
private fun LeadMark(label: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Filled.ArrowDropUp,
            contentDescription = "확실히 앞섬",
            tint = AppColors.good,
            modifier = Modifier.size(AppTheme.tokens.spacing.l),
        )
        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.good)
    }
}

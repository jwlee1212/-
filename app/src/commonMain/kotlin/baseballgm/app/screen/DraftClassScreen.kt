package baseballgm.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.Pill
import baseballgm.app.ui.PlayerIdentity
import baseballgm.app.ui.RatingBar
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.SectionDivider
import baseballgm.app.ui.StatRow
import baseballgm.model.GrowthType
import baseballgm.market.DraftSelection
import baseballgm.model.Attribute
import baseballgm.model.PlayerId
import baseballgm.scouting.ScoutReport
import kotlin.math.roundToInt

/**
 * 우리 신인 (2026-10-03, 유저 요청 "드래프트 직후, 뽑은 신인들의 능력치 확인할 수 있는 화면").
 *
 * 드래프트가 끝나면 자동으로 열린다. 지명한 신인은 계약금을 치른 **우리 선수**라서 현재 능력치를 정확히 보고,
 * 잠재력은 우리 선수처럼 등급으로 본다 (docs/10 "지명 이후"). 값은 전부 엔진 리포트(`ScoutReport`)를 거친다.
 *
 * 순서: 비서 한 줄(즉시 전력감·최고 재목) → 한눈 요약(인원·투타·계약금) → 신인 카드(능력치 막대 + 지명 전 예상과 비교).
 */
@Composable
fun DraftClassScreen(session: GameSession, onProspect: (PlayerId) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val tokens = AppTheme.tokens
    val rookies = session.myDraftClass()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
        contentPadding = PaddingValues(vertical = tokens.spacing.m),
    ) {
        item { SecretaryCard(briefing(rookies), title = "${session.league.season} 신인 드래프트") }
        if (rookies.isEmpty()) return@LazyColumn
        item { SummaryCard(rookies) }
        items(rookies, key = { it.first.overallPick }) { (selection, report) ->
            RookieCard(session, selection, report) { onProspect(selection.playerId) }
        }
    }
}

/** 비서 한 줄: 바로 쓸 만한 선수(현재 종합 최고)와 크게 될 재목(잠재력 최고) */
private fun briefing(rookies: List<Pair<DraftSelection, ScoutReport>>): String {
    if (rookies.isEmpty()) return "올해는 우리가 뽑은 신인이 없어요."
    // 우리 신인이라 종합은 정확한 값(low == high)이다
    val ready = rookies.maxBy { it.second.scouted.overall.low }
    val ceiling = rookies.maxBy { (_, report) ->
        report.scouted.potentialHigh.ordinal * 1000 + report.scouted.potentialLow.ordinal * 100 + report.scouted.overall.low
    }
    val readyName = ready.first.playerName
    val ceilingName = ceiling.first.playerName
    val head = "신인 ${rookies.size}명 계약 마쳤어요. 이제 우리 선수라 능력치가 정확히 보여요."
    return if (readyName == ceilingName) {
        "$head 눈에 띄는 건 $readyName 선수예요. 지금 종합 ${ready.second.scouted.overall.low}에 잠재력도 ${ready.second.scouted.potentialLabel}등급이에요."
    } else {
        "$head 당장 쓸 만한 건 $readyName(종합 ${ready.second.scouted.overall.low}), " +
            "크게 될 재목은 $ceilingName(잠재력 ${ceiling.second.scouted.potentialLabel})이에요."
    }
}

@Composable
private fun SummaryCard(rookies: List<Pair<DraftSelection, ScoutReport>>) {
    val pitchers = rookies.count { it.first.positionLabel.endsWith("P") }
    val highSchool = rookies.count { it.first.schoolTypeLabel == "고졸" }
    val bonus = rookies.sumOf { it.first.signingBonus }
    val average = rookies.map { it.second.scouted.overall.low }.average()
    SectionCard("한눈에") {
        StatRow("인원", "${rookies.size}명 · 투수 $pitchers / 타자 ${rookies.size - pitchers}")
        StatRow("출신", "고졸 $highSchool / 대졸 ${rookies.size - highSchool}")
        StatRow("평균 종합", "${(average * 10).roundToInt() / 10.0}")
        StatRow("계약금 합계", "${(bonus * 10).roundToInt() / 10.0}억")
        Text(
            "다음 시즌 개막에 2군으로 입단해요. 잠재력은 우리 선수여도 등급으로만 보여요.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.tokens.base.textMuted,
        )
    }
}

/**
 * 신인 한 명. 머리(지명 순번·정체·종합/잠재) → 능력치 막대(정확한 값) → 지명 전 예상과 비교 → 성장 타입·계약금.
 * 카드를 누르면 스카우트 리포트(상세)로 간다.
 */
@Composable
private fun RookieCard(session: GameSession, selection: DraftSelection, report: ScoutReport, onOpen: () -> Unit) {
    val tokens = AppTheme.tokens
    val scouted = report.scouted
    val before = session.preDraftReport(selection.playerId)?.scouted
    SectionCard("${selection.round}라운드 · 전체 ${selection.overallPick}순위") {
        Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PlayerIdentity(
                        session.tagOf(session.league.draftPool.byId(selection.playerId) ?: return@Column, report)
                            .copy(caption = "${scouted.age}세 · ${selection.schoolTypeLabel} ${report.school.orEmpty()}"),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${scouted.overall.low}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.grade.of(scouted.overall.center),
                    )
                    Text("종합", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
                }
            }
            Spacer(Modifier.height(tokens.spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Pill("잠재력 ${scouted.potentialLabel}", AppColors.good)
                Text(growthLabel(scouted.growthTypeGuess), style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
            }
            Spacer(Modifier.height(tokens.spacing.s))
            Attribute.entries.filter { it in scouted.ratings }.forEach { attribute ->
                val range = scouted.ratings.getValue(attribute)
                RatingBar(attribute.label, range.low, range.high)
            }
            if (before != null && !before.isExactView) {
                Spacer(Modifier.height(tokens.spacing.s))
                SectionDivider()
                Spacer(Modifier.height(tokens.spacing.s))
                ScoutingCheck(before.overall.low, before.overall.high, scouted.overall.low)
            }
            Spacer(Modifier.height(tokens.spacing.xs))
            Text(
                "계약금 ${selection.signingBonus}억 · 누르면 스카우트 리포트",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.base.textMuted,
            )
        }
    }
}

/** 지명 전 우리 스카우트 예상(범위)과 실제 종합 — 스카우트가 얼마나 맞혔나 */
@Composable
private fun ScoutingCheck(low: Int, high: Int, actual: Int) {
    val (label, color) = when {
        actual > high -> "예상보다 좋아요" to AppColors.good
        actual < low -> "예상보다 아쉬워요" to AppColors.bad
        else -> "예상 범위 안" to AppColors.muted
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "지명 전 예상 $low~$high → 실제 $actual",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Pill(label, color)
    }
}

private fun growthLabel(type: GrowthType): String = when (type) {
    GrowthType.EARLY -> "조기 완성형"
    GrowthType.NORMAL -> "일반형"
    GrowthType.LATE -> "대기만성형"
}

package baseballgm.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.Pill
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.model.PlayerId
import baseballgm.scouting.DigestMove
import baseballgm.scouting.DigestPick
import baseballgm.scouting.ScoutDigest
import kotlin.math.roundToInt

/**
 * 스카우트팀 정기 리포트 (유저 요청 2026-10-04).
 *
 * 보고서는 **받은 시점 그대로**다. 숫자(남을 확률·종합 범위)는 그때 값이고, 화면은 "지금 지명됐는지"만 덧붙인다.
 * 단장은 보고를 읽고 판단한다 — 추천이 실시간으로 따라붙지 않게 하려는 형식이다.
 */
@Composable
fun ScoutDigestScreen(session: GameSession, index: Int, onProspect: (PlayerId) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val digests = session.scoutDigests()
    val tokens = AppTheme.tokens
    if (digests.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(tokens.spacing.l)) {
            Text(
                "아직 받은 리포트가 없어요." + (session.nextDigestWeek()?.let { " 첫 리포트는 ${it}주차에 와요." } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    var selected by rememberSaveable { mutableStateOf(if (index in digests.indices) index else digests.lastIndex) }
    val digest = digests[selected.coerceIn(digests.indices)]
    var showAll by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
        contentPadding = PaddingValues(vertical = tokens.spacing.m),
    ) {
        if (digests.size > 1) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                    digests.forEachIndexed { i, d ->
                        FilterChip(
                            selected = i == selected,
                            onClick = { selected = i },
                            label = { Text(if (d.final) "최종" else "${d.week}주차") },
                        )
                    }
                }
            }
        }
        item {
            SectionCard(if (digest.final) "${digest.season} 드래프트 최종 리포트" else "${digest.week}주차 스카우팅 리포트") {
                Text(digest.headline, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(tokens.spacing.xs))
                Text(
                    "기준: ${digest.policy.label} · ${digest.week}주차에 받은 보고라 지금과 다를 수 있어요. " +
                        "남을 확률은 다른 구단 선택을 가상으로 돌려 본 값이에요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                session.nextDigestWeek()?.let {
                    if (digest === digests.last()) {
                        Text(
                            "다음 리포트: ${it}주차",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            SectionCard("순번별로 노려볼 선수") {
                if (digest.rounds.isEmpty()) Text("남은 우리 지명권이 없었어요.", style = MaterialTheme.typography.bodyMedium)
                val shown = if (showAll) digest.rounds else digest.rounds.take(ROUNDS_SHOWN)
                shown.forEach { round ->
                    Spacer(Modifier.height(tokens.spacing.s))
                    Text("${round.round}R ${round.overallPick}순위", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (round.picks.isEmpty()) {
                        Text(
                            "이 순번까지 남을 만한 후보가 마땅치 않았어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    round.picks.forEach { pick -> PickLine(session, pick, onProspect) }
                }
                if (digest.rounds.size > ROUNDS_SHOWN) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "접기" else "나머지 ${digest.rounds.size - ROUNDS_SHOWN}개 순번 보기")
                    }
                }
            }
        }
        if (digest.risers.isNotEmpty() || digest.fallers.isNotEmpty()) {
            item {
                SectionCard("평가가 움직인 선수") {
                    Text(
                        "지난 보고보다 관찰이 쌓이며 평가가 크게 바뀐 선수예요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    (digest.risers + digest.fallers).forEach { move -> MoveLine(move, onProspect) }
                }
            }
        }
        if (digest.completed.isNotEmpty()) {
            item {
                SectionCard("관찰을 마친 선수") {
                    Text(
                        "더 봐도 정보가 늘지 않는 선수예요. 지금 리포트가 최선이에요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    digest.completed.forEach { done ->
                        Text(
                            "· ${done.position} ${done.name} · 잠재 ${done.potential}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable { onProspect(done.playerId) }.padding(vertical = tokens.spacing.xs),
                        )
                    }
                }
            }
        }
        item {
            SecretaryCard("리포트는 참고용이에요. 마지막 판단은 단장님 몫이고요.")
        }
    }
}

@Composable
private fun PickLine(session: GameSession, pick: DigestPick, onProspect: (PlayerId) -> Unit) {
    val tokens = AppTheme.tokens
    Row(
        Modifier.fillMaxWidth().clickable { onProspect(pick.playerId) }.padding(vertical = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${pick.position} ${pick.name} · 종합 ${pick.overall} · 잠재 ${pick.potential}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            if (pick.reasons.isNotEmpty()) {
                Text(pick.reasons.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(tokens.spacing.s))
        // 리포트 숫자는 그대로 두고, 그 뒤에 뽑혀 갔으면 그 사실만 덧붙인다
        val taken = session.prospectById(pick.playerId)?.let { session.selectionOf(it) }
        if (taken != null) {
            Pill("${session.league.team(taken.teamId).nickname} 지명", if (taken.teamId == session.userTeamId) AppColors.good else AppColors.muted)
        } else {
            Pill(
                "남을 확률 ${(pick.availability * PERCENT).roundToInt()}%",
                when {
                    pick.availability >= LIKELY -> AppColors.good
                    pick.availability >= TOSS_UP -> AppColors.warn
                    else -> AppColors.bad
                },
            )
        }
    }
}

@Composable
private fun MoveLine(move: DigestMove, onProspect: (PlayerId) -> Unit) {
    val up = move.delta > 0
    Row(
        Modifier.fillMaxWidth().clickable { onProspect(move.playerId) }.padding(vertical = AppTheme.tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${move.position} ${move.name} · 잠재 ${move.potential}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Pill(
            (if (up) "+" else "−") + "${kotlin.math.abs(move.delta).roundToInt().coerceAtLeast(1)}",
            if (up) AppColors.good else AppColors.bad,
        )
    }
}

private const val ROUNDS_SHOWN = 3
private const val PERCENT = 100

/** 확률 색 구분: 이 이상이면 "남을 듯"(초록), [TOSS_UP] 이상이면 "반반"(주황), 아래는 "어려움"(빨강) */
private const val LIKELY = 0.7
private const val TOSS_UP = 0.4

package baseballgm.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.PlayerLine
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.league.AwardEntry
import baseballgm.league.AwardKind
import baseballgm.model.PlayerId

/**
 * 시즌 시상식 (2026-10-01, 진단 3번).
 *
 * MVP·신인왕 → 골든글러브 → 부문 타이틀 순. 우리 팀 수상자는 강조하고, 리그에 남아 있는 선수는 누르면 상세로 간다.
 * 수상 근거(WAR·타율·홈런 수)를 같이 적는다 — 어떤 기준으로 뽑혔는지 보여야 납득이 된다 (SeasonAwards 참고).
 */
@Composable
fun AwardsScreen(session: GameSession, season: Int, onPlayer: (PlayerId) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val tokens = AppTheme.tokens
    val awards = session.awardsFor(season)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
    ) {
        if (awards.isEmpty()) {
            SecretaryCard("$season 시즌 시상은 정규시즌이 끝나야 정해져요.")
            return@Column
        }
        val ours = awards.filter { it.teamId == session.userTeamId }
        SecretaryCard(
            if (ours.isEmpty()) {
                "$season 시상식이에요. 올해는 우리 팀 수상자가 없어요. 내년엔 이 자리에 우리 선수 이름을 올려 봐요."
            } else {
                "$season 시상식이에요. 우리 팀에서 ${ours.size}개를 받았어요 — " +
                    ours.joinToString(", ") { "${it.name} ${label(it)}" } + "."
            },
            title = "$season 시상식",
        )

        SectionCard("MVP · 신인왕") {
            awards.filter { it.kind == AwardKind.MVP || it.kind == AwardKind.ROOKIE }.forEach { AwardRow(session, it, onPlayer, large = true) }
        }
        SectionCard("골든글러브") {
            awards.filter { it.kind == AwardKind.GOLDEN_GLOVE }.forEach { AwardRow(session, it, onPlayer) }
        }
        SectionCard("부문 타이틀") {
            awards.filter { it.kind.title }.forEach { AwardRow(session, it, onPlayer) }
        }
        Spacer(Modifier.height(tokens.spacing.s))
    }
}

private fun label(entry: AwardEntry): String = when (entry.kind) {
    AwardKind.GOLDEN_GLOVE -> "골든글러브(${positionName(entry.position)})"
    else -> entry.kind.label
}

private fun positionName(position: String?): String = when (position) {
    "C" -> "포수"; "1B" -> "1루수"; "2B" -> "2루수"; "3B" -> "3루수"; "SS" -> "유격수"
    "OF" -> "외야수"; "DH" -> "지명타자"; "P" -> "투수"; else -> position ?: ""
}

/**
 * 수상 한 줄: 상 이름 · 선수. 리그에 있는 선수는 다른 화면과 같은 정체 표시(누르면 상세, 2026-10-02 전 화면 통일),
 * 은퇴·떠난 선수는 이름 글자만. 우리 팀 수상자는 굵게(정체 표시는 이름이 늘 굵다) + 소속 글자로 구분된다.
 */
@Composable
private fun AwardRow(session: GameSession, entry: AwardEntry, onPlayer: (PlayerId) -> Unit, large: Boolean = false) {
    val tokens = AppTheme.tokens
    val ours = entry.teamId == session.userTeamId
    val inLeague = session.inLeague(entry.playerId)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (entry.kind == AwardKind.GOLDEN_GLOVE) positionName(entry.position) else entry.kind.label,
            style = if (large) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (large) FontWeight.Bold else FontWeight.Normal,
            color = tokens.base.textMuted,
            modifier = Modifier.width(88.dp),
        )
        val caption = "${session.league.team(entry.teamId).nickname}${if (ours) "(우리 팀)" else ""} · ${entry.value}"
        if (inLeague) {
            PlayerLine(
                session.tagOf(session.player(entry.playerId), caption = caption),
                onClick = { onPlayer(entry.playerId) },
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(Modifier.weight(1f).padding(vertical = tokens.spacing.xs)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (ours) FontWeight.Bold else FontWeight.Normal)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
            }
        }
    }
}

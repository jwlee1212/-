package baseballgm.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.SampleMode
import baseballgm.app.StatBoard
import baseballgm.app.StatFilter
import baseballgm.app.StatPosition
import baseballgm.app.StatScope
import baseballgm.app.StatView
import baseballgm.app.TeamView
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.TeamColors
import baseballgm.model.TeamId
import baseballgm.util.fixed

/**
 * 리그 탭 허브 (2026-10-04, 유저 요청 "상단 탭에서 고르는 게 아니라, 전체 컨테이너 안에 각 요소 컨테이너를 넣고 누르면 그 화면으로").
 *
 * 카드 하나 = 구역 하나(순위·타자·투수·팀 기록·뉴스·팬 반응·역대). 카드엔 **맛보기만** — 순위 상위 다섯, 리그 1~3위,
 * 우리 팀 한 줄, 최근 기사 둘 — 두고, 누르면 그 구역 전체 화면([LeagueSectionScreen])이 쌓인다.
 * 섹션이 넷을 넘지만 단장실처럼 허브라 절제 규칙의 예외로 둔다 (유저 결정). 대신 카드마다 숫자 몇 개 + 한 줄.
 */
@Composable
fun LeagueHubScreen(session: GameSession, onOpen: (LeagueSection) -> Unit) {
    val tokens = AppTheme.tokens
    val revision = session.revision
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
    ) {
        HubCard(LeagueSection.STANDINGS, "${session.record().games}경기", onOpen) { StandingsPreview(session) }
        HubCard(LeagueSection.BATTING, "OPS 리그 상위", onOpen) { BattingPreview(session, revision) }
        HubCard(LeagueSection.PITCHING, "평균자책 리그 상위", onOpen) { PitchingPreview(session, revision) }
        HubCard(LeagueSection.TEAMS, "열 팀 비교", onOpen) { TeamPreview(session, revision) }
        HubCard(LeagueSection.NEWS, null, onOpen) { NewsPreview(session) }
        HubCard(LeagueSection.FANS, null, onOpen) { FanPreview(session) }
        HubCard(LeagueSection.HISTORY, null, onOpen) { HistoryPreview(session) }
        Spacer(Modifier.height(tokens.spacing.s))
    }
}

/** 허브 카드: 제목 + 보조 한마디 + 오른쪽 화살표. 카드 어디를 눌러도 그 구역으로 */
@Composable
private fun HubCard(
    section: LeagueSection,
    caption: String?,
    onOpen: (LeagueSection) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = AppTheme.tokens
    SectionCard(
        title = section.label,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(tokens.radii.card))
            .clickable(role = Role.Button) { onOpen(section) }
            .semantics { contentDescription = "${section.label} 전체 보기" },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.base.textMuted)
            }
        },
        content = content,
    )
}

/** 구단 색 점 (글자에 구단 색을 쓰지 않는다) */
@Composable
private fun TeamDot(teamId: TeamId) {
    val tokens = AppTheme.tokens
    Box(Modifier.size(tokens.sizes.statusIcon / 2).clip(CircleShape).background(TeamColors.of(teamId)))
    Spacer(Modifier.width(tokens.spacing.xs))
}

/** 맛보기 한 줄: 순위 · 점 · 이름 · (보조) · 값. 우리 팀·우리 선수는 굵게 */
@Composable
private fun PreviewRow(rank: Int?, teamId: TeamId, name: String, detail: String?, value: String, ours: Boolean) {
    val tokens = AppTheme.tokens
    Row(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(rank?.toString() ?: "", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, modifier = Modifier.width(24.dp))
        TeamDot(teamId)
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.base.text,
            fontWeight = if (ours) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, maxLines = 1)
            Spacer(Modifier.width(tokens.spacing.m))
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.base.text,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = AppTheme.tokens.base.textMuted)
}

/** 순위 상위 다섯 + (밖이면) 우리 팀 */
@Composable
private fun StandingsPreview(session: GameSession) {
    val ranked = session.teamsRanked()
    val ours = ranked.indexOfFirst { it.teamId == session.userTeamId }
    val shown = ranked.take(STANDINGS_PREVIEW).withIndex().toMutableList()
    if (ours >= STANDINGS_PREVIEW) shown += IndexedValue(ours, ranked[ours])
    shown.forEach { (index, record) ->
        val behind = session.gamesBehind(record.teamId)
        PreviewRow(
            rank = index + 1,
            teamId = record.teamId,
            name = session.league.team(record.teamId).name,
            detail = "${record.wins}승 ${record.losses}패" + if (index > 0) " · ${behind.fixed(1)}" else "",
            value = record.winPct.fixed(3),
            ours = record.teamId == session.userTeamId,
        )
    }
}

@Composable
private fun BattingPreview(session: GameSession, revision: Int) {
    val ops = StatBoard.defaultBattingSort(StatView.BASIC)
    val (top, ourBest) = remember(revision) {
        StatBoard.battingTable(session, StatScope.LEAGUE, StatView.BASIC, ops, true).take(LEADERS_PREVIEW) to
            StatBoard.battingTable(
                session, StatScope.TEAM, StatView.BASIC, ops, true,
                StatFilter(StatPosition.ALL_BATTERS, SampleMode.HALF),
            ).firstOrNull()
    }
    if (top.isEmpty()) {
        Empty("규정 타석을 채운 타자가 아직 없어요.")
        return
    }
    top.forEachIndexed { index, row ->
        PreviewRow(index + 1, row.player.teamId!!, row.player.registeredName, null, row.line.ops.fixed(3), row.player.teamId == session.userTeamId)
    }
    ourBest?.let { row ->
        OursLine("우리 팀 1위 ${row.player.registeredName} OPS ${row.line.ops.fixed(3)} · 홈런 ${row.line.homeRuns}")
    }
}

@Composable
private fun PitchingPreview(session: GameSession, revision: Int) {
    val era = StatBoard.defaultPitchingSort(StatView.BASIC)
    val (top, ourBest) = remember(revision) {
        StatBoard.pitchingTable(session, StatScope.LEAGUE, StatView.BASIC, era, false).take(LEADERS_PREVIEW) to
            StatBoard.pitchingTable(
                session, StatScope.TEAM, StatView.BASIC, era, false,
                StatFilter(StatPosition.STARTER, SampleMode.HALF),
            ).firstOrNull()
    }
    if (top.isEmpty()) {
        Empty("규정 이닝을 채운 투수가 아직 없어요.")
        return
    }
    top.forEachIndexed { index, row ->
        PreviewRow(index + 1, row.player.teamId!!, row.player.registeredName, null, row.line.era.fixed(2), row.player.teamId == session.userTeamId)
    }
    ourBest?.let { row ->
        OursLine("우리 선발 1위 ${row.player.registeredName} 평균자책 ${row.line.era.fixed(2)} · ${row.line.wins}승")
    }
}

/** 우리 팀 한 줄 (보조 글자, 위 순위와 구분선) */
@Composable
private fun OursLine(text: String) {
    baseballgm.app.ui.SectionDivider()
    Text(text, style = MaterialTheme.typography.bodySmall, color = AppTheme.tokens.base.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 우리 팀 팀 기록 넷: 값 + 리그 순위 */
@Composable
private fun TeamPreview(session: GameSession, revision: Int) {
    val tiles = remember(revision) {
        listOf(
            Triple(TeamView.BATTING, "타율", "팀 타율"),
            Triple(TeamView.BATTING, "OPS", "팀 OPS"),
            Triple(TeamView.PITCHING, "평균자책", "팀 평균자책"),
            Triple(TeamView.PITCHING, "실점", "실점"),
        ).mapNotNull { (view, header, label) ->
            StatBoard.teamRankOf(session, view, header)?.let { (value, rank) -> Triple(label, value, rank) }
        }
    }
    if (tiles.isEmpty()) {
        Empty("아직 경기가 없어요.")
        return
    }
    val tokens = AppTheme.tokens
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
        tiles.forEach { (label, value, rank) ->
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, maxLines = 1)
                Text(value, style = MaterialTheme.typography.titleMedium, color = tokens.base.text, maxLines = 1)
                Text("리그 ${rank}위", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NewsPreview(session: GameSession) {
    val items = session.news().take(NEWS_PREVIEW)
    if (items.isEmpty()) {
        Empty("한 주가 지나면 리그 소식이 쌓여요.")
        return
    }
    val tokens = AppTheme.tokens
    items.forEachIndexed { index, item ->
        if (index > 0) Spacer(Modifier.height(tokens.spacing.s))
        Text("${item.week}주차 · ${item.kind.label}", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
        Text(
            item.headline,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (session.userTeamId in item.teams) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FanPreview(session: GameSession) {
    val tokens = AppTheme.tokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("팬심", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
        Spacer(Modifier.width(tokens.spacing.s))
        Text("${session.fanSupport()}", style = MaterialTheme.typography.titleMedium, color = tokens.base.text)
        Spacer(Modifier.width(tokens.spacing.s))
        Text(session.fanLabel(), style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
    }
    session.fanPosts().firstOrNull()?.let { post ->
        Spacer(Modifier.height(tokens.spacing.s))
        Text("“${post.text}”", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${post.author} · ${post.week}주차", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
    }
}

@Composable
private fun HistoryPreview(session: GameSession) {
    val last = session.history().seasons.lastOrNull()
    if (last == null) {
        Empty("첫 시즌이 끝나면 역대 우승팀과 수상자, 통산 기록이 쌓여요.")
        return
    }
    val mvp = last.awards.firstOrNull { it.kind == baseballgm.league.AwardKind.MVP }
    val tokens = AppTheme.tokens
    Text(
        "${last.season} " + (last.champion?.let { "우승 ${session.league.team(it).name}" } ?: "포스트시즌 없음"),
        style = MaterialTheme.typography.bodyMedium,
        color = tokens.base.text,
    )
    mvp?.let { Text("MVP ${it.name}", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted) }
    Text("지난 ${session.history().seasons.size}시즌", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
}

private const val STANDINGS_PREVIEW = 5
private const val LEADERS_PREVIEW = 3
private const val NEWS_PREVIEW = 2

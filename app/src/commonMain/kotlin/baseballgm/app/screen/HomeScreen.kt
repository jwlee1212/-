package baseballgm.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.Pill
import baseballgm.app.ui.SectionCard
import baseballgm.season.InboxCategory

/**
 * 홈 화면 (docs/15 M10 표).
 *
 * 주 시작 결정 단계에 해당한다 — 알림함을 보고, 순위를 확인하고, 진행 버튼을 누른다.
 * **알림이 없으면 바로 진행할 수 있어야 한다** (docs/07)는 원칙대로 버튼을 맨 위에 둔다.
 */
@Composable
fun HomeScreen(session: GameSession) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val record = session.record()
    val report = session.lastReport

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 팀 헤더
        Column {
            Text(session.userTeam.name, style = MaterialTheme.typography.titleLarge)
            Text(
                if (session.seasonOver) "정규시즌 종료" else session.calendarLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("성적") {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${record.wins}승 ${record.losses}패${if (record.ties > 0) " ${record.ties}무" else ""}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${session.rank()}위",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (session.rank() <= 5) AppColors.good else AppColors.warn,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("승률 ${"%.3f".format(record.winPct)}")
                Pill("게임차 ${"%.1f".format(session.gamesBehind())}")
                Pill(record.streakText(), if (record.streak >= 0) AppColors.good else AppColors.bad)
                Pill("득실 ${if (record.runDifferential >= 0) "+" else ""}${record.runDifferential}")
            }
        }

        // 시즌이 끝나면 스토브리그로 넘어간다 (docs/09)
        if (session.seasonOver) {
            SectionCard("정규시즌 종료") {
                val record = session.record()
                Text(
                    "${session.league.season} 시즌 ${session.rank()}위 · ${record.wins}승 ${record.losses}패" +
                        if (record.ties > 0) " ${record.ties}무" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (session.rank() <= 5) "포스트시즌 진출권입니다 (포스트시즌은 아직 구현 전)" else "다음 시즌을 준비합니다",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { session.startNextSeason() },
                    enabled = !session.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("스토브리그 진행 → ${session.league.season + 1} 시즌") }
            }
        }

        // 지난 스토브리그 결과
        session.lastOffseason?.let { offseason ->
            SectionCard("${offseason.season} 스토브리그 결과") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("은퇴 ${offseason.retired.size}", AppColors.bad)
                    Pill("신인 ${offseason.rookies.size}", AppColors.good)
                    Pill("각성 ${offseason.awakened.size}", AppColors.good)
                    Pill("급노쇠 ${offseason.collapsed.size}", AppColors.warn)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("입대 ${offseason.enlisted.size}")
                    Pill("제대 ${offseason.discharged.size}")
                    Pill("코치 전향 ${offseason.newCoachCandidates.size}")
                }
            }
        }

        // 진행
        if (!session.seasonOver) {
            SectionCard("진행") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { session.advanceWeek() },
                        enabled = !session.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("이번 주 진행") }
                    OutlinedButton(
                        onClick = { session.advanceUntil(session.state.calendar.allStarBreakAfterWeek) },
                        enabled = !session.busy && session.week <= session.state.calendar.allStarBreakAfterWeek,
                        modifier = Modifier.weight(1f),
                    ) { Text("올스타까지") }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { session.advanceUntil(session.state.calendar.regularSeasonWeeks) },
                    enabled = !session.busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(),
                ) { Text("시즌 끝까지 자동 진행") }
            }
        }

        // 알림함
        val messages = report?.messages?.filter { it.teamId == null || it.teamId == session.userTeamId }.orEmpty()
        SectionCard("알림함", trailing = { Text("${messages.size}건", style = MaterialTheme.typography.labelSmall) }) {
            if (messages.isEmpty()) {
                Text(
                    "새 소식이 없습니다. 바로 진행하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                messages.take(8).forEach { message ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Pill(
                            message.category.label,
                            when (message.category) {
                                InboxCategory.INJURY -> AppColors.bad
                                InboxCategory.RETURN, InboxCategory.ROSTER -> AppColors.good
                                InboxCategory.STREAK -> AppColors.warn
                                else -> AppColors.muted
                            },
                        )
                        Spacer(Modifier.fillMaxWidth(0.02f))
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }

        // 지난 주 하이라이트
        if (report != null && report.highlights.isNotEmpty()) {
            SectionCard("지난 주 주요 장면") {
                report.highlights.forEach {
                    Text("· $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        // 이번 주 일정
        if (!session.seasonOver) {
            SectionCard("이번 주 일정") {
                session.weekSchedule().forEach { game ->
                    val home = game.home == session.userTeamId
                    val opponent = session.league.team(game.opponentOf(session.userTeamId))
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            DAY_NAMES.getOrElse(game.day) { "?" },
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.muted,
                            modifier = Modifier.fillMaxWidth(0.12f),
                        )
                        Text(
                            "${if (home) "홈" else "원정"}  ${opponent.name}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // 순위 요약
        SectionCard("순위") {
            session.teamsRanked().take(6).forEachIndexed { index, team ->
                val isUser = team.teamId == session.userTeamId
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (index < 5) AppColors.good else AppColors.muted,
                        modifier = Modifier.fillMaxWidth(0.08f),
                    )
                    Text(
                        session.league.team(team.teamId).name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isUser) FontWeight.Bold else FontWeight.Normal,
                        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(0.45f),
                    )
                    Text(
                        "${team.wins}-${team.losses}${if (team.ties > 0) "-${team.ties}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(0.4f),
                    )
                    Text("%.3f".format(team.winPct), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private val DAY_NAMES = listOf("화", "수", "목", "금", "토", "일")

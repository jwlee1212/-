package baseballgm.app.screen

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.Briefing
import baseballgm.app.GameSession
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.Pill
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.SectionDivider
import baseballgm.app.ui.NavRow
import baseballgm.season.InboxCategory
import baseballgm.season.InboxMessage

/**
 * 주간 브리핑 (docs/07, docs/16 §4).
 *
 * 2026-10-01 절제 작업으로 섹션을 넷으로 줄였다.
 * 1. **이번 주 전적 — 주인공.** 비서 한 줄("8주차는 2승 4패…", 가장 큰 글자) 아래 경기 결과·주요 장면·결정의 메아리
 * 2. 결정 성적표 — 최근 결정이 그 뒤로 어떻게 됐는지 (2026-10-02. 예전엔 "이번 주 내린 결정" 목록)
 * 3. 소식
 * 4. 더 보기 — 리그 기사·팬 반응·문자 중계로 가는 줄
 * 하단 진행 버튼이 이 화면에서는 "승인하고 N주차 진행" 으로 바뀐다.
 */
@Composable
fun WeeklyReportScreen(session: GameSession, onWatch: () -> Unit, onOpenNews: (tab: Int) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val report = session.lastReport
    val tokens = AppTheme.tokens
    val spacing = tokens.spacing

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.l, vertical = spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        if (report == null) {
            SecretaryCard("아직 브리핑할 주가 없어요. 한 주를 진행하시면 여기 정리해 둘게요.")
            return@Column
        }

        val mine = report.games.filter {
            it.home.teamId == session.userTeamId || it.away.teamId == session.userTeamId
        }

        // 1. 주인공: 이번 주 전적. 전적·순위는 비서 문장 안에 있으니 숫자를 따로 크게 되풀이하지 않는다
        SecretaryCard(Briefing.weeklySummary(session, report), title = "${report.week}주차 결과", hero = true) {
            mine.forEach { game ->
                val home = game.home.teamId == session.userTeamId
                val opponent = session.league.team(if (home) game.away.teamId else game.home.teamId)
                val ours = if (home) game.homeScore else game.awayScore
                val theirs = if (home) game.awayScore else game.homeScore
                val (mark, color) = when {
                    game.tie -> "무" to AppColors.muted
                    ours > theirs -> "승" to AppColors.good
                    else -> "패" to AppColors.bad
                }
                Row(Modifier.fillMaxWidth().padding(vertical = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    // 승패는 칩이 아니라 색 글자 하나 (색은 점에만)
                    Text(mark, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(spacing.xl))
                    Text(
                        "${if (home) "홈" else "원정"} ${opponent.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$ours : $theirs" + if (game.innings > 9) " (${game.innings}회)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (report.allStarBreak) {
                Text("이번 주를 끝으로 올스타 브레이크예요.", style = MaterialTheme.typography.bodySmall, color = AppColors.warn, modifier = Modifier.padding(top = spacing.s))
            }
            if (report.highlights.isNotEmpty() || report.echoes.isNotEmpty()) {
                SectionDivider()
                // 주요 장면과 과거 결정의 메아리(docs/13)는 회색 문장으로
                (report.highlights + report.echoes).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.base.textSecondary, modifier = Modifier.padding(vertical = spacing.xs))
                }
            }
        }

        // 2. 결정 성적표 (재미 개선 2번, 2026-10-02): 최근 몇 주의 결정이 그 뒤로 어떻게 됐는지.
        //    결정한 순간의 기준값과 지금을 비교한다. 이번 주 결정은 대개 "지켜보는 중"이고 주가 지날수록 판정이 난다
        val reviews = session.decisionReviewer.recent(session)
        if (reviews.isNotEmpty()) {
            SectionCard("결정 성적표") {
                reviews.forEachIndexed { index, review ->
                    if (index > 0) SectionDivider()
                    DecisionReviewRow(review)
                }
            }
        } else if (report.incidents.isNotEmpty()) {
            // 기준값이 없는 옛 기록(이 기능 전 세이브)은 예전처럼 고른 답만
            SectionCard("이번 주 내린 결정") {
                report.incidents.forEach { record ->
                    Column(Modifier.fillMaxWidth().padding(vertical = spacing.xs)) {
                        Text(record.headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${record.kind.label} · ${record.choice}" + if (record.delegated) " (비서 처리)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.base.textMuted,
                        )
                    }
                }
            }
        }

        // 3. 소식. "결정" 소식은 바로 위 "이번 주 내린 결정" 카드와 같은 내용이라 뺀다
        val messages = report.messages.filter {
            (it.teamId == null || it.teamId == session.userTeamId) &&
                !(it.category == InboxCategory.DECISION && report.incidents.isNotEmpty())
        }
        SectionCard("소식") {
            if (messages.isEmpty()) {
                Text("특별한 소식은 없었어요.", style = MaterialTheme.typography.bodySmall, color = AppColors.muted)
            } else {
                messages.forEach { InboxLine(it) }
            }
        }

        // 4. 더 보기 — 기사·팬 반응·중계는 전체 화면에서
        if (report.news.isNotEmpty() || report.fanPosts.isNotEmpty() || report.watched.isNotEmpty()) {
            SectionCard("더 보기") {
                report.news.firstOrNull()?.let { NavRow("이번 주 리그 기사 ${report.news.size}건", it.headline, { onOpenNews(0) }) }
                report.fanPosts.maxByOrNull { it.likes }?.let { NavRow("팬들 반응", "“${it.text}”", { onOpenNews(1) }) }
                if (report.watched.isNotEmpty()) NavRow("우리 팀 경기 문자 중계", null, onWatch)
            }
        }
        Spacer(Modifier.height(spacing.s))
    }
}

/** 알림 한 줄. 홈 요약과 결산 보고서가 같이 쓴다 */
@Composable
internal fun InboxLine(message: InboxMessage) {
    Row(Modifier.fillMaxWidth().padding(vertical = AppTheme.tokens.spacing.xs)) {
        Pill(
            message.category.label,
            when (message.category) {
                InboxCategory.INJURY -> AppColors.bad
                InboxCategory.RETURN, InboxCategory.ROSTER -> AppColors.good
                InboxCategory.STREAK -> AppColors.warn
                else -> AppColors.muted
            },
        )
        Text(
            message.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = AppTheme.tokens.spacing.s),
        )
    }
}


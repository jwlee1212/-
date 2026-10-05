package baseballgm.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.Pill
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.SectionDivider
import baseballgm.app.ui.StatRow
import baseballgm.app.ui.TeamEmblem
import baseballgm.management.CareerSeason
import baseballgm.management.GmDecision

/**
 * 단장 커리어 — 연대기(스크랩북) (docs/13, docs/16 §커리어).
 *
 * 세이브의 중심은 구단이 아니라 **단장 개인**이다. 해임되어도 이어지므로 이 화면이 유저의 진짜 성적표다.
 * 맨 위에 이름과 대표 칭호, 그 아래로 시즌이 한 장씩 쌓인다 — 올해가 맨 위, 첫해가 맨 아래.
 * 각 시즌 카드에는 그해의 성적과 **그해 내린 결정**(지명·트레이드·영입)이 같이 붙는다.
 */
@Composable
fun CareerScreen(session: GameSession) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val career = session.career()
    val decisions = session.decisions().groupBy { it.season }
    val spacing = AppTheme.tokens.spacing

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
        contentPadding = PaddingValues(spacing.m),
    ) {
        if (session.unemployed) {
            item {
                SecretaryCard(
                    if (session.jobOffers.isEmpty()) {
                        "들어온 제안이 없어요. 한 해는 해설위원으로 쉬어 가면서 기회를 기다려 봐요."
                    } else {
                        "${session.gmName} 단장님, 제안이 ${session.jobOffers.size}곳에서 왔어요. 고르시면 그 구단에서 새 시즌을 시작해요."
                    },
                )
            }
            items(session.jobOffers) { offer ->
                SectionCard(offer.teamName, trailing = { OutlinedButton(onClick = { session.acceptOffer(offer) }) { Text("수락") } }) {
                    Text(
                        "목표 '${offer.expectation}' · 구단주 신뢰도 ${offer.ownerTrust}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 표지: 이름 + 대표 칭호(이 화면의 주인공) + 평판 + 라이벌. 라이벌은 따로 카드였는데 표지에 합쳤다
        item {
            val title = session.gmTitle()
            SectionCard("${session.gmName} 단장") {
                Text(title.name, style = MaterialTheme.typography.headlineSmall, color = AppTheme.tokens.base.brand)
                Text(title.description, style = MaterialTheme.typography.bodySmall, color = AppColors.muted)
                val earned = session.gmTitles()
                if (earned.size > 1) {
                    // 다른 칭호는 칩 줄이 아니라 회색 글자 한 줄 (칩은 상태에만)
                    Text("다른 칭호 · ${earned.drop(1).joinToString(" · ") { it.name }}", style = MaterialTheme.typography.bodySmall, color = AppColors.muted)
                }
                SectionDivider()
                val reputation = career?.reputation ?: 0
                StatRow("평판", "$reputation (${session.careerLabel()})")
                // 평판은 능력치가 아니라 등급 색 대신 브랜드 색
                MeterBar(reputation, AppTheme.tokens.base.brand)
                Spacer(Modifier.height(spacing.s))
                Text(career?.summary() ?: "아직 끝난 시즌이 없어요", style = MaterialTheme.typography.bodyMedium)
                StatRow("명예의 전당 평가", session.hallOfFameGrade())
                session.rival()?.let { rival ->
                    val (wins, losses) = session.rivalRecord() ?: (0 to 0)
                    SectionDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamEmblem(rival.id, rival.nickname.take(1), size = 28.dp)
                        Spacer(Modifier.width(spacing.s))
                        Column(Modifier.weight(1f)) {
                            Text("라이벌 ${rival.name}", style = MaterialTheme.typography.bodyMedium)
                            Text("올 시즌 상대 전적", style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
                        }
                        Text(
                            "${wins}승 ${losses}패",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                wins > losses -> AppColors.good
                                wins < losses -> AppColors.bad
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }

        // 연대기: 올해(진행 중) → 지난 시즌들
        item { ChronicleHeader("연대기") }
        if (!session.unemployed) {
            item {
                val record = session.record()
                ChroniclePage(
                    season = session.league.season,
                    teamName = session.userTeam.name,
                    emblem = { TeamEmblem(session.userTeamId, session.userTeam.nickname.take(1), size = 28.dp) },
                    headline = "진행 중 · ${session.rank()}위 ${record.wins}승 ${record.losses}패",
                    badges = emptyList(),
                    decisions = decisions[session.league.season].orEmpty(),
                )
            }
        }
        items(career?.seasons.orEmpty().reversed(), key = { it.season }) { past ->
            ChroniclePage(
                season = past.season,
                teamName = if (past.commentary) "해설위원" else past.teamName,
                emblem = past.teamId?.let { id ->
                    { TeamEmblem(id, session.league.team(id).nickname.take(1), size = 28.dp) }
                },
                headline = headlineOf(past),
                badges = badgesOf(past),
                decisions = decisions[past.season].orEmpty(),
            )
        }

        // 업적
        item {
            SectionCard("업적 ${session.unlockedAchievements().size}/${session.achievements().size}") {
                session.achievements().forEach { achievement ->
                    val unlocked = achievement.id in session.unlockedAchievements()
                    Row(Modifier.fillMaxWidth().padding(vertical = AppTheme.tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                achievement.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Normal,
                                color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                achievement.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (unlocked) Pill("달성", AppColors.good)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronicleHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = AppTheme.tokens.spacing.s),
    )
}

/**
 * 연대기 한 장. 왼쪽에 시즌 점과 세로줄(타임라인), 오른쪽에 그해 카드.
 */
@Composable
private fun ChroniclePage(
    season: Int,
    teamName: String,
    emblem: (@Composable () -> Unit)?,
    headline: String,
    badges: List<Pair<String, androidx.compose.ui.graphics.Color>>,
    decisions: List<GmDecision>,
) {
    val tokens = AppTheme.tokens
    Row {
        // 타임라인 점 + 연도
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tokens.base.brand))
            Spacer(Modifier.height(AppTheme.tokens.spacing.xs))
            Text("$season", style = MaterialTheme.typography.labelMedium, color = tokens.base.textSecondary)
        }
        SectionCard(
            teamName,
            modifier = Modifier.weight(1f),
            trailing = emblem,
        ) {
            Text(headline, style = MaterialTheme.typography.bodyMedium)
            if (badges.isNotEmpty()) {
                Row(Modifier.padding(top = tokens.spacing.xs), horizontalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.s)) {
                    badges.forEach { (label, color) -> Pill(label, color) }
                }
            }
            if (decisions.isNotEmpty()) {
                Spacer(Modifier.height(tokens.spacing.s))
                Text("그해의 결정", style = MaterialTheme.typography.labelMedium, color = tokens.base.textMuted)
                decisions.sortedBy { it.week }.take(MAX_DECISIONS_PER_PAGE).forEach { decision ->
                    Text(
                        "· ${decision.kind.label} — ${decision.playerName}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = AppTheme.tokens.spacing.xs),
                    )
                }
                if (decisions.size > MAX_DECISIONS_PER_PAGE) {
                    Text(
                        "외 ${decisions.size - MAX_DECISIONS_PER_PAGE}건",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.base.textMuted,
                    )
                }
            }
        }
    }
}

private fun headlineOf(season: CareerSeason): String = when {
    season.commentary -> "현장을 떠나 중계석에서 한 해를 보냈다"
    else -> "${season.rank}위 · ${season.wins}승 ${season.losses}패" + if (season.ties > 0) " ${season.ties}무" else ""
}

@Composable
private fun badgesOf(season: CareerSeason): List<Pair<String, androidx.compose.ui.graphics.Color>> = buildList {
    if (season.champion) add("우승" to AppColors.good)
    else season.reachedRound?.let { add(it.label to AppTheme.tokens.base.brand) }
    season.goalOutcome?.let { add("목표 ${it.label}" to if (it.name.startsWith("MISS") || it.name.startsWith("FAR")) AppColors.bad else AppColors.good) }
}

private const val MAX_DECISIONS_PER_PAGE = 6

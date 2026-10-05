package baseballgm.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.NavRow
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.SectionDivider
import baseballgm.model.RosterLevel
import baseballgm.season.PostseasonProgress
import baseballgm.season.SeriesProgress
import baseballgm.tactics.WeeklyPolicy
import baseballgm.util.eulReul
import baseballgm.util.ieyo

/**
 * 포스트시즌 진행 카드 (2026-10-03, docs/14·16).
 *
 * 단장 전용 모드라 라인업·선발 순서는 감독이 정한다. 단장이 정하는 건 셋이다:
 * ① 우리 팀 방침(총력전·정상·보호) — 경기마다 바꿀 수 있다
 * ② 다음 경기 휴식 지시 — 그 경기를 치르면 풀린다
 * ③ 엔트리 — **우리 시리즈를 시작하기 전까지만** (로스터 탭에서)
 *
 * 비서가 다음 상대·시리즈 전적·감독 구상(선발)을 브리핑하고, 결정은 진행 버튼(N차전 진행)으로 승인한다.
 */
@Composable
internal fun PostseasonLiveCard(session: GameSession, actions: HomeActions) {
    val tokens = AppTheme.tokens
    val progress = session.postseasonProgress
    if (progress == null) {
        val rank = session.rank()
        SecretaryCard(
            if (rank <= session.postseasonSpots) {
                "${rank}위로 가을야구에 나가요. 시작하면 대진이 나오고, 우리 시리즈 전까지 엔트리를 정할 수 있어요."
            } else {
                "올해 가을야구는 없어요. 다른 팀 결과는 진행하면 볼 수 있어요."
            },
            title = "포스트시즌",
        )
        return
    }
    val user = session.userTeamId
    val alive = progress.isAlive(user)
    val current = progress.current

    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.m)) {
        SecretaryCard(briefingLine(session, progress), title = "${progress.season} 포스트시즌")

        if (alive && current != null) {
            SectionCard("단장 결정") {
                Text("우리 팀 방침", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(tokens.spacing.xs))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    POLICY_ORDER.forEachIndexed { index, policy ->
                        SegmentedButton(
                            selected = session.postseasonPolicy() == policy,
                            onClick = { session.setPostseasonPolicy(policy) },
                            shape = SegmentedButtonDefaults.itemShape(index, POLICY_ORDER.size),
                        ) { Text(policy.label) }
                    }
                }
                Text(
                    when (session.postseasonPolicy()) {
                        WeeklyPolicy.ALL_OUT -> "감독 기본값이에요. 필승조를 넓게 쓰고 선발을 길게 끌어요. 시리즈가 길어지면 불펜이 지쳐요."
                        WeeklyPolicy.NORMAL -> "정규시즌처럼 운영해요. 다음 시리즈까지 내다볼 때 좋아요."
                        WeeklyPolicy.PROTECT -> "제한을 조여서 주전을 아껴요. 이번 경기보다 다음 경기가 중요할 때요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.base.textMuted,
                )

                SectionDivider()
                RestSection(session)

                SectionDivider()
                if (progress.entryOpenFor(user)) {
                    NavRow(
                        "엔트리 정하기",
                        "${current.takeIf { it.involves(user) }?.round?.label ?: "우리 시리즈"} 시작 전까지 바꿀 수 있어요 · 재등록 제한 없음",
                        actions.openSquad,
                    )
                } else {
                    Text(
                        "시리즈 중이라 엔트리는 잠겨 있어요. 이 시리즈가 끝나면 다시 열려요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.base.textMuted,
                    )
                }
            }
        }

        SectionCard("대진") {
            progress.completed.forEach { series ->
                val ours = series.higherSeed == user || series.lowerSeed == user
                Text(
                    series.line { session.league.team(it).nickname },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (ours) FontWeight.Bold else FontWeight.Normal,
                    color = if (ours) tokens.base.text else tokens.base.textSecondary,
                )
            }
            current?.let { series ->
                Text(
                    "${series.round.label}: ${scoreLine(session, series)} (진행 중)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            lastUserGameIndex(session)?.let { index ->
                val game = session.postseasonGames[index]
                SectionDivider()
                NavRow(
                    "${game.round.label} ${game.gameNumber}차전 문자 중계",
                    gameLine(session, game),
                    { actions.openPostseasonGame(index) },
                )
            }
            if (session.postseasonGames.isNotEmpty()) {
                NavRow("포스트시즌 경기 다시 보기", "${session.postseasonGames.size}경기", actions.openPostseason)
            }
        }
    }
}

/** 비서 한마디: 우리 상황 → 상대 → 감독 구상 */
private fun briefingLine(session: GameSession, progress: PostseasonProgress): String {
    val user = session.userTeamId
    val current = progress.current ?: return "포스트시즌이 끝났어요."
    if (!progress.isAlive(user)) {
        val out = progress.completed.lastOrNull { it.loser == user }
        return if (out != null) {
            "${out.round.label}에서 멈췄어요. 아쉽지만 여기까지 온 것도 수확이에요. 남은 경기는 한 번에 볼 수 있어요."
        } else {
            "올해 가을야구는 없어요. 다른 팀 결과는 진행하면 볼 수 있어요."
        }
    }
    if (!current.involves(user)) {
        val wait = progress.nextRoundOf(user)?.label ?: "다음 시리즈"
        return "우리는 ${wait}에서 기다려요. 지금은 ${current.round.label} 중이에요. 기다리는 동안 엔트리를 정해 두시면 돼요."
    }
    val opponent = session.league.team(current.opponentOf(user))
    val (w, l) = session.headToHead(opponent.id)
    val starter = session.postseasonPlannedStarter()?.registeredName
    val status = when {
        current.gamesPlayed == 0 -> "${current.round.label} 상대는 ${opponent.name.ieyo()}. 시즌 상대 전적은 ${w}승 ${l}패였어요."
        else -> "${current.round.label} ${scoreLine(session, current)}."
    }
    val plan = starter?.let { " 감독은 ${current.gamesPlayed + 1}차전 선발로 ${it.eulReul()} 생각하고 있어요." } ?: ""
    return status + plan
}

/** "우리 2 - 1 상대" (우리 팀이 있으면 우리 쪽을 앞에) */
private fun scoreLine(session: GameSession, series: SeriesProgress): String {
    val user = session.userTeamId
    val (first, second) = if (series.lowerSeed == user) series.lowerSeed to series.higherSeed else series.higherSeed to series.lowerSeed
    val name = { id: baseballgm.model.TeamId -> session.league.team(id).nickname }
    return "${name(first)} ${series.winsOf(first)} - ${series.winsOf(second)} ${name(second)}" +
        if (series.ties > 0) " (무 ${series.ties})" else ""
}

private fun lastUserGameIndex(session: GameSession): Int? =
    session.postseasonGames.indexOfLast { it.box.home.teamId == session.userTeamId || it.box.away.teamId == session.userTeamId }
        .takeIf { it >= 0 && session.postseasonGames[it].events.isNotEmpty() }

private fun gameLine(session: GameSession, game: baseballgm.season.Postseason.PlayedGame): String {
    val box = game.box
    val user = session.userTeamId
    val ours = if (box.home.teamId == user) box.homeScore else box.awayScore
    val theirs = if (box.home.teamId == user) box.awayScore else box.homeScore
    val result = when {
        box.tie -> "무"
        ours > theirs -> "승"
        else -> "패"
    }
    return "$ours-$theirs $result" + (if (box.innings > 9) " · ${box.innings}회" else "") + if (box.walkOff) " · 끝내기" else ""
}

/** 지친 선수 휴식 지시: 1군에서 피로가 높은 순 */
@Composable
private fun RestSection(session: GameSession) {
    val tokens = AppTheme.tokens
    val tired = session.roster(RosterLevel.FIRST_TEAM)
        .filter { it.condition.fatigue >= REST_SUGGEST_FATIGUE || session.isPostseasonResting(it.id) }
        .sortedByDescending { it.condition.fatigue }
        .take(REST_SUGGEST_COUNT)
    Text("다음 경기 휴식", style = MaterialTheme.typography.labelLarge)
    if (tired.isEmpty()) {
        Text("눈에 띄게 지친 선수는 없어요.", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
        return
    }
    Text(
        "쉬게 하면 그 경기 명단에서 빠져요. 경기가 끝나면 지시는 풀려요.",
        style = MaterialTheme.typography.bodySmall,
        color = tokens.base.textMuted,
    )
    tired.forEach { player ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${player.registeredName} · 피로 ${player.condition.fatigue}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (player.condition.fatigue >= REST_SUGGEST_FATIGUE) AppColors.warn else tokens.base.text,
                modifier = Modifier.weight(1f),
            )
            val resting = session.isPostseasonResting(player.id)
            FilterChip(
                selected = resting,
                onClick = { session.togglePostseasonRest(player.id) },
                label = { Text(if (resting) "휴식" else "출전") },
            )
        }
    }
}

/** 세그먼트 순서: 감독 기본값(총력전)을 가운데가 아니라 맨 앞에 */
private val POLICY_ORDER = listOf(WeeklyPolicy.ALL_OUT, WeeklyPolicy.NORMAL, WeeklyPolicy.PROTECT)

/** 휴식 후보로 띄우는 피로 기준 — 홈 화면 피로 주의(FATIGUE_ALERT)와 같은 값 (표시용) */
private const val REST_SUGGEST_FATIGUE = 70
private const val REST_SUGGEST_COUNT = 5

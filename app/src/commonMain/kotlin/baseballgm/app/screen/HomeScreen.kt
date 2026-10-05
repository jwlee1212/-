package baseballgm.app.screen

import baseballgm.util.fixed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import baseballgm.app.Briefing
import baseballgm.app.BriefingTarget
import baseballgm.app.GameSession
import baseballgm.app.GoalProgress
import baseballgm.app.GoalStatus
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.FanAvatar
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.Pill
import baseballgm.app.ui.Secretary
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.SectionDivider
import baseballgm.app.ui.NavRow
import baseballgm.app.ui.EnterOnce
import baseballgm.app.ui.rememberCountUp
import baseballgm.app.Messages
import baseballgm.app.ui.TeamEmblem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign

/** 단장실에서 다른 화면으로 가는 길 */
class HomeActions(
    val openReport: () -> Unit,
    val openClub: () -> Unit,
    val openCareer: () -> Unit,
    val openDecision: (BriefingTarget) -> Unit,
    val openMessages: () -> Unit = {},
    val openSquad: () -> Unit = {},
    val openRecruit: () -> Unit = {},
    val openLeague: () -> Unit = {},
    val openPlayer: (baseballgm.model.PlayerId) -> Unit = {},
    val openCompare: (List<baseballgm.model.PlayerId>) -> Unit = {},
    val openPostseason: () -> Unit = {},
    /** 포스트시즌 경기 하나를 문자 중계로 (몇 번째 경기인가) */
    val openPostseasonGame: (Int) -> Unit = {},
    val openAwards: (season: Int) -> Unit = {},
)

/**
 * 단장실 = 허브 (docs/16 §3·4-1, 2026-10-03 화면 개편 — 더쇼 프랜차이즈 허브를 캐주얼하게).
 *
 * 위에서부터
 * 1. (첫 주만) 비서 첫 인사
 * 2. 팀 띠: 전적·순위·연승 + 가을야구권 칩
 * 3. 다음 경기 매치업 — **이 화면의 주인공**(시뮬 버튼과 함께): 요일·홈/원정·상대 · 예상 선발 둘 · 상대 전적.
 *    시즌이 끝났으면 시즌 단계 카드(FA·포스트시즌·시즌 종료)
 * 4. 메시지 미리보기: 비서 한 줄 + 답장 필요 수 → 메시지 탭
 * 5. 타일 넷: 선수단 · 영입 · 구단주 목표 · 리그 (숫자 하나 + 상태 한 줄)
 * 6. 이번 주 일정 띠 (치른 날은 승·패)
 * 7. (그 시기에만) 국제대회·스토브리그 결과
 *
 * 절제 규칙 "섹션 4개까지"의 예외다(유저 결정 2026-10-03). 대신 타일은 넷으로 고정하고 칸마다 숫자 하나 + 상태 한 줄.
 * 돌발 이벤트·결정할 것·주간 알림은 메시지 탭으로 갔다.
 */
@Composable
fun HomeScreen(
    session: GameSession,
    welcome: Boolean,
    onDismissWelcome: () -> Unit,
    actions: HomeActions,
) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision

    val spacing = AppTheme.tokens.spacing
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.l, vertical = spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        if (welcome) {
            SecretaryCard(Briefing.welcome(session), title = "첫 인사") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissWelcome) { Text("잘 부탁해요") }
                }
            }
        }
        TeamStrip(session)
        if (session.seasonOver || session.inFreeAgency) SeasonPhaseCard(session, actions) else NextGameCard(session)
        MessagePreview(session, actions.openMessages)
        Tiles(session, actions)
        if (!session.seasonOver && !session.inFreeAgency) WeekStrip(session)
        SeasonNotes(session)
        Spacer(Modifier.height(spacing.s))
    }
}

/** 팀 띠: 전적(지난주 값에서 이어짐) · 승률 · 연승/연패 · 가을야구까지 거리 */
@Composable
private fun TeamStrip(session: GameSession) {
    val tokens = AppTheme.tokens
    val record = session.record()
    val wins = rememberCountUp(record.wins, "home-wins")
    val losses = rememberCountUp(record.losses, "home-losses")
    val pct = rememberCountUp(record.winPct.toFloat(), "home-pct")
    val rank = session.rank()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${wins}승 ${losses}패${if (record.ties > 0) " ${record.ties}무" else ""} · ${rank}위",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.base.text,
            )
            Text(
                // 연승·연패가 없으면(시즌 첫 경기 전 등) 그 칸은 뺀다
                listOfNotNull("승률 ${pct.toDouble().fixed(3)}", record.streakText().takeIf { record.streak != 0 }, postseasonLine(session).ifBlank { null })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textMuted,
            )
        }
        if (rank <= POSTSEASON_SPOTS) Pill("가을야구권", AppColors.good) else Pill("가을야구 밖", AppColors.warn)
    }
}

/**
 * 다음 경기 매치업 (더쇼 허브의 주인공). 예상 선발은 로테이션 다음 순번이다 — 감독이 매주 로테이션을 다시 짜므로 "예상".
 * 상대 선발도 공개 기록(승·평균자책)만 쓴다.
 */
@Composable
private fun NextGameCard(session: GameSession) {
    val tokens = AppTheme.tokens
    val game = session.nextGame() ?: return
    val home = game.home == session.userTeamId
    val opponentId = if (home) game.away else game.home
    val opponent = session.league.team(opponentId)
    val (w, l) = session.headToHead(opponentId)
    val rival = session.rival()?.id == opponentId
    SectionCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "다음 경기 · ${DAY_NAMES.getOrElse(game.day) { "?" }}요일 ${if (home) "홈" else "원정"}",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textMuted,
                modifier = Modifier.weight(1f),
            )
            if (rival) {
                Pill("라이벌전", AppColors.warn)
                Spacer(Modifier.width(tokens.spacing.s))
            }
            Text("상대 전적 $w-$l", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
        }
        Spacer(Modifier.height(tokens.spacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MatchupSide(session, session.userTeamId, session.probableStarter(session.userTeamId), Modifier.weight(1f))
            Text("VS", style = MaterialTheme.typography.titleLarge, color = tokens.base.textMuted, modifier = Modifier.padding(horizontal = tokens.spacing.s))
            MatchupSide(session, opponentId, session.probableStarter(opponentId), Modifier.weight(1f))
        }
        Spacer(Modifier.height(tokens.spacing.s))
        Text(
            if (session.weekPaused) "${session.week}주차를 이어서 치러요." else "${opponent.name}전으로 ${session.week}주차를 시작해요.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.base.textSecondary,
        )
    }
}

/** 매치업 한쪽: 엠블럼 · 구단 별칭(우리 팀은 굵게) · 예상 선발 · 그 투수 승·평균자책 */
@Composable
private fun MatchupSide(session: GameSession, teamId: baseballgm.model.TeamId, starter: baseballgm.model.Player?, modifier: Modifier) {
    val tokens = AppTheme.tokens
    val team = session.league.team(teamId)
    val ours = teamId == session.userTeamId
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamEmblem(teamId, team.nickname.take(1), size = tokens.sizes.numberBadgeLarge)
        Spacer(Modifier.height(tokens.spacing.xs))
        Text(team.nickname, style = MaterialTheme.typography.bodyMedium, fontWeight = if (ours) FontWeight.Bold else FontWeight.Normal, color = tokens.base.text)
        if (starter != null) {
            val line = session.pitching(starter.id)
            Text(starter.registeredName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tokens.base.text)
            Text(
                if (line.outs == 0) "선발 예정 · 첫 등판" else "${line.wins}승 ${line.losses}패 · ${line.era.fixed(2)}",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textMuted,
            )
        }
    }
}

/** 메시지 미리보기: 비서 한 줄 + 답장이 필요하면 빨간 칩. 누르면 메시지 탭 */
@Composable
private fun MessagePreview(session: GameSession, onOpen: () -> Unit) {
    val tokens = AppTheme.tokens
    val pending = Messages.needsReply(session)
    SectionCard(null, modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            baseballgm.app.ui.SecretaryAvatar()
            Spacer(Modifier.width(tokens.spacing.m))
            Column(Modifier.weight(1f)) {
                Text(Secretary.NAME, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
                Text(
                    session.pendingIncident?.headline?.let { "$it — 답 주시면 진행할게요." } ?: Briefing.headline(session),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.base.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pending > 0) {
                Spacer(Modifier.width(tokens.spacing.s))
                Pill("답장 $pending", AppColors.bad)
            }
        }
    }
}

/** 타일 넷 (2×2). 칸마다 이름 · 숫자 하나 · 상태 한 줄 */
@Composable
private fun Tiles(session: GameSession, actions: HomeActions) {
    val tokens = AppTheme.tokens
    val firstTeam = session.roster(baseballgm.model.RosterLevel.FIRST_TEAM)
    val injured = firstTeam.count { it.condition.isInjured }
    val tired = firstTeam.count { it.condition.fatigue >= FATIGUE_ALERT }
    val deadlineWeeks = session.state.calendar.tradeDeadlineWeek - session.week + 1
    val goal = GoalProgress.of(session)
    val ranked = session.teamsRanked()
    val leader = ranked.firstOrNull()
    val behind = session.gamesBehind(session.userTeamId)
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s), modifier = Modifier.height(IntrinsicSize.Min)) {
            Tile(
                "선수단",
                "1군 ${firstTeam.size}/${session.firstTeamLimit}",
                listOfNotNull(if (injured > 0) "부상 $injured" else null, if (tired > 0) "피로 주의 $tired" else null).joinToString(" · ").ifBlank { "모두 뛸 수 있어요" },
                actions.openSquad,
                Modifier.weight(1f),
            )
            Tile(
                "영입",
                when {
                    session.inFreeAgency -> "FA ${session.faRound()}/${session.faRounds()} 라운드"
                    session.tradeOpen -> "트레이드 마감 ${deadlineWeeks}주"
                    else -> "트레이드 마감 지남"
                },
                "관찰 ${session.focusUsed}/${session.focusSlots}",
                actions.openRecruit,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s), modifier = Modifier.height(IntrinsicSize.Min)) {
            Tile(
                "구단주 목표",
                session.seasonGoal().kind.label,
                null,
                actions.openClub,
                Modifier.weight(1f),
                chip = goal.status.label to when (goal.status) {
                    GoalStatus.ON_TRACK -> AppColors.good
                    GoalStatus.CLOSE -> AppColors.warn
                    GoalStatus.OFF_TRACK -> AppColors.bad
                },
            )
            Tile(
                "리그",
                if (leader?.teamId == session.userTeamId) "1위 질주 중" else "1위와 ${behind.fixed(1)}경기",
                leader?.let { "1위 ${session.league.team(it.teamId).nickname}" },
                actions.openLeague,
                Modifier.weight(1f),
            )
        }
    }
}

/** 타일 한 칸. 카드 안에 카드를 넣지 않도록 타일끼리는 바탕 위에 나란히 둔다 */
@Composable
private fun Tile(
    label: String,
    value: String,
    sub: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    chip: Pair<String, androidx.compose.ui.graphics.Color>? = null,
) {
    val tokens = AppTheme.tokens
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(tokens.radii.card))
            .background(tokens.base.card)
            .clickable(onClick = onClick)
            .padding(tokens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tokens.base.text, maxLines = 2)
        chip?.let { (text, color) -> Pill(text, color) }
        sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.base.textSecondary, maxLines = 2) }
    }
}

/**
 * 이번 주 일정 띠: 요일 칸마다 상대 별칭, 치른 날은 승·패(멈춘 주), 라이벌전은 주의 색 테두리 대신 글자 아래 점.
 * 승패는 색 + 글자("승"/"패")로 — 색만으로 구분하지 않는다.
 */
@Composable
private fun WeekStrip(session: GameSession) {
    val tokens = AppTheme.tokens
    val schedule = session.weekSchedule().sortedBy { it.day }
    if (schedule.isEmpty()) return
    val played = session.weekGamesSoFar()
    val rival = session.rival()?.id
    SectionCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("이번 주", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tokens.base.text, modifier = Modifier.weight(1f))
            Text("${session.week}주차 · ${session.calendarLabel()}", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
        }
        Spacer(Modifier.height(tokens.spacing.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            schedule.forEachIndexed { index, game ->
                val home = game.home == session.userTeamId
                val opponentId = if (home) game.away else game.home
                val result = played.getOrNull(index)?.box?.let { box ->
                    when {
                        box.tie -> "무" to AppColors.muted
                        box.winner == session.userTeamId -> "승" to AppColors.good
                        else -> "패" to AppColors.bad
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(DAY_NAMES.getOrElse(game.day) { "?" }, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
                    Box(
                        Modifier.size(tokens.sizes.numberBadge).clip(CircleShape)
                            .background(result?.second?.copy(alpha = RESULT_ALPHA) ?: tokens.base.cardInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            result?.first ?: (if (home) "홈" else "원"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = result?.second ?: tokens.base.textSecondary,
                        )
                    }
                    Text(
                        session.league.team(opponentId).nickname.take(OPPONENT_CHARS),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (opponentId == rival) FontWeight.Bold else FontWeight.Normal,
                        color = if (opponentId == rival) AppColors.warn else tokens.base.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 그 시기에만 나오는 소식: 국제대회 결과, 새 시즌 첫 2주의 스토브리그 결과 */
@Composable
private fun SeasonNotes(session: GameSession) {
    val tokens = AppTheme.tokens
    val tournament = session.tournamentResult()
    val offseason = session.lastOffseason?.takeIf { session.inFreeAgency || session.week <= OFFSEASON_CARD_WEEKS }
    if (tournament == null && offseason == null) return
    SectionCard(null) {
        // 국제대회·스토브리그 결과는 그 시기에만 한 줄 (예전엔 칩 열 개짜리 카드)
        session.tournamentResult()?.let { result ->
            val mine = result.squad.count { it.teamId == session.userTeamId }
            val exempted = result.squad.count { it.teamId == session.userTeamId && it.playerId in result.exempted }
            NavRow(
                "${result.season} ${result.label} ${result.medal.label}",
                "우리 팀 대표 ${mine}명" + if (exempted > 0) " · 병역 특례 ${exempted}명" else "",
                null,
            )
        }
        val offseason = session.lastOffseason
        if (offseason != null && (session.inFreeAgency || session.week <= OFFSEASON_CARD_WEEKS)) {
            val warnings = offseason.enlistmentWarnings.filter {
                session.state.allPlayers().any { player -> player.id == it.playerId && player.teamId == session.userTeamId }
            }.map { it.message(session.player(it.playerId).registeredName) }
            val penalty = offseason.capPenalties.firstOrNull { it.teamId == session.userTeamId }?.message(session.userTeam.name)
            NavRow(
                "${offseason.season} 스토브리그 결과",
                "은퇴 ${offseason.retired.size} · 신인 ${offseason.rookies.size} · 드래프트 ${offseason.drafted.size} · " +
                    "FA 계약 ${offseason.faSignings.size} · 방출 ${offseason.released.size} · 각성 ${offseason.awakened.size} · " +
                    "급노쇠 ${offseason.collapsed.size} · 입대 ${offseason.enlisted.size} · 제대 ${offseason.discharged.size}",
                null,
            )
            (listOfNotNull(offseason.environmentAnnouncement, penalty) + warnings.take(OFFSEASON_WARNINGS)).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.warn, modifier = Modifier.padding(bottom = tokens.spacing.xs))
            }
            // 단장이 겨울에 정한 것이 어떻게 처리됐는지 (외국인 재계약·연봉·방출 — 2026-10-01)
            if (offseason.planNotes.isNotEmpty()) {
                Text(
                    "겨울 결정: " + offseason.planNotes.take(OFFSEASON_PLAN_NOTES).joinToString(" · ") +
                        if (offseason.planNotes.size > OFFSEASON_PLAN_NOTES) " 외 ${offseason.planNotes.size - OFFSEASON_PLAN_NOTES}건" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.base.textSecondary,
                    modifier = Modifier.padding(bottom = tokens.spacing.xs),
                )
            }
        }
    }
}

/** 가을야구 커트라인과의 거리 한 줄 ("5위와 3.5경기 차") */
private fun postseasonLine(session: GameSession): String {
    val ranked = session.teamsRanked()
    val rank = session.rank()
    val mine = session.record()
    val line = ranked.getOrNull(if (rank <= POSTSEASON_SPOTS) POSTSEASON_SPOTS else POSTSEASON_SPOTS - 1) ?: return ""
    val gap = ((line.wins - line.losses) - (mine.wins - mine.losses)) / 2.0
    return if (rank <= POSTSEASON_SPOTS) {
        "가을야구 안 · ${POSTSEASON_SPOTS + 1}위와 ${(-gap).fixed(1)}경기 차"
    } else {
        "${POSTSEASON_SPOTS}위와 ${gap.fixed(1)}경기 차"
    }
}

/** 정규시즌이 끝난 뒤의 단계 카드: FA·포스트시즌·시즌 종료. 한 번에 하나만 보인다 */
@Composable
private fun SeasonPhaseCard(session: GameSession, actions: HomeActions) {
    val tokens = AppTheme.tokens
    when {
        // FA 시장이 열려 있으면 그것부터 알린다 (docs/11)
        session.inFreeAgency -> SectionCard("FA 시장 ${session.faRound()}/${session.faRounds()} 라운드") {
            Text(
                baseballgm.app.Briefing.faNegotiationLine(session) + " 영입 탭 FA에서 조건을 내고 라운드를 넘겨요.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(tokens.spacing.s))
            OutlinedButton(onClick = { session.skipFreeAgency() }, enabled = !session.busy, modifier = Modifier.fillMaxWidth()) {
                Text("한 번에 마무리하고 ${session.league.season + 1} 시즌으로")
            }
        }

        // 포스트시즌 (docs/14). 정규시즌이 끝나면 먼저 치른다 — 우리 시리즈는 한 경기씩, 경기 사이에 단장 결정 (2026-10-03)
        !session.postseasonDone -> PostseasonLiveCard(session, actions)

        else -> session.postseason()?.let { result ->
            val champion = session.league.team(result.champion)
            SectionCard("${result.season} 포스트시즌") {
                Text(
                    if (result.champion == session.userTeamId) "${champion.name} 우승 — 정말 고생 많으셨어요" else "${champion.name} 우승",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (result.champion == session.userTeamId) AppColors.good else tokens.base.text,
                )
                // 대진·경기 다시 보기와 시상식 (2026-10-01, 진단 3번)
                NavRow(
                    "포스트시즌 다시 보기",
                    result.series.joinToString(" · ") { "${it.round.label} ${session.league.team(it.winner).nickname}" },
                    actions.openPostseason,
                )
                val mvp = session.seasonAwards().firstOrNull { it.kind == baseballgm.league.AwardKind.MVP }
                val ours = session.seasonAwards().count { it.teamId == session.userTeamId }
                NavRow(
                    "${result.season} 시상식",
                    listOfNotNull(mvp?.let { "MVP ${it.name}(${session.league.team(it.teamId).nickname})" }, if (ours > 0) "우리 팀 ${ours}개 수상" else null)
                        .joinToString(" · "),
                    { actions.openAwards(result.season) },
                )
                SectionDivider()
                Text(
                    "진행 버튼을 누르면 스토브리그 준비로 가요. 외국인 재계약·연봉·방출 명단을 정하고 시작해요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.base.textMuted,
                )
            }
        }
    }
}

private const val POSTSEASON_SPOTS = 5
private const val OFFSEASON_CARD_WEEKS = 2
private const val OFFSEASON_WARNINGS = 3
private const val OFFSEASON_PLAN_NOTES = 4

/** 피로 주의로 세는 기준 — 로스터 화면 줄의 피로 "나쁨" 구간과 같다 (표시용) */
private const val FATIGUE_ALERT = 70

/** 일정 띠의 상대 별칭 글자 수 (칸이 좁다) */
private const val OPPONENT_CHARS = 3

/** 승패 원 바탕의 옅기 */
private const val RESULT_ALPHA = 0.16f

private val DAY_NAMES = baseballgm.events.Incident.DAY_NAMES

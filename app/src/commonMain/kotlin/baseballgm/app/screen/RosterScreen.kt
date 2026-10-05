package baseballgm.app.screen

import baseballgm.util.fixed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.Pill
import baseballgm.app.ui.PlayerFilterBar
import baseballgm.app.ui.PlayerList
import baseballgm.app.ui.PlayerLine
import baseballgm.app.ui.RowAction
import baseballgm.app.ui.rememberPlayerFilter
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import baseballgm.app.ui.Secretary
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionDivider
import baseballgm.app.ui.StrengthBar
import baseballgm.app.ui.TeamColors
import baseballgm.app.ui.TeamEmblem
import baseballgm.league.TeamStrengthRange
import baseballgm.scouting.LeaguePowerBoard
import baseballgm.season.RosterSlot
import baseballgm.util.iGa
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 로스터 메인 화면 (docs/15 M10 표, docs/16 §4-3).
 *
 * 위에서부터 **전력 요약 → 포지션 뎁스 → 리그 전력 비교 → 선수 목록** (2026-10-01).
 * 한 줄 요약(비서) → 핵심 숫자 → 상세 순서(docs/16 §3)를 화면 전체에 그대로 적용한 배치다.
 *
 * - 전력 요약: 부문별 가로 막대. 진한 막대가 현재 전력, 옅게 깔린 막대가 베스트 전력, 세로선이 리그 평균
 * - 포지션 뎁스: 야구장 다이아몬드 위에 자리별 주전. 누르면 그 자리 1·2군 뎁스가 아래에 펼쳐진다
 * - 리그 전력 비교: 타 팀은 **스카우트 관측값을 엔진에서 합산한 범위**로만 본다 (불변 원칙 4).
 *   그래서 우리 순위도 "추정 3~5위"처럼 범위다
 *
 * **엔트리를 직접 바꾼다** (docs/07, 2026-09-27): 2군 선수 "등록", 1군 선수 "말소".
 * 둘 다 **맞바꾸기 창**([SwapDialog])으로 간다 — 등록은 내릴 1군 선수를, 말소는 올릴 2군 선수를 같이 고른다.
 * 1군이 꽉 찼을 때 한 번에 바꾸려고 만든 창이다 (2026-10-01). 포지션 제한은 없어서(같은 날 유저 요청) 상대는 아무나 고를 수 있다.
 * 혼자 해도 규칙이 지켜지면 "혼자 하기"도 함께 뜬다. 바뀐 엔트리는 주중이라도 다음 경기부터 반영된다.
 */
/** 선수단 탭의 구역 (2026-10-03 화면 개편) */
enum class SquadSection(val label: String) { PLAYERS("선수"), DEPTH("뎁스"), DIRECTIVE("지시") }

/**
 * 선수단 탭 (2026-10-03 화면 개편 — 예전 "로스터" 탭 + 사전 지시).
 * 선수(전력 요약 + 목록) / 뎁스(다이아몬드 + 리그 전력 비교) / 지시(사전 지시)를 세그먼트로 나눈다.
 * 예전엔 한 화면에 카드 넷이 세로로 쌓여 목록까지 한참 내려가야 했다.
 */
@Composable
fun SquadScreen(session: GameSession, onPlayer: (PlayerId) -> Unit, onLeague: (LeagueSection, Boolean) -> Unit = { _, _ -> }) {
    var section by rememberSaveable { mutableStateOf(SquadSection.PLAYERS) }
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = section.ordinal) {
            SquadSection.entries.forEach { entry ->
                Tab(selected = section == entry, onClick = { section = entry }, text = { Text(entry.label) })
            }
        }
        when (section) {
            SquadSection.DIRECTIVE -> DirectiveScreen(session)
            else -> RosterScreen(session, onPlayer, section, onLeague)
        }
    }
}

@Composable
fun RosterScreen(
    session: GameSession,
    onPlayer: (PlayerId) -> Unit,
    section: SquadSection = SquadSection.PLAYERS,
    onLeague: (LeagueSection, Boolean) -> Unit = { _, _ -> },
) {
    val revision = session.revision
    // 목록은 기본이 간단히(종합 + 핵심 기록 둘), "능력치 보기"를 켜면 능력치 전 열 (FM 식, 2026-10-03)
    var detailed by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val level = if (tab == 0) RosterLevel.FIRST_TEAM else RosterLevel.FUTURES
    var filter by rememberPlayerFilter("roster")
    val players = session.roster(level).filter {
        filter.matches(session.scout(it), it.contract.salary, it.contract.yearsRemaining)
    }
    var demoting by remember { mutableStateOf<Player?>(null) }
    var promoting by remember { mutableStateOf<Player?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val editable = !session.seasonOver && !session.inFreeAgency
    // 리그 전체 선수를 스카우트 시선으로 한 번씩 보는 계산이라, 상태가 바뀔 때만 다시 한다
    val board = remember(revision) { session.powerBoard() }
    val current = remember(revision) { session.currentStrength() }
    val best = remember(revision) { session.bestStrength() }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = AppTheme.tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.m),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = AppTheme.tokens.spacing.s),
    ) {
        // 스토브리그(FA 시장) 중엔 다음 시즌 선수단을 보여 준다 — 빈 포지션이 바로 보이게
        if (session.inFreeAgency) {
            item {
                val leaving = session.faAgents().count { session.isOurFormer(it) }
                baseballgm.app.ui.SecretaryCard(
                    "다음 시즌 선수단이에요. 시장에 나간 우리 FA ${leaving}명과 은퇴·방출 선수는 빠졌고, 신인은 들어와 있어요. " +
                        "FA에서 다시 잡으면 여기로 돌아와요.",
                )
            }
        }
        if (section == SquadSection.DEPTH) {
            item { DepthCard(session, onPlayer) }
            item { LeaguePowerCard(session, board) }
            return@LazyColumn
        }
        item { StrengthSummaryCard(session, current, best, board) }
        // 기록 바로가기 (2026-10-04, 유저 요청 "선수단 탭에서 팀 기록표로 가는 길"): 리그 탭 기록표를 "우리 팀"으로 연다
        if (!session.inFreeAgency) item { RecordLinksCard(session, onLeague) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = AppTheme.tokens.spacing.s, start = AppTheme.tokens.spacing.xs)) {
                Text("선수 목록", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { detailed = !detailed }) { Text(if (detailed) "간단히 보기" else "능력치 보기") }
            }
        }
        item {
            SecondaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("1군 ${session.roster(RosterLevel.FIRST_TEAM).size}/${session.firstTeamLimit}") },
                )
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("2군 ${session.roster(RosterLevel.FUTURES).size}") })
            }
        }
        item {
            PlayerFilterBar(filter, { filter = it }, resultCount = players.size, showContract = true, showSalary = true)
        }
        if (editable) {
            item {
                Text(
                    if (tab == 0) {
                        "말소하면 ${session.reRegisterWeekIfDemoted()}주차까지 다시 못 올려요. 바꾼 엔트리는 다음 경기부터 반영돼요."
                    } else {
                        "등록하면 다음 경기부터 1군에서 뛰어요. 1군이 꽉 찼으면 내릴 선수를 같이 골라요."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.muted,
                )
            }
        }
        item {
            // 선수 목록 (2026-10-02 레퍼런스 리팩토링): 왼쪽 고정 + 숫자 가로 스크롤, 타자·투수 묶음
            val sections = playerListSections(session, players, level, detailed = detailed)
            PlayerList(
                sections = sections,
                onPlayer = onPlayer,
                action = if (!editable) {
                    null
                } else {
                    { row ->
                        val player = session.player(row.id)
                        if (level == RosterLevel.FIRST_TEAM) {
                            // 말소: 혼자 내려도 되면 "말소만", 최소 인원이 깨지면 대신 올릴 2군 선수를 같이 고른다
                            RowAction("말소") { demoting = player }
                        } else {
                            // 등록: 자리가 있으면 "그냥 등록", 꽉 찼거나 포지션이 최대면 내릴 1군 선수를 같이 고른다
                            RowAction("등록") {
                                val reason = session.eligibilityProblem(player)
                                if (reason != null) problem = reason else promoting = player
                            }
                        }
                    }
                },
            )
        }
    }

    demoting?.let { player ->
        SwapDialog(
            session = session,
            title = "${player.registeredName} 말소",
            guide = "그냥 내려도 되고, 대신 올릴 2군 선수를 같이 고르면 한 번에 바꿔요. " +
                "말소한 선수는 ${session.reRegisterWeekIfDemoted()}주차부터 다시 올릴 수 있어요.",
            soloLabel = "말소만 하기 (빈자리는 주 시작에 자동으로 채워져요)",
            soloProblem = session.demoteProblem(player),
            candidateTitle = "대신 올릴 2군 선수",
            candidates = session.swapInCandidates(player),
            onSolo = { session.demote(player) },
            onPick = { up -> session.swap(up, player) },
            onDismiss = { demoting = null },
        )
    }

    promoting?.let { player ->
        SwapDialog(
            session = session,
            title = "${player.registeredName} 등록",
            guide = "1군이 ${session.firstTeamLimit}명으로 꽉 찼으면 내릴 선수를 같이 골라요 (같은 포지션 먼저 보여요). " +
                "내린 선수는 ${session.reRegisterWeekIfDemoted()}주차부터 다시 올릴 수 있어요.",
            soloLabel = "아무도 안 내리고 등록",
            soloProblem = session.promoteProblem(player),
            candidateTitle = "대신 내릴 1군 선수",
            candidates = session.swapOutCandidates(player),
            onSolo = { session.promote(player) },
            onPick = { down -> session.swap(player, down) },
            onDismiss = { promoting = null },
        )
    }

    problem?.let { reason ->
        AlertDialog(
            onDismissRequest = { problem = null },
            title = { Text("지금은 안 돼요") },
            text = { Text(reason) },
            confirmButton = { TextButton(onClick = { problem = null }) { Text("알겠어요") } },
        )
    }
}

/**
 * 엔트리 맞바꾸기 창 (2026-10-01 유저 지적: "내리면 최소 인원 미달, 올리면 꽉 차서 한 명 내려야 하는 모순").
 *
 * 등록·말소 둘 다 여기로 온다. 혼자 할 수 있으면(1군에 자리가 있거나, 말소) 맨 위에 "혼자 하기"가 있고,
 * 아래에는 맞바꿀 상대가 나온다 (같은 포지션 먼저). 포지션 제한은 없다 (2026-10-01 유저 요청).
 */
@Composable
private fun SwapDialog(
    session: GameSession,
    title: String,
    guide: String,
    soloLabel: String,
    soloProblem: String?,
    candidateTitle: String,
    candidates: List<Player>,
    onSolo: () -> Unit,
    onPick: (Player) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = AppTheme.tokens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(guide, style = MaterialTheme.typography.bodySmall)
                if (soloProblem == null) {
                    OutlinedButton(
                        onClick = { onSolo(); onDismiss() },
                        modifier = Modifier.fillMaxWidth().padding(top = tokens.spacing.s),
                    ) { Text(soloLabel) }
                }
                Text(
                    candidateTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.base.textSecondary,
                    modifier = Modifier.padding(top = tokens.spacing.m, bottom = tokens.spacing.xs),
                )
                if (candidates.isEmpty()) {
                    Text(
                        if (soloProblem != null) "맞바꿀 수 있는 선수가 없어요. $soloProblem" else "맞바꿀 수 있는 선수가 없어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.bad,
                    )
                }
                candidates.forEach { other ->
                    // 한 줄 = 선수 정체(등번호·이름·상태 / 포지션·자리·기록) + 종합. 누르면 바로 맞바꾼다
                    val overall = session.scout(other).overall
                    PlayerLine(
                        session.tagOf(other, caption = "${session.slotLabel(other)} · ${session.seasonSummary(other)}"),
                        onClick = null,
                        modifier = Modifier.clickable { onPick(other); onDismiss() },
                    ) {
                        Text(overall.toString(), color = tokens.grade.of(overall.center), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

// ---------- 전력 요약 ----------

/** 부문별 막대 축. 팀 전력은 40~90 에 몰려 있어 1~100 축이면 차이가 안 보인다 */
private val SUMMARY_AXIS = 20.0..100.0

private fun rankText(rank: IntRange?): String = when {
    rank == null -> "순위 추정 불가"
    rank.first == rank.last -> "추정 ${rank.first}위"
    else -> "추정 ${rank.first}~${rank.last}위"
}

/**
 * 전력 요약. 비서 한 줄 → 핵심 숫자(현재·베스트·추정 순위) → 부문별 막대.
 * 부문 막대: 진한 쪽이 현재, 옅게 깔린 쪽이 베스트, 세로선이 리그 평균(관측값 중심의 평균).
 */
@Composable
private fun StrengthSummaryCard(
    session: GameSession,
    current: TeamStrengthRange,
    best: TeamStrengthRange,
    board: LeaguePowerBoard,
) {
    val tokens = AppTheme.tokens
    val teamColor = TeamColors.of(session.userTeamId)
    val parts = listOf(
        Triple("타선", current.lineup, best.lineup) to board.rows.map { it.strength.lineup.center }.average(),
        Triple("선발", current.rotation, best.rotation) to board.rows.map { it.strength.rotation.center }.average(),
        Triple("불펜", current.bullpen, best.bullpen) to board.rows.map { it.strength.bullpen.center }.average(),
    )
    val now = current.overall.center
    val top = best.overall.center
    val weakest = parts.minBy { (part, average) -> part.second.center - average }
    val message = buildString {
        append("지금 전력은 ${now.fixed(0)}, 리그에선 ${rankText(board.viewerRank)} 정도로 보여요. ")
        append(
            if (top - now >= 1.5) {
                "부상자가 돌아오고 2군까지 최적으로 짜면 ${top.fixed(0)}까지 올라가요. "
            } else {
                "지금이 거의 베스트 전력이에요. "
            },
        )
        val (part, average) = weakest
        append(
            if (part.second.center < average) {
                "${part.first.iGa()} 리그 평균보다 처지니 보강 1순위예요."
            } else {
                "세 부문 모두 리그 평균 이상이에요."
            },
        )
    }

    SecretaryCard(message, title = "전력 요약") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // 가장 큰 숫자는 현재 전력 하나 (절제 규칙 "화면당 주인공 하나")
            KeyNumber("현재 전력", now.fixed(0), teamColor, hero = true)
            KeyNumber("베스트 전력", top.fixed(0), tokens.base.text)
            KeyNumber("리그 순위", rankText(board.viewerRank), tokens.base.text)
        }
        Spacer(Modifier.height(tokens.spacing.m))
        parts.forEach { (part, average) ->
            val (label, now, best) = part
            Row(Modifier.fillMaxWidth().padding(vertical = AppTheme.tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = tokens.base.textSecondary, modifier = Modifier.width(36.dp))
                StrengthBar(
                    low = now.center,
                    high = now.center,
                    color = teamColor,
                    axis = SUMMARY_AXIS,
                    ghost = best.center,
                    marker = average,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(tokens.spacing.s))
                Text(
                    if (best.center - now.center >= 0.5) "${now.center.fixed(0)} → ${best.center.fixed(0)}" else now.center.fixed(0),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(64.dp),
                )
            }
        }
        Text(
            "진한 막대 현재 · 옅은 막대 베스트(부상 복귀·2군 포함) · 세로선 리그 평균",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.muted,
        )
    }
}

@Composable
private fun KeyNumber(label: String, value: String, color: androidx.compose.ui.graphics.Color, hero: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
        Text(
            value,
            style = if (hero) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

// ---------- 포지션 뎁스 ----------

/**
 * 다이아몬드 위 수비 위치. 홈플레이트에서의 거리(외야 반지름 대비)와 각도(가운데 0, 왼쪽 음수).
 * 칸끼리 겹치지 않게 실제 수비 위치보다 조금씩 벌려 놓았다.
 */
private val FIELD_SPOTS: Map<RosterSlot, Pair<Float, Float>> = mapOf(
    RosterSlot.LEFT to (0.86f to -32f),
    RosterSlot.CENTER to (0.92f to 0f),
    RosterSlot.RIGHT to (0.86f to 32f),
    RosterSlot.SHORT to (0.55f to -18f),
    RosterSlot.SECOND to (0.55f to 18f),
    RosterSlot.THIRD to (0.40f to -45f),
    RosterSlot.FIRST to (0.40f to 45f),
)

private val NODE_WIDTH = 68.dp
private val NODE_HEIGHT = 40.dp
private val FIELD_HEIGHT = 300.dp

/**
 * 포지션 뎁스. 야구장 위에 자리별 주전(1군에서 뛸 수 있는 선수 중 종합 1위)을 놓고,
 * 지명타자·선발·불펜은 아래 줄에 둔다. 칸을 누르면 그 자리 1·2군 뎁스가 펼쳐진다.
 * 포지션별 인원 제한은 없다 (2026-10-01 유저 요청). 자리마다 1군 인원만 보여 준다.
 */
@Composable
private fun DepthCard(session: GameSession, onPlayer: (PlayerId) -> Unit) {
    val tokens = AppTheme.tokens
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSlot = RosterSlot.entries.firstOrNull { it.key == selected }
    val select: (RosterSlot) -> Unit = { slot -> selected = if (selected == slot.key) null else slot.key }

    SectionCard("포지션 뎁스") {
        FieldDiagram(session, selectedSlot, select)
        Spacer(Modifier.height(tokens.spacing.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(RosterSlot.DH, RosterSlot.SP, RosterSlot.RP).forEach { slot ->
                DepthNode(session, slot, slot == selectedSlot) { select(slot) }
            }
        }
        if (selectedSlot != null) {
            DepthList(session, selectedSlot, onPlayer)
        } else {
            Spacer(Modifier.height(tokens.spacing.s))
            Text("자리를 누르면 1·2군 뎁스가 보여요.", style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
        }
    }
}

/** 야구장 그림 + 수비 위치 칸. 외야 부채꼴 → 내야 다이아몬드 → 베이스 순으로 그린다 */
@Composable
private fun FieldDiagram(session: GameSession, selected: RosterSlot?, onSelect: (RosterSlot) -> Unit) {
    val tokens = AppTheme.tokens
    val grass = tokens.base.cardInset
    val dirt = tokens.base.textMuted.copy(alpha = 0.22f)
    val chalk = tokens.base.card
    BoxWithConstraints(Modifier.fillMaxWidth().height(FIELD_HEIGHT)) {
        val width = maxWidth
        val height = maxHeight
        // 외야 반지름: 파울 라인(±45°)이 좌우로 넘치지 않고, 위로도 넘치지 않는 쪽
        val radius = minOf(width / 2 / 0.7071f, height - NODE_HEIGHT / 2 - 4.dp)
        val homeX = width / 2
        val homeY = height - NODE_HEIGHT / 2 - 2.dp

        Canvas(Modifier.matchParentSize()) {
            val r = radius.toPx()
            val home = Offset(homeX.toPx(), homeY.toPx())
            drawArc(grass, startAngle = 225f, sweepAngle = 90f, useCenter = true, topLeft = home - Offset(r, r), size = Size(r * 2, r * 2))
            val base = r * 0.40f * 0.7071f
            val first = home + Offset(base, -base)
            val second = home + Offset(0f, -base * 2)
            val third = home + Offset(-base, -base)
            val infield = Path().apply {
                moveTo(home.x, home.y)
                lineTo(first.x, first.y)
                lineTo(second.x, second.y)
                lineTo(third.x, third.y)
                close()
            }
            drawPath(infield, dirt)
            val bag = 5.dp.toPx()
            listOf(first, second, third, home).forEach {
                drawRect(chalk, topLeft = it - Offset(bag, bag), size = Size(bag * 2, bag * 2))
            }
        }

        FIELD_SPOTS.forEach { (slot, spot) ->
            val (distance, angle) = spot
            val radians = angle / 180f * PI.toFloat()
            val x = homeX + radius * distance * sin(radians)
            val y = homeY - radius * distance * cos(radians)
            Box(Modifier.offset(x = x - NODE_WIDTH / 2, y = y - NODE_HEIGHT / 2)) {
                DepthNode(session, slot, slot == selected) { onSelect(slot) }
            }
        }
        // 포수는 홈플레이트 자리
        Box(Modifier.offset(x = homeX - NODE_WIDTH / 2, y = homeY - NODE_HEIGHT / 2)) {
            DepthNode(session, RosterSlot.C, RosterSlot.C == selected) { onSelect(RosterSlot.C) }
        }
    }
}

/**
 * 자리 칸 하나: "SS 2"(1군 인원) + 주전 이름 + 종합.
 * 투수 칸(선발·불펜)은 주전 대신 1군 인원과 상위 평균을 보여준다.
 * 1군에 뛸 수 있는 선수가 없으면 "공백"(주의 색).
 */
@Composable
private fun DepthNode(session: GameSession, slot: RosterSlot, selected: Boolean, onClick: () -> Unit) {
    val tokens = AppTheme.tokens
    val depth = session.depthOf(slot)
    val firstTeam = depth.filter { it.rosterLevel == RosterLevel.FIRST_TEAM }
    val ready = firstTeam.filter { !it.condition.isInjured && it.military.isAvailable }
    val starter = ready.firstOrNull()
    Column(
        Modifier
            .width(NODE_WIDTH)
            .height(NODE_HEIGHT)
            .clip(RoundedCornerShape(tokens.radii.chip))
            .background(tokens.base.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) tokens.base.brand else tokens.base.line, RoundedCornerShape(tokens.radii.chip))
            .clickable(onClick = onClick)
            .padding(horizontal = AppTheme.tokens.spacing.xs, vertical = AppTheme.tokens.spacing.xs),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(slot.key, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.base.textSecondary)
            Spacer(Modifier.weight(1f))
            Text("${firstTeam.size}명", style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
        }
        if (starter == null) {
            Text("공백", style = MaterialTheme.typography.labelMedium, color = AppColors.warn, fontWeight = FontWeight.Bold)
        } else if (slot.pitcher) {
            val average = ready.take(if (slot == RosterSlot.SP) 5 else 7).map { session.overall(it) }.average()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("평균", style = MaterialTheme.typography.labelSmall, color = AppColors.muted, modifier = Modifier.weight(1f))
                Text(average.fixed(0), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AppColors.forRating(average))
            }
        } else {
            val overall = session.overall(starter)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    starter.registeredName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(overall.fixed(0), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AppColors.forRating(overall))
            }
        }
    }
}

/**
 * 고른 자리의 뎁스 차트. 1군 먼저 · 뛸 수 있는 선수 먼저 · 종합 순. 누르면 선수 상세.
 * 카드 안이라 바탕을 따로 칠하지 않고 구분선으로 나눈다 (카드 중첩 금지). 1군/2군은 칩이 아니라 소제목.
 */
@Composable
private fun DepthList(session: GameSession, slot: RosterSlot, onPlayer: (PlayerId) -> Unit) {
    val tokens = AppTheme.tokens
    val depth = session.depthOf(slot)
    val firstTeam = session.slotCounts().firstOrNull { it.label == slot.label }?.count ?: 0
    SectionDivider()
    Text(
        "${slot.label} 뎁스 · 1군 ${firstTeam}명",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
    )
    if (depth.isEmpty()) {
        Text("이 자리 선수가 없어요. 시장에서 찾아볼까요?", style = MaterialTheme.typography.bodySmall, color = AppColors.warn)
    }
    depth.groupBy { it.rosterLevel }.forEach { (level, players) ->
        Text(
            if (level == RosterLevel.FIRST_TEAM) "1군" else "2군",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.base.textMuted,
            modifier = Modifier.padding(top = tokens.spacing.s),
        )
        players.forEach { player ->
            val overall = session.scout(player).overall
            PlayerLine(session.tagOf(player), onClick = { onPlayer(player.id) }) {
                Text(
                    overall.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.forRating(overall.center),
                )
            }
        }
    }
}

// ---------- 리그 전력 비교 ----------

/**
 * 리그 전력 비교 (현재 전력 종합). 우리 팀은 구단 색 선명한 막대, 타 팀은 중립색 **범위 막대**.
 * 타 팀 범위는 엔진([baseballgm.scouting.TeamStrengthScouting])이 스카우트 관측값만 모아 낸 것이라
 * 화면은 진짜 전력을 알 방법이 없다 (불변 원칙 4).
 */
@Composable
private fun LeaguePowerCard(session: GameSession, board: LeaguePowerBoard) {
    val tokens = AppTheme.tokens
    val lowest = board.rows.minOf { it.strength.overall.low }
    val highest = board.rows.maxOf { it.strength.overall.high }
    val axis = (floor((lowest - 3) / 10) * 10)..(ceil((highest + 3) / 10) * 10)

    // 추정 순위는 위 전력 요약이 이미 말한다 — 여기선 되풀이하지 않는다
    SectionCard("리그 전력 비교") {
        board.rows.forEach { row ->
            val team = session.league.team(row.teamId)
            val range = row.strength.overall
            Row(Modifier.fillMaxWidth().padding(vertical = AppTheme.tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                TeamEmblem(row.teamId, team.nickname.take(1), size = 22.dp)
                Spacer(Modifier.width(AppTheme.tokens.spacing.s))
                Text(
                    team.nickname,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (row.isViewer) FontWeight.Bold else FontWeight.Normal,
                    color = if (row.isViewer) tokens.base.text else tokens.base.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(64.dp),
                )
                StrengthBar(
                    low = range.low,
                    high = range.high,
                    color = if (row.isViewer) TeamColors.of(row.teamId) else tokens.base.textSecondary,
                    axis = axis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(tokens.spacing.s))
                Text(
                    if (range.isExact) range.center.fixed(0) else "${range.low.fixed(0)}~${range.high.fixed(0)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (row.isViewer) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
        Spacer(Modifier.height(tokens.spacing.xs))
        Text(
            "다른 팀은 스카우트가 본 만큼만 보여요. 막대가 넓을수록 정보가 부족한 거예요 — 집중 관찰을 붙이면 좁아져요.",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.muted,
        )
    }
}

/**
 * 선수단 → 리그 탭 기록 바로가기 세 줄. 설명줄은 우리 팀 팀 기록과 리그 순위 (팀 타율 0.271 · 3위).
 * 리그 탭 허브 위에 그 구역 화면을 쌓는다 — 타자·투수는 "우리 팀" 범위(팀 내 순위)로.
 */
@Composable
private fun RecordLinksCard(session: GameSession, onLeague: (LeagueSection, Boolean) -> Unit) {
    val revision = session.revision
    val summary = remember(revision) {
        fun rank(view: baseballgm.app.TeamView, header: String, label: String) =
            baseballgm.app.StatBoard.teamRankOf(session, view, header)?.let { (value, rank) -> "$label $value · 리그 ${rank}위" }
        Triple(
            rank(baseballgm.app.TeamView.BATTING, "OPS", "팀 OPS"),
            rank(baseballgm.app.TeamView.PITCHING, "평균자책", "팀 평균자책"),
            listOfNotNull(
                rank(baseballgm.app.TeamView.BATTING, "타율", "팀 타율"),
                rank(baseballgm.app.TeamView.PITCHING, "평균자책", "평균자책"),
            ).joinToString(" / "),
        )
    }
    SectionCard("기록") {
        baseballgm.app.ui.NavRow("타자 기록 · 팀 내 순위", summary.first ?: "아직 경기가 없어요", onClick = { onLeague(LeagueSection.BATTING, true) })
        baseballgm.app.ui.NavRow("투수 기록 · 팀 내 순위", summary.second ?: "아직 경기가 없어요", onClick = { onLeague(LeagueSection.PITCHING, true) })
        baseballgm.app.ui.NavRow("팀 기록 · 열 팀 비교", summary.third.ifEmpty { "아직 경기가 없어요" }, onClick = { onLeague(LeagueSection.TEAMS, false) })
    }
}

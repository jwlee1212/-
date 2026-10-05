package baseballgm.app.screen

import baseballgm.util.fixed
import baseballgm.app.ui.AppTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import baseballgm.app.ui.AccuracyGauge
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.Pill
import baseballgm.app.ui.PlayerFilterBar
import baseballgm.app.ui.RatingBar
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.PlayerIdentity
import baseballgm.app.ui.PlayerListScroll
import baseballgm.app.ui.RowAction
import baseballgm.app.ui.playerListItems
import baseballgm.app.ui.rememberPlayerListScroll
import baseballgm.app.ui.StatRow
import baseballgm.app.ui.rememberPlayerFilter
import baseballgm.market.DraftProspect
import baseballgm.market.DraftSelection
import baseballgm.market.DraftPolicy
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import kotlin.math.roundToInt
import baseballgm.model.Attribute
import baseballgm.model.GrowthType
import baseballgm.model.PlayerId
import baseballgm.scouting.ScoutReport
import kotlinx.coroutines.delay

/**
 * 스카우트 탭 (docs/10, docs/15 M10 표).
 *
 * 세 가지를 한다.
 * ① 드래프트 풀 훑기 — 포지션·잠재력·현재 종합으로 거르고, 줄에서 바로 집중 관찰을 붙인다
 * ② 집중 관찰 슬롯 관리 + 투자 단계 — 슬롯은 유한하고, 오래 볼수록 범위가 좁아진다
 * ③ 드래프트 — 드래프트 주차에는 다른 구단의 지명을 한 장씩 보여주고(생중계), 내 차례에 멈춘다
 *
 * **능력치는 전부 리포트(`ScoutReport`)를 거쳐 들어온다.** 화면은 진짜 값을 볼 방법이 없다.
 */
@Composable
fun ScoutScreen(
    session: GameSession,
    onOpenProspect: (PlayerId) -> Unit,
    onOpenDraftClass: () -> Unit = {},
    onOpenDigest: () -> Unit = {},
) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    var tab by rememberSaveable { mutableStateOf(0) }
    val open: (DraftProspect) -> Unit = { onOpenProspect(it.id) }

    // 드래프트 주차에 들어오면 곧장 드래프트 탭을 연다 (진행 버튼 "드래프트로" 도 여기로 온다)
    val draftLive = session.isDraftWeek && !session.draftDone
    LaunchedEffect(draftLive) { if (draftLive) tab = DRAFT_TAB }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("드래프트 풀") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("관찰 ${session.focusUsed}/${session.focusSlots}") })
            Tab(
                selected = tab == DRAFT_TAB,
                onClick = { tab = DRAFT_TAB },
                text = { Text(if (draftLive) "드래프트 LIVE" else "드래프트") },
            )
        }
        when (tab) {
            0 -> PoolTab(session, open)
            1 -> FocusTab(session, open)
            else -> DraftTab(session, open, onOpenDraftClass, onOpenDigest)
        }
    }
}

/** 리포트를 한 번만 만들어 두고 필터·줄 표시에 같이 쓴다 */
private class Scouted(val prospect: DraftProspect, val report: ScoutReport)

private fun GameSession.scouted(prospects: List<DraftProspect>): List<Scouted> =
    prospects.map { Scouted(it, prospectReport(it)) }

/** 지명 후보 전체. 우리 구단 평가 순으로 줄을 세운다. */
@Composable
private fun PoolTab(session: GameSession, onOpen: (DraftProspect) -> Unit) {
    var filter by rememberPlayerFilter("pool")
    val rows = session.scouted(session.draftBoard(limit = Int.MAX_VALUE))
        .filter { filter.matches(it.report.scouted) }
    val scroll = rememberPlayerListScroll()
    val spacing = AppTheme.tokens.spacing

    // 목록 줄 사이에 틈이 없어야 둥근 카드 하나로 보여서, 간격은 spacedBy 대신 Spacer 로 준다
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = spacing.l),
        contentPadding = PaddingValues(vertical = spacing.s),
    ) {
        if (session.draftDone) {
            item {
                SectionCard("올해 드래프트는 끝났어요") {
                    Text(
                        "아래는 지명받지 못한 선수들이에요. 스토브리그에 육성선수 계약 후보가 되고, " +
                            "관찰은 더 붙일 수 없어요. 새 드래프트 풀은 다음 시즌에 공개돼요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(spacing.s))
            }
        }
        // 투자 단계·슬롯은 "관찰" 탭(탭 이름에도 슬롯 수)에 있어 여기서 되풀이하지 않는다
        item {
            PlayerFilterBar(filter, { filter = it }, resultCount = rows.size)
            Spacer(Modifier.height(spacing.s))
        }
        prospectItems(session, "후보", rows, scroll, onOpen) { focusAction(session, it) }
    }
}

/** 드래프트 후보 목록(지연). 줄을 누르면 리포트, 줄 끝 버튼은 [action] */
private fun androidx.compose.foundation.lazy.LazyListScope.prospectItems(
    session: GameSession,
    title: String,
    rows: List<Scouted>,
    scroll: PlayerListScroll,
    onOpen: (DraftProspect) -> Unit,
    action: (DraftProspect) -> RowAction?,
) {
    if (rows.isEmpty()) return
    val byId = rows.associateBy { it.prospect.id }
    playerListItems(
        prospectSection(session, title, rows.map { it.prospect to it.report }),
        scroll,
        onPlayer = { id -> byId[id]?.let { onOpen(it.prospect) } },
        action = { row -> byId[row.id]?.let { action(it.prospect) } },
    )
}

/** 줄에서 바로 관찰을 켜고 끈다. 슬롯이 꽉 찼거나 관찰할 수 없는 선수면 버튼이 없다 */
private fun focusAction(session: GameSession, prospect: DraftProspect): RowAction? {
    val focused = session.isFocused(prospect)
    if (!focused && !session.canFocus(prospect)) return null
    if (!focused && !session.canAddFocus) return null
    return RowAction(if (focused) "해제" else "관찰") { session.toggleFocus(prospect) }
}

/** 집중 관찰 슬롯. 슬롯은 유한해서 "누구를 볼 것인가"가 결정거리가 된다 (docs/10). */
@Composable
private fun FocusTab(session: GameSession, onOpen: (DraftProspect) -> Unit) {
    var filter by rememberPlayerFilter("focus")
    val focused = session.scouted(session.focusedProspects())
    val rows = focused.filter { filter.matches(it.report.scouted) }
    val scroll = rememberPlayerListScroll()
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = AppTheme.tokens.spacing.l),
        contentPadding = PaddingValues(vertical = AppTheme.tokens.spacing.s),
    ) {
        item {
            SectionCard("투자 단계") {
                Text(
                    "단계를 올리면 드래프트 풀 전체를 보는 눈이 좋아져요. 집중 관찰 슬롯은 단계와 상관없이 ${session.focusSlots}개예요. " +
                        "비용은 스토브리그에 운용 자금에서 나가요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppTheme.tokens.spacing.s))
                // 단계 고르기: 칩 다섯 개 대신 세그먼트 버튼 하나. 슬롯 수는 탭 이름에 있어 줄을 뺐다
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    (1..SCOUTING_LEVELS).forEach { level ->
                        SegmentedButton(
                            selected = session.scoutingLevel == level,
                            onClick = { session.setScoutingLevel(level) },
                            shape = SegmentedButtonDefaults.itemShape(level - 1, SCOUTING_LEVELS),
                        ) { Text("$level") }
                    }
                }
                Spacer(Modifier.height(AppTheme.tokens.spacing.s))
                StatRow("연간 비용", "${session.scoutingCost(session.scoutingLevel)}억")
                StatRow("운용 자금", "${session.currentFunds().fixed(1)}억")
                val foreignFocus = session.focusUsed - focused.size
                if (foreignFocus > 0) {
                    Text(
                        "외국인 후보 ${foreignFocus}명도 슬롯을 쓰고 있어요 (시장 탭 → 외국인).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(AppTheme.tokens.spacing.s))
        }
        item {
            AutoFocusCard(session)
            Spacer(Modifier.height(AppTheme.tokens.spacing.s))
        }
        if (focused.isEmpty()) {
            item {
                Text(
                    if (session.draftDone) {
                        "드래프트가 끝나서 관찰 슬롯을 비웠어요. 다음 시즌 드래프트 풀이 공개되면 다시 붙일 수 있어요."
                    } else {
                        "아직 집중 관찰 중인 선수가 없어요. 드래프트 풀에서 '관찰'을 눌러 시작해 보세요."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                PlayerFilterBar(filter, { filter = it }, resultCount = rows.size)
                Spacer(Modifier.height(AppTheme.tokens.spacing.s))
            }
        }
        prospectItems(session, "관찰 중", rows, scroll, onOpen) { focusAction(session, it) }
    }
}

/**
 * 드래프트 탭.
 *
 * 드래프트 주차에는 **생중계**다. 다른 구단 차례면 [PICK_INTERVAL_MILLIS] 마다 한 명씩 지명하고,
 * 방금 누가 누구를 뽑았는지와 남은 선수를 같이 보여준다. 우리 차례가 오면 멈춘다.
 * 한 장씩 넘기든 "우리 차례까지"로 건너뛰든 엔진은 같은 순서로 랜덤을 쓰므로 결과가 같다.
 */
@Composable
private fun DraftTab(session: GameSession, onOpen: (DraftProspect) -> Unit, onOpenDraftClass: () -> Unit, onOpenDigest: () -> Unit) {
    val live = session.isDraftWeek && !session.draftDone
    // 생중계를 보다가 드래프트가 끝나면 곧장 우리 신인 화면을 연다 (끝난 뒤 다시 들어올 땐 열지 않는다)
    var watchedLive by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(live, session.draftDone) {
        if (live) watchedLive = true
        if (watchedLive && session.draftDone) {
            watchedLive = false
            if (session.myDraftPicks().isNotEmpty()) onOpenDraftClass()
        }
    }
    val myTurn = live && session.isMyDraftTurn
    var playing by rememberSaveable { mutableStateOf(true) }
    // 지명 자동 진행: 켜면 우리 차례에도 멈추지 않고 스카우트팀이 추천 기준대로 고른다
    var autoPick by rememberSaveable { mutableStateOf(false) }
    var filter by rememberPlayerFilter("draft")

    LaunchedEffect(live) { if (live) session.openDraft() }
    // 생중계: 한 장씩 넘긴다. 우리 차례면 멈추고(자동 진행이면 스카우트팀이 고른다), 일시정지·종료면 멈춘다
    LaunchedEffect(live, myTurn, playing, autoPick) {
        if (!live || !playing || (myTurn && !autoPick)) return@LaunchedEffect
        while (true) {
            delay(PICK_INTERVAL_MILLIS)
            val pick = if (autoPick) session.advanceDraftPickAuto() else session.advanceDraftPick()
            pick ?: break
        }
    }

    val remaining = if (live) {
        session.scouted(session.draftBoard(limit = Int.MAX_VALUE)).filter { filter.matches(it.report.scouted) }
    } else {
        emptyList()
    }

    val scroll = rememberPlayerListScroll()
    val gap = AppTheme.tokens.spacing.m
    // 남은 선수 목록이 둥근 카드 하나로 이어지도록 spacedBy 대신 카드마다 아래 Spacer 를 둔다
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = AppTheme.tokens.spacing.l),
        contentPadding = PaddingValues(vertical = AppTheme.tokens.spacing.s),
    ) {
        item {
            DraftStatusCard(session, live, myTurn, playing, autoPick, onAutoPick = { autoPick = it }) { playing = it }
            Spacer(Modifier.height(gap))
        }

        if (live) {
            item {
                LiveFeed(session)
                Spacer(Modifier.height(gap))
            }
        }

        if (session.myDraftPicks().isNotEmpty()) {
            item {
                SectionCard("우리 지명") {
                    session.myDraftPicks().forEach { selection ->
                        StatRow(
                            "${selection.round}R ${selection.overallPick}순위",
                            "${selection.playerName} ${selection.positionLabel} · 계약금 ${selection.signingBonus}억",
                        )
                    }
                    Spacer(Modifier.height(AppTheme.tokens.spacing.s))
                    OutlinedButton(onClick = onOpenDraftClass, modifier = Modifier.fillMaxWidth()) {
                        Text("우리 신인 능력치 보기")
                    }
                }
                Spacer(Modifier.height(gap))
            }
        }

        if (!session.draftDone) {
            item {
                DigestCard(session, onOpenDigest)
                Spacer(Modifier.height(gap))
            }
        }

        if (live) {
            item {
                Text(
                    "남은 선수",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = AppTheme.tokens.spacing.xs),
                )
                PlayerFilterBar(filter, { filter = it }, resultCount = remaining.size)
                Spacer(Modifier.height(gap))
            }
            prospectItems(session, "후보", remaining, scroll, onOpen) { prospect ->
                if (myTurn && !autoPick) RowAction("지명") { session.draftPlayer(prospect) } else null
            }
        } else if (session.draftDone) {
            item { Text("전체 지명 결과", style = MaterialTheme.typography.titleMedium) }
            items(session.draftSelections(), key = { it.overallPick }) { selection ->
                PickLine(session, selection, highlight = false)
            }
        } else {
            item {
                SectionCard("보유 지명권") {
                    session.myPicks().forEach { pick -> StatRow(pick.label(), "${pick.round}라운드") }
                }
            }
        }
    }
}

@Composable
private fun DraftStatusCard(
    session: GameSession,
    live: Boolean,
    myTurn: Boolean,
    playing: Boolean,
    autoPick: Boolean,
    onAutoPick: (Boolean) -> Unit,
    onPlaying: (Boolean) -> Unit,
) {
    val slot = session.draftSlot()
    var confirmFinish by remember { mutableStateOf(false) }
    SectionCard("${session.league.season} 신인 드래프트") {
        when {
            session.draftDone -> Text("드래프트가 끝났어요. 지명 선수는 다음 시즌 개막에 2군으로 입단해요. 홈에서 다음 주로 넘어가세요.")
            !session.isDraftWeek -> Text(
                "${session.calendarLabel(session.state.calendar.draftWeek)}에 열려요. " +
                    "그때까지 집중 관찰로 리포트를 좁혀 두세요.",
                style = MaterialTheme.typography.bodyMedium,
            )
            slot == null -> Text("순번표를 준비하고 있어요.")
            myTurn && autoPick -> Text(
                "${slot.round}라운드 ${slot.overallPick}순위 — 우리 차례, 스카우트팀이 ${session.draftPolicy.label} 기준으로 고르는 중…",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.good,
            )
            myTurn -> Text(
                "${slot.round}라운드 ${slot.overallPick}순위 — 우리 차례예요. 아래 남은 선수에서 골라 주세요.",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.good,
            )
            else -> Text(
                "${slot.round}라운드 ${slot.overallPick}순위 — ${session.league.team(slot.ownerTeam).name} 지명 중…",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (live && session.draftTotalPicks > 0) {
            Spacer(Modifier.height(AppTheme.tokens.spacing.xs))
            Text(
                "진행 ${session.draftSelections().size}/${session.draftTotalPicks} · 우리 지명 ${session.myDraftPicks().size}명",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (live && (!myTurn || autoPick)) {
            Spacer(Modifier.height(AppTheme.tokens.spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.s)) {
                OutlinedButton(onClick = { onPlaying(!playing) }) { Text(if (playing) "일시정지" else "계속 보기") }
                if (!autoPick) {
                    OutlinedButton(onClick = { session.advanceDraft() }, enabled = !session.busy) { Text("우리 차례까지 건너뛰기") }
                }
            }
        }
        if (live) {
            Spacer(Modifier.height(AppTheme.tokens.spacing.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("우리 지명도 스카우트팀에 맡기기", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "켜면 우리 차례에도 멈추지 않아요. 스카우트팀이 추천 기준(${session.draftPolicy.label})대로 골라요. 언제든 끌 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(AppTheme.tokens.spacing.s))
                Switch(checked = autoPick, onCheckedChange = onAutoPick)
            }
            TextButton(onClick = { confirmFinish = true }, enabled = !session.busy) { Text("남은 지명 끝까지 바로 진행") }
        }
    }
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("드래프트를 끝까지 진행할까요?") },
            text = {
                Text(
                    "남은 우리 지명 ${session.remainingUserPicks()}장을 스카우트팀이 ${session.draftPolicy.label} 기준으로 고르고, " +
                        "다른 구단 지명까지 한 번에 끝내요. 되돌릴 수 없어요.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    session.autoDraft()
                }) { Text("끝까지 진행") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("취소") } },
        )
    }
}

/** 방금 나온 지명들. 맨 위가 가장 최근이다 */
@Composable
private fun LiveFeed(session: GameSession) {
    val recent = session.draftSelections().takeLast(LIVE_FEED_SIZE).asReversed()
    SectionCard("방금 지명") {
        if (recent.isEmpty()) {
            Text(
                "아직 지명이 없어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        recent.forEachIndexed { index, selection ->
            PickLine(session, selection, highlight = index == 0)
        }
    }
}

@Composable
private fun PickLine(session: GameSession, selection: DraftSelection, highlight: Boolean) {
    val ours = selection.teamId == session.userTeamId
    // 지명되면 슬롯에서 빠지므로 "지금 관찰 중"이 아니라 "관찰한 적 있음"으로 본다
    val focused = session.wasWatched(selection.playerId)
    Row(Modifier.fillMaxWidth().padding(vertical = AppTheme.tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${selection.round}R ${selection.overallPick}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            session.league.team(selection.teamId).nickname,
            style = MaterialTheme.typography.labelLarge,
            color = if (ours) AppColors.good else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (ours) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(64.dp),
        )
        Text(
            "${selection.playerName} (${selection.positionLabel}, ${selection.schoolTypeLabel})",
            style = if (highlight) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (focused && !ours) Pill("관찰했던 선수", AppColors.warn)
    }
}

/**
 * 리포트 상세 (docs/10 리포트 구성).
 *
 * 탭 백스택에 쌓이는 화면이다. 목록 안에서 화면을 갈아 끼우면 목록이 통째로 사라졌다 다시 만들어져서
 * 필터·스크롤이 풀렸다 — 백스택에 쌓으면 목록 상태는 SaveableStateHolder 가 보관한다.
 */
@Composable
fun ProspectDetailScreen(session: GameSession, prospectId: PlayerId, onBack: () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val prospect = session.league.draftPool.byId(prospectId) ?: return
    val report = session.prospectReport(prospect)
    val scouted = report.scouted

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = AppTheme.tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.m),
        contentPadding = PaddingValues(vertical = AppTheme.tokens.spacing.s),
    ) {
        item {
            // 선수 상세와 같은 머리: 큰 정체(아마추어라 번호 없는 빈 원) + 학교
            PlayerIdentity(session.tagOf(prospect, report).copy(caption = "${scouted.age}세 · ${prospect.schoolTypeLabel} ${prospect.school}"), large = true)
        }
        item { AccuracyGauge(scouted.precision) }
        item {
            SectionCard("리포트") {
                StatRow("체격", report.physique?.toString() ?: "-")
                report.topSpeedKmh?.let { StatRow("최고 구속", "${it}km/h") }
                StatRow("잠재력", "${scouted.potentialLabel} (${report.potentialRange})")
                StatRow(
                    "성장 타입",
                    when (scouted.growthTypeGuess) {
                        GrowthType.EARLY -> "조기 완성형"
                        GrowthType.NORMAL -> "일반형"
                        GrowthType.LATE -> "대기만성형"
                    } + if (scouted.precision.growthTypeReliable) "" else " (추정)",
                )
                StatRow("부상 이력", report.injuryText)
                StatRow("관찰", if (report.focusWeeks > 0) "${report.focusWeeks}주" else "집중 관찰 안 함")
                val selection = session.selectionOf(prospect)
                when {
                    selection != null -> StatRow(
                        "지명",
                        "${session.league.team(selection.teamId).nickname} ${selection.round}R ${selection.overallPick}순위",
                        if (selection.teamId == session.userTeamId) AppColors.good else null,
                    )
                    session.draftDone -> StatRow("지명", "미지명 (육성선수 계약 후보)")
                }
            }
        }
        item {
            // "추정 범위"는 바로 위 정보 정확도 카드가 말한다
            SectionCard("능력치") {
                Attribute.entries.filter { it in scouted.ratings }.forEach { attribute ->
                    val range = scouted.ratings.getValue(attribute)
                    RatingBar(attribute.label, range.low, range.high)
                }
            }
        }
        item {
            SectionCard("스카우트 코멘트") {
                report.comments.forEach {
                    Text("· $it", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(AppTheme.tokens.spacing.xs))
                }
            }
        }
        item {
            val focused = session.isFocused(prospect)
            val full = !session.canAddFocus
            val closed = !focused && !session.canFocus(prospect)
            OutlinedButton(
                onClick = { session.toggleFocus(prospect) },
                enabled = !closed && (focused || !full),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        focused -> "집중 관찰 해제"
                        closed -> if (session.draftDone) "드래프트가 끝났어요" else "이미 지명된 선수예요"
                        else -> "집중 관찰 (${session.focusUsed}/${session.focusSlots})"
                    },
                )
            }
            if (!focused && !closed && full) {
                Text(
                    "슬롯이 꽉 찼어요. 다른 선수를 빼야 해요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.bad,
                )
            }
            val draftable = session.isDraftWeek && !session.draftDone && session.isMyDraftTurn &&
                session.availableProspects().any { it.id == prospect.id }
            if (draftable) {
                Spacer(Modifier.height(AppTheme.tokens.spacing.s))
                OutlinedButton(
                    onClick = {
                        session.draftPlayer(prospect)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("이 선수 지명") }
            }
        }
    }
}

/**
 * 자동 집중 관찰 스위치 (유저 요청 2026-10-03).
 * 켜 두면 매주 초 빈 슬롯을 추천 기준 순으로 채우고, 더 봐도 정확도가 오르지 않는 선수는 뺀다.
 */
@Composable
private fun AutoFocusCard(session: GameSession) {
    SectionCard("자동 관찰") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "빈 슬롯은 추천 기준(${session.draftPolicy.label}) 순으로 채우고, 더 볼 게 없는 선수는 빼요. " +
                    "직접 붙인 선수도 다 보면 빠져요. 외국인 후보 슬롯은 건드리지 않아요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(AppTheme.tokens.spacing.s))
            Switch(checked = session.autoFocus, onCheckedChange = { session.autoFocus = it })
        }
    }
}

/**
 * 스카우트팀 리포트 카드 (2026-10-04). 추천을 늘 띄우지 않고 **받은 리포트를 열어 보는 버튼**만 둔다.
 * 추천 기준은 여기서 고른다 — 자동 관찰 순서, 지명 자동 진행, 다음 리포트가 이 기준을 따른다.
 */
@Composable
private fun DigestCard(session: GameSession, onOpenDigest: () -> Unit) {
    val latest = session.scoutDigests().lastOrNull()
    val spacing = AppTheme.tokens.spacing
    SectionCard("스카우트팀 리포트") {
        Text(
            when {
                latest == null -> "아직 받은 리포트가 없어요." + (session.nextDigestWeek()?.let { " 첫 리포트는 ${it}주차에 와요." } ?: "")
                else -> latest.headline
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (latest != null) {
            Text(
                (if (latest.final) "최종 리포트" else "${latest.week}주차 리포트") +
                    (session.nextDigestWeek()?.let { " · 다음 리포트 ${it}주차" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.s))
            OutlinedButton(onClick = onOpenDigest, modifier = Modifier.fillMaxWidth()) { Text("리포트 보기") }
        }
        Spacer(Modifier.height(spacing.s))
        Text(
            "스카우트팀 기준 — 자동 관찰 순서, 지명 자동 진행, 다음 리포트가 이 기준을 따라요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xs))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DraftPolicy.entries.forEachIndexed { index, policy ->
                SegmentedButton(
                    selected = session.draftPolicy == policy,
                    onClick = { session.draftPolicy = policy },
                    shape = SegmentedButtonDefaults.itemShape(index, DraftPolicy.entries.size),
                ) { Text(policy.label) }
            }
        }
    }
}

private const val DRAFT_TAB = 2
private const val SCOUTING_LEVELS = 5

/** 생중계 한 장 간격. 화면 연출 값이라 balance.json 이 아니라 여기 둔다 */
private const val PICK_INTERVAL_MILLIS = 700L

private const val LIVE_FEED_SIZE = 8
